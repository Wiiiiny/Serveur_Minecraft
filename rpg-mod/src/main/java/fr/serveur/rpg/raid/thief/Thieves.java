package fr.serveur.rpg.raid.thief;

import com.google.gson.JsonObject;
import fr.serveur.rpg.Msg;
import fr.serveur.rpg.SurvieRpg;
import fr.serveur.rpg.data.PlayerData;
import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.economy.Ledger;
import fr.serveur.rpg.raid.RaidMobs;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Enregistrement des voleurs, règles du vol, affinité et compagnons. */
public final class Thieves {
    public static final EntityType<ThiefEntity> TYPE = FabricEntityTypeBuilder.<ThiefEntity>create(SpawnGroup.CREATURE, ThiefEntity::new)
            .dimensions(EntityDimensions.fixed(0.4f, 0.7f)).trackRangeBlocks(64).build();
    public static final String COMPANION_TAG = "survierpg_compagnon";
    /** Paliers d'affinité (pièces données) et rançon minimale. */
    public static final int AFFINITE_SANS_VOL = 20, AFFINITE_SUIT = 50, AFFINITE_COMPAGNON = 100, RANCON_MIN = 2;
    private static final long COMPANION_COOLDOWN = 24 * 3_600_000L;
    private static final Map<UUID, UUID> COMPANIONS = new HashMap<>(); // joueur -> entité

    private Thieves() {}

    public static void register() {
        Registry.register(Registries.ENTITY_TYPE, new Identifier(SurvieRpg.MOD_ID, "chapardeur"), TYPE);
        FabricDefaultAttributeRegistry.register(TYPE, ThiefEntity.createAttributes());
    }

    public static void spawn(ServerWorld w, BlockPos pos) {
        ThiefEntity t = TYPE.create(w);
        if (t == null) return;
        t.refreshPositionAndAngles(pos, w.random.nextFloat() * 360, 0);
        t.addCommandTag(RaidMobs.RAID_MOB_TAG);
        w.spawnEntity(t);
    }

    static boolean isBound(ItemStack s) {
        return ThiefEntity.bound(s);
    }

    /** Un joueur peut être volé : affinité basse, pas déjà ciblé par un autre voleur, pas protégé par un compagnon. */
    static boolean canBeRobbed(ServerPlayerEntity p, ThiefEntity self) {
        PlayerData d = RpgState.get(p.getServer()).player(p);
        if (d.thiefAffinity >= AFFINITE_SANS_VOL) return true; // il viendra le suivre plutôt que le voler
        Box around = new Box(p.getBlockPos()).expand(24);
        for (ThiefEntity o : p.getServerWorld().getEntitiesByClass(ThiefEntity.class, around, t -> t != self && t.isAlive())) {
            if (o.mode() == ThiefEntity.Mode.FUITE) return false; // un seul voleur actif par joueur
            if (o.mode() == ThiefEntity.Mode.COMPAGNON && p.getUuid().equals(o.owner()) && o.squaredDistanceTo(p) < 16 * 16) return false;
        }
        return true;
    }

    public static void addAffinity(ServerPlayerEntity p, int n) {
        RpgState s = RpgState.get(p.getServer());
        s.player(p).thiefAffinity += n;
        s.markDirty();
    }

    /** Palier 100 atteint : ce voleur devient le compagnon du joueur. */
    static void promote(ServerPlayerEntity p, ThiefEntity t) {
        PlayerData d = RpgState.get(p.getServer()).player(p);
        if (COMPANIONS.containsKey(p.getUuid()) || System.currentTimeMillis() < d.companionCooldownUntil) return;
        t.bindCompanion(p.getUuid());
        COMPANIONS.put(p.getUuid(), t.getUuid());
        Msg.send(p, Text.literal("Le chapardeur devient ton compagnon fidèle : il ramasse les objets autour de toi et éloigne les autres voleurs.")
                .formatted(Formatting.GOLD));
    }

    static void onCompanionDeath(MinecraftServer server, UUID owner) {
        COMPANIONS.remove(owner);
        RpgState s = RpgState.get(server);
        PlayerData d = s.players.get(owner);
        if (d == null) return;
        d.companionCooldownUntil = System.currentTimeMillis() + COMPANION_COOLDOWN;
        s.markDirty();
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(owner);
        if (p != null) Msg.send(p, Text.literal("Ton compagnon est mort. Un autre viendra dans 24 h.").formatted(Formatting.RED));
    }

    /** Chaque seconde : chaque joueur à l'affinité 100 (compagnon actif, pas de pénalité) a son compagnon à côté de lui. */
    public static void tickCompanions(MinecraftServer server) {
        long now = System.currentTimeMillis();
        RpgState state = RpgState.get(server);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            PlayerData d = state.player(p);
            boolean wanted = d.thiefAffinity >= AFFINITE_COMPAGNON && !d.companionDisabled && now >= d.companionCooldownUntil && !p.isSpectator();
            UUID id = COMPANIONS.get(p.getUuid());
            Entity current = id == null ? null : find(server, id);
            if (!wanted) {
                if (current != null) current.discard();
                COMPANIONS.remove(p.getUuid());
                continue;
            }
            if (current != null && current.isAlive() && current.getWorld() == p.getWorld()) continue;
            if (current != null) current.discard(); // resté dans un autre monde : on le recrée à côté du joueur
            ThiefEntity t = TYPE.create(p.getServerWorld());
            if (t == null) continue;
            t.refreshPositionAndAngles(p.getX() + 1, p.getY(), p.getZ() + 1, p.getYaw(), 0);
            t.bindCompanion(p.getUuid());
            p.getServerWorld().spawnEntity(t);
            COMPANIONS.put(p.getUuid(), t.getUuid());
        }
    }

    private static Entity find(MinecraftServer server, UUID id) {
        for (ServerWorld w : server.getWorlds()) {
            Entity e = w.getEntity(id);
            if (e != null) return e;
        }
        return null;
    }

    /** Un voleur a disparu avec un objet : on le note (support / restauration). */
    static void logLoss(MinecraftServer server, UUID victim, ItemStack stack) {
        if (stack.isEmpty()) return;
        JsonObject o = new JsonObject();
        o.addProperty("t", LocalDateTime.now().withNano(0).toString());
        o.addProperty("joueur", victim == null ? "?" : victim.toString());
        PlayerData d = victim == null ? null : RpgState.get(server).players.get(victim);
        o.addProperty("nom", d == null ? "?" : d.name);
        o.addProperty("objet", stack.writeNbt(new net.minecraft.nbt.NbtCompound()).toString());
        try {
            var file = Ledger.dir(server).resolve("vols.jsonl");
            Files.createDirectories(file.getParent());
            Files.writeString(file, o + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            SurvieRpg.LOGGER.error("Journal des vols : écriture impossible", e);
        }
        SurvieRpg.LOGGER.info("[raid] objet perdu (voleur) : {} — {}", d == null ? "?" : d.name, stack);
    }
}
