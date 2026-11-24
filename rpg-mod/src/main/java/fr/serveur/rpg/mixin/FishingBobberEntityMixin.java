package fr.serveur.rpg.mixin;

import fr.serveur.rpg.jobs.SideJobRules;
import net.minecraft.advancement.criterion.FishingRodHookedCriterion;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collection;

/** Prise de pêche : XP du Pêcheur (on se branche là où le jeu déclenche le succès « pêche »). */
@Mixin(FishingBobberEntity.class)
public abstract class FishingBobberEntityMixin {
    @Redirect(method = "use", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/advancement/criterion/FishingRodHookedCriterion;trigger(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/projectile/FishingBobberEntity;Ljava/util/Collection;)V"))
    private void survierpg$fished(FishingRodHookedCriterion criterion, ServerPlayerEntity player, ItemStack rod,
                                  FishingBobberEntity bobber, Collection<ItemStack> loot) {
        criterion.trigger(player, rod, bobber, loot);
        SideJobRules.onFished(player, loot);
    }
}
