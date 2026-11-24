package fr.serveur.rpg.raid.statue;

import fr.serveur.rpg.SurvieRpg;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Effet « Peur » : monte d'un niveau par seconde tant qu'une statue est à moins de 12 blocs, redescend d'un niveau
 * par seconde sinon, tombe à zéro si la statue est détruite.
 * 1-3 : lenteur, vignette sombre, battements de cœur ; 4-6 : nausée, obscurité (vignette épaisse), particules ; 7+ : 1 cœur par seconde
 * (ignore l'armure). Être touché par une statue fait passer directement au niveau 5. La vignette est dessinée
 * côté client (niveau envoyé par le paquet survierpg:peur).
 */
public final class Fear {
    public static final Identifier PACKET = new Identifier(SurvieRpg.MOD_ID, "peur");
    public static final double RADIUS = 12;
    private static final Map<UUID, Integer> LEVELS = new HashMap<>();

    private Fear() {}

    public static int level(ServerPlayerEntity p) {
        return LEVELS.getOrDefault(p.getUuid(), 0);
    }

    /** Une fois par seconde. */
    public static void tick(ServerWorld w, long now) {
        LEVELS.keySet().removeIf(id -> w.getServer().getPlayerManager().getPlayer(id) == null);
        for (ServerPlayerEntity p : w.getServer().getPlayerManager().getPlayerList()) {
            int before = level(p);
            boolean near = p.getWorld() == w && !p.isSpectator() && p.isAlive()
                    && !w.getEntitiesByClass(StatueEntity.class, new Box(p.getBlockPos()).expand(RADIUS), s -> s.isAlive() && s.squaredDistanceTo(p) < RADIUS * RADIUS).isEmpty();
            int lvl = near ? Math.min(10, before + 1) : Math.max(0, before - 1);
            set(p, lvl);
            if (lvl > 0) apply(w, p, lvl);
        }
    }

    private static void apply(ServerWorld w, ServerPlayerEntity p, int lvl) {
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 0, false, false));
        p.playSound(SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 0.4f + lvl * 0.1f, 1f);
        if (lvl >= 4) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 0, false, false));
            // L'« obscurité » de la peur est dessinée par le client (vignette) : l'effet vanilla Obscurité
            // empêcherait de voir les statues, donc de les figer et de les briser.
            w.spawnParticles(p, ParticleTypes.ASH, true, p.getX(), p.getY() + 1, p.getZ(), 30, 1.2, 1, 1.2, 0.01);
        }
        if (lvl >= 7) p.damage(w.getDamageSources().magic(), 2f);
    }

    public static void onHit(ServerPlayerEntity p) {
        set(p, Math.max(level(p), 5));
    }

    /** Une statue détruite : la peur disparaît pour ceux qui n'ont plus de statue à côté. */
    public static void onStatueDestroyed(ServerWorld w, Vec3d where) {
        for (ServerPlayerEntity p : w.getPlayers(pl -> pl.getPos().distanceTo(where) < RADIUS * 2)) {
            boolean other = !w.getEntitiesByClass(StatueEntity.class, new Box(p.getBlockPos()).expand(RADIUS),
                    s -> s.isAlive() && !s.isRemoved() && s.getPos().distanceTo(where) > 0.1 && s.squaredDistanceTo(p) < RADIUS * RADIUS).isEmpty();
            if (!other) set(p, 0);
        }
    }

    private static void set(ServerPlayerEntity p, int lvl) {
        Integer old = LEVELS.put(p.getUuid(), lvl);
        if (old != null && old == lvl) return;
        if (!ServerPlayNetworking.canSend(p, PACKET)) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(lvl);
        ServerPlayNetworking.send(p, PACKET, buf);
    }
}
