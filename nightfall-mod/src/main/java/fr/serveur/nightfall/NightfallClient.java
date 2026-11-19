package fr.serveur.nightfall;

import fr.serveur.nightfall.client.HorrorAmbience;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.CameraSubmersionType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class NightfallClient implements ClientModInitializer {
    /** 0 = pas de lanterne, 1 = lanterne en main (transition douce). */
    private static float lanternBlend, prevLanternBlend;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(NightfallClient::tick);
        HorrorAmbience.register();
        fr.serveur.nightfall.client.NightEffects.register();
    }

    private static void tick(MinecraftClient client) {
        prevLanternBlend = lanternBlend;
        float target = client.player != null && Nightfall.holdsLantern(client.player) ? 1f : 0f;
        lanternBlend += MathHelper.clamp(target - lanternBlend, -0.1f, 0.1f);
    }

    public static float lanternBlend(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevLanternBlend, lanternBlend);
    }

    /** 0 = jour, 1 = nuit complète, avec transitions au crépuscule et à l'aube. */
    public static float nightFactor(World world) {
        if (world == null || !Nightfall.isAffectedWorld(world)) return 0f;
        float t = Nightfall.timeOfDay(world);
        if (t < NightfallConfig.DUSK_START) return 0f;
        if (t < NightfallConfig.NIGHT_START)
            return (t - NightfallConfig.DUSK_START) / (NightfallConfig.NIGHT_START - NightfallConfig.DUSK_START);
        if (t < NightfallConfig.NIGHT_END) return 1f;
        return 1f - (t - NightfallConfig.NIGHT_END) / (24000f - NightfallConfig.NIGHT_END);
    }

    /** Facteur de nuit « accéléré » : la brume s'installe vite au début du crépuscule. */
    public static float easedNight(World world) {
        float n = nightFactor(world);
        float inv = 1f - n;
        return 1f - inv * inv * inv;
    }

    /** Vrai si la brume du mod doit s'appliquer (pas sous l'eau, pas aveuglé...). */
    public static boolean fogApplies(Camera camera) {
        if (camera.getSubmersionType() != CameraSubmersionType.NONE) return false;
        Entity entity = camera.getFocusedEntity();
        if (entity == null) return false;
        return !(entity instanceof LivingEntity living
                && (living.hasStatusEffect(StatusEffects.BLINDNESS) || living.hasStatusEffect(StatusEffects.DARKNESS)));
    }

    /** Distance de brume de nuit, selon la lanterne. */
    public static float nightFogDistance(float tickDelta) {
        return MathHelper.lerp(lanternBlend(tickDelta), NightfallConfig.NIGHT_FOG_DISTANCE, NightfallConfig.LANTERN_FOG_DISTANCE)
                * fr.serveur.nightfall.client.NightEffects.fogMultiplier(tickDelta);
    }

    /** Début de la brume : collée au joueur sans lanterne, un peu dégagée avec. */
    public static float nightFogStart(float tickDelta) {
        return MathHelper.lerp(lanternBlend(tickDelta), 0f, 3f);
    }

    public static float[] nightFogColor(float tickDelta) {
        float b = lanternBlend(tickDelta);
        float[] a = NightfallConfig.NIGHT_FOG_COLOR, l = NightfallConfig.LANTERN_FOG_COLOR;
        return new float[]{MathHelper.lerp(b, a[0], l[0]), MathHelper.lerp(b, a[1], l[1]), MathHelper.lerp(b, a[2], l[2])};
    }
}
