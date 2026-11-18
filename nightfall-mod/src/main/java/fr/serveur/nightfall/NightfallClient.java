package fr.serveur.nightfall;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class NightfallClient implements ClientModInitializer {
    /** 0 = pas de lanterne, 1 = lanterne en main (transition douce). */
    private static float lanternBlend, prevLanternBlend;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(NightfallClient::tick);
    }

    private static void tick(MinecraftClient client) {
        prevLanternBlend = lanternBlend;
        float target = client.player != null && Nightfall.holdsLantern(client.player) ? 1f : 0f;
        lanternBlend += MathHelper.clamp(target - lanternBlend, -0.1f, 0.1f);
    }

    /** 0 = jour, 1 = nuit complète, avec transitions au crépuscule et à l'aube. */
    public static float nightFactor(World world) {
        if (!Nightfall.isAffectedWorld(world)) return 0f;
        float t = Nightfall.timeOfDay(world);
        if (t < NightfallConfig.DUSK_START) return 0f;
        if (t < NightfallConfig.NIGHT_START)
            return (t - NightfallConfig.DUSK_START) / (NightfallConfig.NIGHT_START - NightfallConfig.DUSK_START);
        if (t < NightfallConfig.NIGHT_END) return 1f;
        return 1f - (t - NightfallConfig.NIGHT_END) / (24000f - NightfallConfig.NIGHT_END);
    }

    /** Distance de brume de nuit, selon la lanterne. */
    public static float nightFogDistance(float tickDelta) {
        float blend = MathHelper.lerp(tickDelta, prevLanternBlend, lanternBlend);
        return MathHelper.lerp(blend, NightfallConfig.NIGHT_FOG_DISTANCE, NightfallConfig.LANTERN_FOG_DISTANCE);
    }
}
