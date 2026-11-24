package fr.serveur.rpg.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Exotique au sol perdu : tué par une commande (/kill) ou tombé dans le vide. La destruction par dégâts (lave, feu,
 * explosion, cactus) passe par {@link ItemStackMixin}, la disparition après 5 minutes par {@link ItemEntityMixin}.
 * Un objet ramassé (joueur, entonnoir) n'est jamais compté comme perdu.
 */
@Mixin(Entity.class)
public abstract class EntityRemoveMixin {
    @Inject(method = "remove", at = @At("HEAD"))
    private void survierpg$exoticKilled(Entity.RemovalReason reason, CallbackInfo ci) {
        if (reason == Entity.RemovalReason.KILLED && (Object) this instanceof ItemEntity item) {
            fr.serveur.rpg.sombre.Exotiques.onItemEntityLost(item, "supprimé");
        }
    }

    @Inject(method = "tickInVoid", at = @At("HEAD"))
    private void survierpg$exoticVoid(CallbackInfo ci) {
        if ((Object) this instanceof ItemEntity item) fr.serveur.rpg.sombre.Exotiques.onItemEntityLost(item, "tombé dans le vide");
    }
}
