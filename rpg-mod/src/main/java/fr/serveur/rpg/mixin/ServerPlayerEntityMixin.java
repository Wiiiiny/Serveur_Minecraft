package fr.serveur.rpg.mixin;

import fr.serveur.rpg.death.DeathSystem;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Règles de mort du serveur, appliquées avant que l'inventaire ne tombe. */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {
    @Inject(method = "onDeath", at = @At("HEAD"))
    private void survierpg$deathRules(DamageSource source, CallbackInfo ci) {
        DeathSystem.onDeath((ServerPlayerEntity) (Object) this, source);
    }
}
