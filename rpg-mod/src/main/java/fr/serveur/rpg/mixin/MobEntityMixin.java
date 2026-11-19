package fr.serveur.rpg.mixin;

import fr.serveur.rpg.mobs.MobRules;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Zombies, squelettes, araignées et pillards ne ciblent un joueur que s'il les a attaqués. */
@Mixin(MobEntity.class)
public abstract class MobEntityMixin {
    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void survierpg$neutralMobs(LivingEntity target, CallbackInfo ci) {
        MobEntity self = (MobEntity) (Object) this;
        if (target instanceof PlayerEntity player && !self.getWorld().isClient
                && MobRules.isNeutralType(self) && !MobRules.isProvokedBy(self, player)) {
            ci.cancel();
        }
    }
}
