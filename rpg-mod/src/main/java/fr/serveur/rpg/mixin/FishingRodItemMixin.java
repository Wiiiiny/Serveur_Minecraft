package fr.serveur.rpg.mixin;

import fr.serveur.rpg.jobs.SideJobRules;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Le Pêcheur expérimenté pêche plus vite (Appât) et attrape plus de trésors (Chance de la mer).
 * On retient le pêcheur au début de {@code use}, puis on ajoute son bonus aux niveaux d'enchantement lus par la canne.
 */
@Mixin(FishingRodItem.class)
public abstract class FishingRodItemMixin {
    @Unique private static PlayerEntity survierpg$fisher;

    @Inject(method = "use", at = @At("HEAD"))
    private void survierpg$begin(World world, PlayerEntity user, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        survierpg$fisher = world.isClient ? null : user;
    }

    @Inject(method = "use", at = @At("RETURN"))
    private void survierpg$end(World world, PlayerEntity user, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        survierpg$fisher = null;
    }

    @Redirect(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/enchantment/EnchantmentHelper;getLure(Lnet/minecraft/item/ItemStack;)I"))
    private int survierpg$lure(ItemStack rod) {
        int base = EnchantmentHelper.getLure(rod);
        // Au-delà d'Appât V, le poisson ne mord plus jamais : on plafonne.
        return survierpg$fisher == null ? base : Math.min(5, base + SideJobRules.fishingLureBonus(survierpg$fisher));
    }

    @Redirect(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/enchantment/EnchantmentHelper;getLuckOfTheSea(Lnet/minecraft/item/ItemStack;)I"))
    private int survierpg$luck(ItemStack rod) {
        int base = EnchantmentHelper.getLuckOfTheSea(rod);
        return survierpg$fisher == null ? base : base + SideJobRules.fishingLuckBonus(survierpg$fisher);
    }
}
