package fr.serveur.rpg.mixin.client;

import fr.serveur.rpg.client.RareteClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Contour de rareté dans la barre d'objets. */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method = "renderHotbarItem", at = @At("TAIL"))
    private void survierpg$rarity(DrawContext context, int x, int y, float tickDelta, PlayerEntity player, ItemStack stack, int seed, CallbackInfo ci) {
        RareteClient.outline(context, stack, x, y);
    }
}
