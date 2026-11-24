package fr.serveur.rpg.mixin;

import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Objets exotiques au sol : particules jaunes ; disparition après 5 minutes = exotique perdu. */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void survierpg$exotic(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (self.isRemoved()) {
            if (self.getItemAge() >= 6000) fr.serveur.rpg.sombre.Exotiques.onItemEntityLost(self, "disparu au sol");
            return;
        }
        fr.serveur.rpg.sombre.Exotiques.onItemEntityTick(self);
    }
}
