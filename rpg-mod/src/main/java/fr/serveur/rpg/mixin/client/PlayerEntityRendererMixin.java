package fr.serveur.rpg.mixin.client;

import fr.serveur.rpg.client.NameTagCache;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Affiche les métiers du joueur sous son pseudo (« Mineur · Ingénieur »), comme la ligne de score
 * sous le nom en vanilla : on dessine notre ligne à la place du nom, puis on remonte le nom d'une ligne.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    @Unique private boolean survierpg$pushed;

    @Inject(method = "renderLabelIfPresent(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"))
    private void survierpg$jobsLine(AbstractClientPlayerEntity player, Text name, MatrixStack matrices,
                                    VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        survierpg$pushed = false;
        Text jobs = NameTagCache.get(player.getUuid());
        if (jobs == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getEntityRenderDispatcher().getSquaredDistanceToCamera(player) > 4096.0) return;

        boolean visibleThroughWalls = !player.isSneaky();
        matrices.push();
        matrices.translate(0.0F, player.getNameLabelHeight(), 0.0F);
        matrices.multiply(client.getEntityRenderDispatcher().getRotation());
        matrices.scale(-0.02F, -0.02F, 0.02F);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        TextRenderer font = client.textRenderer;
        int background = (int) (client.options.getTextBackgroundOpacity(0.25F) * 255.0F) << 24;
        float x = -font.getWidth(jobs) / 2f;
        font.draw(jobs, x, 0, 0x20FFFFFF, false, matrix, consumers,
                visibleThroughWalls ? TextRenderer.TextLayerType.SEE_THROUGH : TextRenderer.TextLayerType.NORMAL, background, light);
        if (visibleThroughWalls) font.draw(jobs, x, 0, -1, false, matrix, consumers, TextRenderer.TextLayerType.NORMAL, 0, light);
        matrices.pop();

        // Le pseudo (et l'éventuelle ligne de score vanilla) passe au-dessus de notre ligne.
        matrices.push();
        survierpg$pushed = true;
        matrices.translate(0.0F, 0.25F, 0.0F); // hauteur d'une ligne du pseudo (9 px × 0,025) + un petit écart
    }

    @Inject(method = "renderLabelIfPresent(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("RETURN"))
    private void survierpg$restore(AbstractClientPlayerEntity player, Text name, MatrixStack matrices,
                                   VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        if (survierpg$pushed) {
            survierpg$pushed = false;
            matrices.pop();
        }
    }
}
