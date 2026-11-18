package fr.serveur.nightfall.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.serveur.nightfall.NightfallClient;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.CameraSubmersionType;
import net.minecraft.client.render.FogShape;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Brume épaisse la nuit : on ne voit qu'à quelques blocs. */
@Mixin(BackgroundRenderer.class)
public abstract class BackgroundRendererMixin {
    @Inject(method = "applyFog", at = @At("RETURN"))
    private static void nightfall$nightFog(Camera camera, BackgroundRenderer.FogType fogType, float viewDistance,
                                           boolean thickFog, float tickDelta, CallbackInfo ci) {
        if (camera.getSubmersionType() != CameraSubmersionType.NONE) return;
        Entity entity = camera.getFocusedEntity();
        if (entity == null) return;
        if (entity instanceof LivingEntity living
                && (living.hasStatusEffect(StatusEffects.BLINDNESS) || living.hasStatusEffect(StatusEffects.DARKNESS))) {
            return;
        }

        float night = NightfallClient.nightFactor(entity.getWorld());
        if (night <= 0f) return;

        float currentEnd = RenderSystem.getShaderFogEnd();
        float fogEnd = MathHelper.lerp(night, currentEnd, NightfallClient.nightFogDistance(tickDelta));
        if (fogEnd >= currentEnd) return;

        if (fogType == BackgroundRenderer.FogType.FOG_SKY) {
            RenderSystem.setShaderFogStart(0f);
        } else {
            RenderSystem.setShaderFogStart(MathHelper.lerp(night, RenderSystem.getShaderFogStart(), fogEnd * 0.1f));
            RenderSystem.setShaderFogShape(FogShape.SPHERE);
        }
        RenderSystem.setShaderFogEnd(fogEnd);
    }
}
