package fr.serveur.rpg.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Phantoms : le délai entre deux piqués est doublé (cadence d'attaque divisée par deux). */
@Mixin(targets = "net.minecraft.entity.mob.PhantomEntity$StartAttackGoal")
public abstract class PhantomStartAttackGoalMixin {
    @Shadow private int cooldown;
    @Unique private int survierpg$before;

    @Inject(method = "tick", at = @At("HEAD"))
    private void survierpg$rememberCooldown(CallbackInfo ci) {
        survierpg$before = cooldown;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void survierpg$doubleCooldown(CallbackInfo ci) {
        if (cooldown > survierpg$before) cooldown *= 2; // le délai vient d'être relancé
    }

    @Inject(method = "start", at = @At("TAIL"))
    private void survierpg$doubleFirstCooldown(CallbackInfo ci) {
        cooldown *= 2;
    }
}
