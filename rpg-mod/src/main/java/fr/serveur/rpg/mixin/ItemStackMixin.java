package fr.serveur.rpg.mixin;

import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Objet au sol détruit par des dégâts (lave, feu, explosion, cactus) : un exotique est alors perdu. */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(method = "onItemEntityDestroyed", at = @At("HEAD"))
    private void survierpg$exoticDestroyed(ItemEntity entity, CallbackInfo ci) {
        fr.serveur.rpg.sombre.Exotiques.onItemEntityLost(entity, "détruit");
    }
}
