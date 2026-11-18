package fr.serveur.nightfall.mixin.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import fr.serveur.nightfall.NightfallClient;
import fr.serveur.nightfall.NightfallConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bords de l'écran beaucoup plus sombres la nuit (dessiné sous la barre d'items). */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    private static final Identifier NIGHTFALL$VIGNETTE = new Identifier("textures/misc/vignette.png");

    @Shadow private int scaledWidth;
    @Shadow private int scaledHeight;
    @Shadow @Final private MinecraftClient client;

    @Inject(method = "renderVignetteOverlay", at = @At("TAIL"), require = 0)
    private void nightfall$nightVignette(DrawContext context, Entity entity, CallbackInfo ci) {
        float night = NightfallClient.nightFactor(client.world);
        if (night <= 0f) return;
        float strength = NightfallConfig.NIGHT_VIGNETTE * night
                * (1f - 0.35f * NightfallClient.lanternBlend(client.getTickDelta()));

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.ZERO, GlStateManager.DstFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ZERO);
        // Deux passes pour un noir plus profond dans les coins.
        for (int i = 0; i < 2; i++) {
            context.setShaderColor(strength, strength, strength, 1f);
            context.drawTexture(NIGHTFALL$VIGNETTE, 0, 0, -90, 0f, 0f, scaledWidth, scaledHeight, scaledWidth, scaledHeight);
        }
        context.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
    }
}
