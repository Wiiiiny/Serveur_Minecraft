package fr.serveur.rpg.world;

import fr.serveur.rpg.data.PlayerData;
import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.items.ModItems;
import fr.serveur.rpg.jobs.SideJob;
import fr.serveur.rpg.jobs.SideJobConfig;
import fr.serveur.rpg.jobs.SideJobs;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Balise du Voyageur : objet du métier (jamais perdu), avec recharge. Clic droit = canalisation
 * immobile (10 s par défaut), interrompue si on bouge ou si on prend des dégâts. Elle téléporte
 * de l'autre côté de la limite de la zone jouable ({@link PlayableZone}), ou ramène à l'intérieur.
 */
public final class TravelBeacon {
    private record Channel(Vec3d start, long endTick, boolean outward) {}

    private static final Map<UUID, Channel> CHANNELS = new HashMap<>();
    private static long tick;

    private TravelBeacon() {}

    public static boolean isBeacon(ItemStack stack) {
        return stack.isOf(ModItems.BALISE);
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(TravelBeacon::tick);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity p && CHANNELS.remove(p.getUuid()) != null) {
                p.sendMessage(Text.literal("Balise interrompue : tu as été blessé.").formatted(Formatting.RED), true);
            }
            return true;
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            ServerPlayerEntity p = handler.player;
            PlayerData data = RpgState.get(server).player(p);
            syncInventory(p, data);
            showCooldown(p, data);
        }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CHANNELS.remove(handler.player.getUuid()));
    }

    /** Donne la balise au Voyageur qui ne l'a pas sur lui, et la reprend à ceux qui ne sont plus Voyageur. */
    public static void syncInventory(ServerPlayerEntity p, PlayerData data) {
        boolean voyageur = data.sideJob == SideJob.VOYAGEUR;
        boolean has = false;
        var inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            if (!isBeacon(inv.getStack(i))) continue;
            if (voyageur && !has) has = true;
            else inv.setStack(i, ItemStack.EMPTY);
        }
        if (voyageur && !has) {
            ItemStack beacon = new ItemStack(ModItems.BALISE);
            if (!inv.insertStack(beacon)) p.dropItem(beacon, false);
        }
    }

    /** Recharge de la balise pour ce joueur, en millisecondes. */
    public static long rechargeMillis(PlayerData data) {
        SideJobConfig.Data cfg = SideJobConfig.data();
        int level = Math.max(1, data.sideLevel(SideJob.VOYAGEUR));
        double minutes = Math.max(5, cfg.voyageurRechargeMinutes - cfg.voyageurRechargeMoinsParNiveau * (level - 1));
        return (long) (minutes * 60_000);
    }

    /** Secondes avant que la balise soit de nouveau utilisable (0 = prête). */
    public static long secondsLeft(PlayerData data) {
        return Math.max(0, (data.beaconReadyAt - System.currentTimeMillis() + 999) / 1000);
    }

    private static void showCooldown(ServerPlayerEntity p, PlayerData data) {
        long left = secondsLeft(data);
        if (left > 0) p.getItemCooldownManager().set(ModItems.BALISE, (int) Math.min(Integer.MAX_VALUE, left * 20));
    }

    /** Clic droit avec la balise. @return vrai si la canalisation commence. */
    public static boolean use(ServerPlayerEntity p) {
        RpgState state = RpgState.get(p.getServer());
        PlayerData data = state.player(p);
        if (data.sideJob != SideJob.VOYAGEUR) {
            syncInventory(p, data);
            return fail(p, "Seul le Voyageur peut utiliser la balise.");
        }
        if (CHANNELS.containsKey(p.getUuid())) return false;
        PlayableZone.Box box = PlayableZone.box(state);
        if (box == null) return fail(p, "Le GM n'a pas encore défini la zone jouable.");
        if (p.getWorld().getRegistryKey() != box.dim()) return fail(p, "La balise ne fonctionne que dans le monde de la zone jouable.");
        long left = secondsLeft(data);
        if (left > 0) return fail(p, "Balise en recharge : encore " + (left >= 60 ? left / 60 + " min " : "") + left % 60 + " s.");
        int seconds = Math.max(1, SideJobConfig.data().voyageurCanalisationSecondes);
        boolean outward = box.contains(p.getX(), p.getZ());
        CHANNELS.put(p.getUuid(), new Channel(p.getPos(), tick + seconds * 20L, outward));
        p.playSound(SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1f, 1.2f);
        p.sendMessage(Text.literal(outward ? "La balise s'éveille… ne bouge plus (" + seconds + " s)." : "La balise te ramène… ne bouge plus (" + seconds + " s).")
                .formatted(Formatting.AQUA), true);
        return true;
    }

    private static boolean fail(ServerPlayerEntity p, String message) {
        p.sendMessage(Text.literal(message).formatted(Formatting.RED), true);
        return false;
    }

    private static void tick(MinecraftServer server) {
        tick++;
        if (CHANNELS.isEmpty()) return;
        Iterator<Map.Entry<UUID, Channel>> it = CHANNELS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Channel> e = it.next();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
            Channel c = e.getValue();
            if (p == null || !p.isAlive()) {
                it.remove();
                continue;
            }
            Vec3d pos = p.getPos();
            if (Math.hypot(pos.x - c.start().x, pos.z - c.start().z) > 0.35 || Math.abs(pos.y - c.start().y) > 0.8) {
                it.remove();
                p.sendMessage(Text.literal("Balise interrompue : tu as bougé.").formatted(Formatting.RED), true);
                continue;
            }
            long left = c.endTick() - tick;
            if (left > 0) {
                if (left % 5 == 0 && p.getWorld() instanceof ServerWorld w) {
                    w.spawnParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 12, 0.4, 0.8, 0.4, 0.4);
                }
                if (left % 20 == 0) {
                    p.sendMessage(Text.literal("Balise : " + left / 20 + " s…").formatted(Formatting.AQUA), true);
                    p.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8f, 1.5f);
                }
                continue;
            }
            it.remove();
            jump(p, c.outward());
        }
    }

    private static void jump(ServerPlayerEntity p, boolean outward) {
        RpgState state = RpgState.get(p.getServer());
        PlayerData data = state.player(p);
        PlayableZone.Box box = PlayableZone.box(state);
        if (box == null || !(p.getWorld() instanceof ServerWorld world) || world.getRegistryKey() != box.dim()) return;
        if (p.hasVehicle()) p.stopRiding();
        Vec3d dest = PlayableZone.destination(world, box, p.getPos(), outward, Math.max(2, SideJobConfig.data().voyageurDistanceSortie));
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 40, 0.5, 1, 0.5, 0.1);
        p.teleport(world, dest.x, dest.y, dest.z, p.getYaw(), p.getPitch());
        world.playSound(null, dest.x, dest.y, dest.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 1f);
        data.beaconReadyAt = System.currentTimeMillis() + rechargeMillis(data);
        state.markDirty();
        showCooldown(p, data);
        p.sendMessage(Text.literal(outward ? "Tu franchis la limite du monde." : "Te voilà de retour dans la zone jouable.").formatted(Formatting.AQUA), false);
        SideJobs.addActionXp(p, SideJob.VOYAGEUR, SideJobConfig.data().voyageurXpSaut);
    }
}
