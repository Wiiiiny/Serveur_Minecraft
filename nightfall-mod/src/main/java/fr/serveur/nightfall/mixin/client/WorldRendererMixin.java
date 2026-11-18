package fr.serveur.nightfall.mixin.client;

import fr.serveur.nightfall.NightfallClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * La nuit, plus de ciel, de lune, d'étoiles ni de nuages : tout est noyé dans la brume.
 * Les chunks pas encore chargés se fondent donc dans le noir au lieu de montrer le ciel.
 */
@Mixin(value = WorldRenderer.class, priority = 900)
public abstract class WorldRendererMixin {
    private static boolean nightfall$shouldHideSky() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || !NightfallClient.fogApplies(mc.gameRenderer.getCamera())) return false;
        return NightfallClient.nightFactor(mc.world) > 0.5f;
    }

    @Inject(method = "renderSky(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void nightfall$hideSky(CallbackInfo ci) {
        if (nightfall$shouldHideSky()) ci.cancel();
    }

    @Inject(method = "renderClouds(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FDDD)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void nightfall$hideClouds(CallbackInfo ci) {
        if (nightfall$shouldHideSky()) ci.cancel();
    }
}
