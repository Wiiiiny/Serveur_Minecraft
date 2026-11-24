package fr.serveur.rpg.death;

import fr.serveur.rpg.Msg;
import fr.serveur.rpg.data.PlayerData;
import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.economy.ObscureStone;
import fr.serveur.rpg.tools.OfficialTools;
import fr.serveur.rpg.world.DayCycle;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Règles de mort :
 * - la nuit : on garde tout (inventaire, armure, XP) et on réapparaît au dernier lit ;
 * - le jour : l'inventaire tombe au sol. Tué par un joueur : seul le tueur peut le ramasser.
 *   Mort « environnementale » : personne ne peut le ramasser. Puis on attend la nuit
 *   (en spectateur ou dans la salle d'attente de la mairie), sauf avec une Larme de vie.
 * La Pioche des ténèbres et les Larmes de vie ne sont jamais perdues.
 */
public final class DeathSystem {
    /** Personne ne peut ramasser un objet appartenant à cet identifiant. */
    public static final UUID NOBODY = new UUID(0L, 0x5EA1EDL);
    public static final String LOCKED_TAG = "survierpg_locked_loot";
    private static final Map<UUID, Boolean> DIED_AT_NIGHT = new HashMap<>();

    private DeathSystem() {}

    // ------------------------------------------------------------------ Larme de vie

    public static ItemStack createTear(int count) {
        return new ItemStack(fr.serveur.rpg.items.ModItems.LARME_DE_VIE, count);
    }

    public static boolean isTear(ItemStack s) {
        if (s.isOf(fr.serveur.rpg.items.ModItems.LARME_DE_VIE)) return true;
        NbtCompound tag = s.getSubNbt(OfficialTools.NBT_KEY); // anciennes larmes (Larme de Ghast + NBT)
        return tag != null && "larme".equals(tag.getString("type"));
    }

    /** Objets jamais perdus à la mort. */
    public static boolean isSoulbound(ItemStack s) {
        return ObscureStone.isPickaxe(s) || isTear(s) || fr.serveur.rpg.world.TravelBeacon.isBeacon(s);
    }

    // ------------------------------------------------------------------ événements

    public static void register() {
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (alive) return;
            onRespawn(newPlayer);
        });

        // Un joueur mort de jour ne peut pas prendre de dégâts pendant son attente.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof ServerPlayerEntity p && RpgState.get(p.getServer()).player(p).awaitingRevive));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            ServerPlayerEntity p = handler.player;
            PlayerData data = RpgState.get(server).player(p);
            if (data.awaitingRevive && DayCycle.isNight(server.getOverworld())) revive(p, "La nuit est tombée pendant ton absence : tu reviens à la vie.");
            else if (data.awaitingRevive) explainWaiting(p);
        }));
        // Le clic droit avec une Larme de vie est géré par l'item lui-même (items.TearItem).
    }

    /** Appelé (mixin) au tout début de ServerPlayerEntity.onDeath, avant que l'inventaire ne tombe. */
    public static void onDeath(ServerPlayerEntity player, DamageSource source) {
        MinecraftServer server = player.getServer();
        RpgState state = RpgState.get(server);
        PlayerData data = state.player(player);
        boolean night = DayCycle.isNight(server.getOverworld());
        DIED_AT_NIGHT.put(player.getUuid(), night);

        data.deaths++;
        fr.serveur.rpg.sombre.Personnage.onDeath(player, data);
        PlayerEntity killer = source.getAttacker() instanceof PlayerEntity pk && pk != player ? pk : null;
        if (killer != null) state.player(killer).pvpKills++;

        // Mort dans la zone de raid : coffre de corps, retour au hub (règles du raid).
        if (fr.serveur.rpg.raid.RaidDeath.onDeath(player, data, DeathSystem::isSoulbound)) {
            DIED_AT_NIGHT.put(player.getUuid(), true);
            state.markDirty();
            return;
        }

        PlayerInventory inv = player.getInventory();
        // Objets liés à l'âme : mis de côté dans tous les cas.
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (isSoulbound(s)) {
                data.keptItems.add(s.writeNbt(new NbtCompound()));
                inv.setStack(i, ItemStack.EMPTY);
            }
        }

        if (night) {
            // Nuit : on garde tout.
            data.keptInventory = inv.writeNbt(new NbtList());
            data.keptXpLevel = player.experienceLevel;
            data.keptXpProgress = player.experienceProgress;
            data.keptXpTotal = player.totalExperience;
            inv.clear();
            player.experienceLevel = 0;
            player.experienceProgress = 0;
            player.totalExperience = 0;
        } else {
            // Jour : tout tombe, mais réservé au tueur, ou à personne.
            UUID owner = killer != null ? killer.getUuid() : NOBODY;
            ServerWorld world = player.getServerWorld();
            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = inv.getStack(i);
                if (s.isEmpty()) continue;
                ItemEntity item = new ItemEntity(world, player.getX(), player.getEyeY() - 0.3, player.getZ(), s.copy());
                item.setVelocity(world.random.nextGaussian() * 0.05, 0.2, world.random.nextGaussian() * 0.05);
                item.setPickupDelay(40);
                item.setOwner(owner);
                item.addCommandTag(LOCKED_TAG);
                world.spawnEntity(item);
                inv.setStack(i, ItemStack.EMPTY);
            }
            if (killer != null) {
                Msg.send((ServerPlayerEntity) killer, Text.literal("Tu as tué " + player.getGameProfile().getName()
                        + " : son butin t'est réservé.").formatted(Formatting.DARK_RED));
            }
        }
        state.markDirty();
    }

    private static void onRespawn(ServerPlayerEntity player) {
        if (fr.serveur.rpg.raid.RaidDeath.onRespawn(player)) {
            DIED_AT_NIGHT.remove(player.getUuid());
            ObscureStone.returnKeptItems(player);
            return;
        }
        RpgState state = RpgState.get(player.getServer());
        PlayerData data = state.player(player);
        boolean night = DIED_AT_NIGHT.getOrDefault(player.getUuid(), true);
        DIED_AT_NIGHT.remove(player.getUuid());

        if (data.keptInventory != null) {
            player.getInventory().readNbt(data.keptInventory);
            player.experienceLevel = data.keptXpLevel;
            player.experienceProgress = data.keptXpProgress;
            player.totalExperience = data.keptXpTotal;
            data.keptInventory = null;
        }
        ObscureStone.returnKeptItems(player);
        state.markDirty();

        if (night) {
            Msg.send(player, Text.literal("Mort pendant la nuit : tu gardes tout et tu reviens à ton lit.").formatted(Formatting.GRAY));
            return;
        }
        data.awaitingRevive = true;
        state.markDirty();
        player.changeGameMode(GameMode.SPECTATOR);
        explainWaiting(player);
    }

    private static void explainWaiting(ServerPlayerEntity player) {
        Msg.send(player, Text.literal("Mort pendant le jour : tu réapparaîtras à la tombée de la nuit.").formatted(Formatting.RED));
        Msg.send(player, Text.literal("/attente spectateur — rester spectateur   /attente salle — attendre à la mairie   /larme — revenir tout de suite (Larme de vie)").formatted(Formatting.GRAY));
    }

    // ------------------------------------------------------------------ attente et retour

    public static void goSpectator(ServerPlayerEntity player) {
        player.changeGameMode(GameMode.SPECTATOR);
    }

    public static boolean goWaitingRoom(ServerPlayerEntity player) {
        RpgState state = RpgState.get(player.getServer());
        if (state.waitingRoom == null) return false;
        ServerWorld world = player.getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD, new Identifier(state.waitingRoomDim)));
        if (world == null) return false;
        player.changeGameMode(GameMode.ADVENTURE);
        BlockPos p = state.waitingRoom;
        player.teleport(world, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, player.getYaw(), player.getPitch());
        return true;
    }

    public static boolean useTear(ServerPlayerEntity player) {
        PlayerData data = RpgState.get(player.getServer()).player(player);
        if (!data.awaitingRevive) {
            player.sendMessage(Text.literal("La Larme de vie ne sert qu'après une mort de jour.").formatted(Formatting.GRAY), true);
            return false;
        }
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (isTear(s)) {
                s.decrement(1);
                revive(player, "La Larme de vie te ramène à la vie.");
                return true;
            }
        }
        player.sendMessage(Text.literal("Tu n'as pas de Larme de vie.").formatted(Formatting.RED), true);
        return false;
    }

    public static void reviveAll(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (RpgState.get(server).player(p).awaitingRevive) revive(p, "La nuit tombe : tu reviens à la vie.");
        }
    }

    public static void revive(ServerPlayerEntity player, String message) {
        RpgState state = RpgState.get(player.getServer());
        state.player(player).awaitingRevive = false;
        state.markDirty();
        player.changeGameMode(GameMode.SURVIVAL);

        // Retour au lit (ou au spawn du monde).
        MinecraftServer server = player.getServer();
        ServerWorld world = server.getWorld(player.getSpawnPointDimension());
        BlockPos spawn = player.getSpawnPointPosition();
        Optional<Vec3d> bed = world != null && spawn != null
                ? PlayerEntity.findRespawnPosition(world, spawn, player.getSpawnAngle(), player.isSpawnForced(), true)
                : Optional.empty();
        if (bed.isPresent()) {
            Vec3d v = bed.get();
            player.teleport(world, v.x, v.y, v.z, player.getSpawnAngle(), 0);
        } else {
            ServerWorld ow = server.getOverworld();
            BlockPos s = ow.getSpawnPos();
            player.teleport(ow, s.getX() + 0.5, ow.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, s.getX(), s.getZ()), s.getZ() + 0.5, 0, 0);
        }
        player.setHealth(player.getMaxHealth());
        Msg.send(player, Text.literal(message).formatted(Formatting.GREEN));
    }
}
