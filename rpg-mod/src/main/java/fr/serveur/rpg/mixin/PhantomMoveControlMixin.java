package fr.serveur.rpg.mixin;

import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Phantoms deux fois plus lents. Le contrôle de vol fait v += (cible - v) * 0.2 à chaque tick ;
 * en multipliant ensuite la vitesse par 5/6, la vitesse d'équilibre tombe à la moitié de la cible.
 */
@Mixin(targets = "net.minecraft.entity.mob.PhantomEntity$PhantomMoveControl")
public abstract class PhantomMoveControlMixin extends MoveControl {
    private PhantomMoveControlMixin(MobEntity entity) {
        super(entity);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void survierpg$halfSpeed(CallbackInfo ci) {
        if (!entity.getWorld().isClient) entity.setVelocity(entity.getVelocity().multiply(5.0 / 6.0));
    }
}
