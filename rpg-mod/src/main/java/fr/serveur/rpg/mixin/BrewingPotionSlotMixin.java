package fr.serveur.rpg.mixin;

import fr.serveur.rpg.jobs.SideJobRules;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Potion retirée de l'alambic : XP d'Alchimiste. */
@Mixin(targets = "net.minecraft.screen.BrewingStandScreenHandler$PotionSlot")
public abstract class BrewingPotionSlotMixin {
    @Inject(method = "onTakeItem", at = @At("HEAD"))
    private void survierpg$xp(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (!player.getWorld().isClient) SideJobRules.onPotionTaken(player, stack);
    }
}
