package fr.serveur.rpg.world;

import fr.serveur.rpg.data.PlayerData;
import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.jobs.SideJob;
import fr.serveur.rpg.jobs.SideJobConfig;
import fr.serveur.rpg.jobs.SideJobs;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Zone jouable : un rectangle défini par le GM avec deux coins (menu B › Maître du jeu › Monde, ou /gm bordure).
 * Les joueurs sont ramenés à l'intérieur s'ils en sortent, sauf le Voyageur qui la franchit avec sa balise
 * ({@link TravelBeacon}) et gagne de l'XP en explorant au-delà. Un rideau de particules marque la limite.
 * Ce n'est pas la bordure vanilla : au-delà, on peut casser et poser des blocs normalement.
 */
public final class PlayableZone {
    private static final DustParticleEffect CURTAIN = new DustParticleEffect(new Vector3f(0.62f, 0.45f, 0.9f), 1.6f);
    private static final Map<UUID, Vec3d> LAST_POS = new HashMap<>();
    private static final Map<UUID, Double> TRAVELED = new HashMap<>();
    private static final Map<UUID, Long> LAST_WARNING = new HashMap<>();
    private static long ticks;

    /** Rectangle en coordonnées de blocs, bornes incluses. */
    public record Box(RegistryKey<World> dim, int minX, int minZ, int maxX, int maxZ) {
        public boolean contains(double x, double z) {
            return x >= minX && x < maxX + 1 && z >= minZ && z < maxZ + 1;
        }

        public String describe() {
            return dim.getValue().getPath() + " : X " + minX + " → " + maxX + ", Z " + minZ + " → " + maxZ
                    + " (" + (maxX - minX + 1) + " × " + (maxZ - minZ + 1) + ")";
        }
    }

    private PlayableZone() {}

    /** La zone active, ou null si le GM n'a pas encore défini ses deux coins. */
    public static Box box(RpgState s) {
        if (!s.borderActive()) return null;
        BlockPos a = s.borderCorner1, b = s.borderCorner2;
        RegistryKey<World> dim = RegistryKey.of(RegistryKeys.WORLD, new Identifier(s.borderDim));
        return new Box(dim, Math.min(a.getX(), b.getX()), Math.min(a.getZ(), b.getZ()), Math.max(a.getX(), b.getX()), Math.max(a.getZ(), b.getZ()));
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PlayableZone::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUuid();
            LAST_POS.remove(id);
            TRAVELED.remove(id);
            LAST_WARNING.remove(id);
        });
    }

    private static void tick(MinecraftServer server) {
        ticks++;
        if (ticks % 10 != 0) return;
        RpgState state = RpgState.get(server);
        Box box = box(state);
        if (box == null) return;
        ServerWorld world = server.getWorld(box.dim());
        if (world == null) return;
        boolean second = ticks % 20 == 0;
        for (ServerPlayerEntity p : world.getPlayers()) {
            PlayerData data = state.player(p);
            boolean inside = box.contains(p.getX(), p.getZ());
            boolean voyageur = data.sideJob == SideJob.VOYAGEUR;
            if (!inside && !voyageur && !SideJobs.exempt(p)) pullBack(world, box, p);
            if (second) {
                curtain(world, box, p);
                if (voyageur && !inside) exploration(p);
                else {
                    LAST_POS.remove(p.getUuid());
                }
            }
        }
    }

    /** Ramène à l'intérieur un joueur qui n'a pas le droit d'en sortir. */
    private static void pullBack(ServerWorld world, Box box, ServerPlayerEntity p) {
        if (p.hasVehicle()) p.stopRiding();
        double x = MathHelper.clamp(p.getX(), box.minX() + 1.0, box.maxX());
        double z = MathHelper.clamp(p.getZ(), box.minZ() + 1.0, box.maxZ());
        double y = p.getY();
        if (!world.isSpaceEmpty(p, p.getBoundingBox().offset(x - p.getX(), 0, z - p.getZ()))) {
            y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(x), MathHelper.floor(z));
        }
        p.teleport(world, x, y, z, p.getYaw(), p.getPitch());
        p.setVelocity(Vec3d.ZERO);
        p.velocityModified = true;
        long now = world.getTime();
        Long last = LAST_WARNING.get(p.getUuid());
        if (last == null || now - last > 60) {
            LAST_WARNING.put(p.getUuid(), now);
            p.sendMessage(Text.literal("Limite du monde : seul le Voyageur peut aller au-delà.").formatted(Formatting.LIGHT_PURPLE), true);
        }
    }

    /** Rideau de particules sur la portion de limite proche du joueur (visible par lui seul). */
    private static void curtain(ServerWorld world, Box box, ServerPlayerEntity p) {
        double x = p.getX(), z = p.getZ(), y = p.getY();
        double[][] edges = { // {coordonnée fixe, axe (0 = X fixe, 1 = Z fixe), début, fin}
                {box.minX(), 0, box.minZ(), box.maxZ() + 1}, {box.maxX() + 1, 0, box.minZ(), box.maxZ() + 1},
                {box.minZ(), 1, box.minX(), box.maxX() + 1}, {box.maxZ() + 1, 1, box.minX(), box.maxX() + 1}};
        for (double[] e : edges) {
            boolean xFixed = e[1] == 0;
            double dist = Math.abs((xFixed ? x : z) - e[0]);
            if (dist > 20) continue;
            double along = xFixed ? z : x;
            double from = Math.max(e[2], along - 12), to = Math.min(e[3], along + 12);
            for (double t = Math.floor(from); t <= to; t += 1.5) {
                for (double dy = -0.5; dy <= 3.5; dy += 1.0) {
                    double px = xFixed ? e[0] : t, pz = xFixed ? t : e[0];
                    world.spawnParticles(p, CURTAIN, true, px, y + dy, pz, 1, 0.1, 0.4, 0.1, 0);
                }
            }
        }
    }

    /** Voyageur au-delà de la limite : 1 XP tous les N blocs parcourus. */
    private static void exploration(ServerPlayerEntity p) {
        Vec3d now = p.getPos();
        Vec3d last = LAST_POS.put(p.getUuid(), now);
        if (last == null) return;
        double d = Math.hypot(now.x - last.x, now.z - last.z);
        if (d > 64) return; // téléportation : ne compte pas
        double total = TRAVELED.getOrDefault(p.getUuid(), 0.0) + d;
        int step = Math.max(1, SideJobConfig.data().voyageurBlocsParXp);
        int xp = (int) (total / step);
        TRAVELED.put(p.getUuid(), total - xp * step);
        if (xp > 0) SideJobs.addActionXp(p, SideJob.VOYAGEUR, xp);
    }

    /**
     * Point d'arrivée de la balise : de l'autre côté de la limite la plus proche (vers l'extérieur),
     * ou juste à l'intérieur (pour revenir). Hauteur choisie au sol, en évitant l'eau et la lave si possible.
     */
    public static Vec3d destination(ServerWorld world, Box box, Vec3d from, boolean outward, int distance) {
        double x = from.x, z = from.z;
        int tx, tz, stepX = 0, stepZ = 0;
        if (outward) {
            double left = x - box.minX(), right = box.maxX() + 1 - x, top = z - box.minZ(), bottom = box.maxZ() + 1 - z;
            double min = Math.min(Math.min(left, right), Math.min(top, bottom));
            tx = MathHelper.floor(x);
            tz = MathHelper.floor(z);
            if (min == left) { tx = box.minX() - distance; stepX = -8; }
            else if (min == right) { tx = box.maxX() + distance; stepX = 8; }
            else if (min == top) { tz = box.minZ() - distance; stepZ = -8; }
            else { tz = box.maxZ() + distance; stepZ = 8; }
        } else {
            int margin = 8;
            tx = (int) Math.round(clampInside(x, box.minX(), box.maxX(), margin));
            tz = (int) Math.round(clampInside(z, box.minZ(), box.maxZ(), margin));
            stepX = Integer.signum((box.minX() + box.maxX()) / 2 - tx) * 8;
            stepZ = Integer.signum((box.minZ() + box.maxZ()) / 2 - tz) * 8;
        }
        BlockPos best = null;
        for (int i = 0; i < 6; i++) {
            int cx = tx + stepX * i, cz = tz + stepZ * i;
            world.getChunk(cx >> 4, cz >> 4); // charge (et génère si besoin) le chunk d'arrivée
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, cx, cz);
            BlockPos pos = new BlockPos(cx, y, cz);
            if (best == null) best = pos;
            if (world.getFluidState(pos.down()).isEmpty() && world.getWorldBorder().contains(pos)) {
                best = pos;
                break;
            }
        }
        return Vec3d.ofBottomCenter(best);
    }

    private static double clampInside(double v, int min, int max, int margin) {
        if (max - min < 2 * margin) return (min + max) / 2.0;
        return MathHelper.clamp(v, min + margin, max - margin);
    }
}
