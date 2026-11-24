package fr.serveur.rpg.world;

import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.death.DeathSystem;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BedPart;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

/**
 * Cycle jour/nuit piloté par le serveur :
 * - durées du jour et de la nuit réglables séparément par le GM ;
 * - annonces « LA NUIT TOMBE » / « LE JOUR SE LÈVE » ;
 * - impossible de dormir (le lit sert seulement de point de réapparition).
 * Nuit = heure du monde entre 13000 et 23000 (comme le mod Nightfall).
 */
public final class DayCycle {
    public static final long NIGHT_START = 13000, NIGHT_END = 23000;
    private static double fraction;
    private static Boolean wasNight;

    private DayCycle() {}

    public static boolean isNight(World world) {
        long t = Math.floorMod(world.getTimeOfDay(), 24000L);
        return t >= NIGHT_START && t < NIGHT_END;
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                server.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server));

        ServerTickEvents.END_SERVER_TICK.register(DayCycle::tick);

        // Clic droit sur un lit : point de réapparition, mais pas de sommeil.
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            BlockState state = world.getBlockState(hit.getBlockPos());
            if (!(state.getBlock() instanceof BedBlock)) return ActionResult.PASS;
            if (sp.isSneaking() && !sp.getMainHandStack().isEmpty()) return ActionResult.PASS;
            BlockPos head = state.get(BedBlock.PART) == BedPart.HEAD ? hit.getBlockPos()
                    : hit.getBlockPos().offset(state.get(BedBlock.FACING));
            if (world.getDimension().bedWorks()) {
                sp.setSpawnPoint(world.getRegistryKey(), head, sp.getYaw(), false, false);
                sp.sendMessage(Text.literal("Point de réapparition défini. Impossible de dormir : la nuit doit être affrontée.")
                        .formatted(Formatting.GRAY), true);
            }
            return ActionResult.FAIL;
        });
    }

    private static void tick(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        RpgState state = RpgState.get(server);
        if (overworld.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE)) {
            overworld.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
        }

        if (fr.serveur.rpg.sombre.Lumiere.controls(server)) {
            // Nuit permanente : seul un Éclat de Lumière appelle le jour.
            fr.serveur.rpg.sombre.Lumiere.tick(server);
            boolean nowNight = isNight(overworld);
            if (wasNight != null && wasNight != nowNight) announce(server, nowNight);
            wasNight = nowNight;
            return;
        }
        long time = overworld.getTimeOfDay();
        boolean night = isNight(overworld);
        // Jour = 14000 ticks de temps de jeu, nuit = 10000 : on adapte la vitesse pour tenir les durées voulues.
        double minutes = night ? state.nightMinutes : state.dayMinutes;
        double phaseTicks = night ? (NIGHT_END - NIGHT_START) : (24000 - (NIGHT_END - NIGHT_START));
        fraction += phaseTicks / Math.max(0.1, minutes * 60 * 20);
        long step = (long) fraction;
        if (step > 0) {
            fraction -= step;
            for (ServerWorld w : server.getWorlds()) {
                if (w == overworld || !w.getDimension().hasFixedTime()) w.setTimeOfDay(time + step);
            }
        }

        boolean nowNight = isNight(overworld);
        if (wasNight != null && wasNight != nowNight) announce(server, nowNight);
        wasNight = nowNight;
    }

    private static void announce(MinecraftServer server, boolean night) {
        Text title = Text.literal(night ? "LA NUIT TOMBE" : "LE JOUR SE LÈVE")
                .formatted(night ? Formatting.DARK_RED : Formatting.GOLD, Formatting.BOLD);
        Text subtitle = Text.literal(night ? "Les morts reviennent... la brume se lève." : "La lumière brûle. Couvre-toi.")
                .formatted(Formatting.GRAY);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 60, 20));
            p.networkHandler.sendPacket(new TitleS2CPacket(title));
            p.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
            p.playSound(night ? SoundEvents.ENTITY_WITHER_SPAWN : SoundEvents.BLOCK_BELL_USE, SoundCategory.MASTER, 0.6f, night ? 0.6f : 1f);
        }
        if (night) DeathSystem.reviveAll(server);
    }
}
