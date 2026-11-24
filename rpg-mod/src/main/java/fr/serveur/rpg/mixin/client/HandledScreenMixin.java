package fr.serveur.rpg.mixin.client;

import fr.serveur.rpg.client.RareteClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Contour de rareté autour des objets dans les inventaires et coffres. */
@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
    @Inject(method = "drawSlot", at = @At("TAIL"))
    private void survierpg$rarity(DrawContext context, Slot slot, CallbackInfo ci) {
        RareteClient.outline(context, slot.getStack(), slot.x, slot.y);
    }
}
