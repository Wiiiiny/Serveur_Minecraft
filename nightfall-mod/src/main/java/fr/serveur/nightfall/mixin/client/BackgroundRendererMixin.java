package fr.serveur.nightfall.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.serveur.nightfall.NightfallClient;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.FogShape;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Brume noire et épaisse la nuit : on ne voit qu'à quelques blocs. */
@Mixin(BackgroundRenderer.class)
public abstract class BackgroundRendererMixin {
    @Shadow private static float red;
    @Shadow private static float green;
    @Shadow private static float blue;

    /** Couleur de la brume (et du fond) tirée vers le noir. */
    @Inject(method = "render", at = @At("TAIL"))
    private static void nightfall$nightFogColor(Camera camera, float tickDelta, ClientWorld world, int viewDistance,
                                                float skyDarkness, CallbackInfo ci) {
        if (!NightfallClient.fogApplies(camera)) return;
        float night = NightfallClient.easedNight(world);
        if (night <= 0f) return;
        float[] c = NightfallClient.nightFogColor(tickDelta);
        red = MathHelper.lerp(night, red, c[0]);
        green = MathHelper.lerp(night, green, c[1]);
        blue = MathHelper.lerp(night, blue, c[2]);
        RenderSystem.clearColor(red, green, blue, 0f);
    }

    /** Distance de la brume. */
    @Inject(method = "applyFog", at = @At("RETURN"))
    private static void nightfall$nightFog(Camera camera, BackgroundRenderer.FogType fogType, float viewDistance,
                                           boolean thickFog, float tickDelta, CallbackInfo ci) {
        if (!NightfallClient.fogApplies(camera)) return;
        float night = NightfallClient.easedNight(camera.getFocusedEntity().getWorld());
        if (night <= 0f) return;

        float currentEnd = RenderSystem.getShaderFogEnd();
        float fogEnd = MathHelper.lerp(night, currentEnd, NightfallClient.nightFogDistance(tickDelta));
        if (fogEnd >= currentEnd) return;

        if (fogType == BackgroundRenderer.FogType.FOG_SKY) {
            RenderSystem.setShaderFogStart(0f);
        } else {
            RenderSystem.setShaderFogStart(MathHelper.lerp(night, RenderSystem.getShaderFogStart(),
                    NightfallClient.nightFogStart(tickDelta)));
            RenderSystem.setShaderFogShape(FogShape.SPHERE);
        }
        RenderSystem.setShaderFogEnd(fogEnd);
    }
}
