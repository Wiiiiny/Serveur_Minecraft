package fr.serveur.rpg.mixin;

import fr.serveur.rpg.jobs.SideJobRules;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Alambic : les améliorations (durée, puissance) et les potions jetables / persistantes demandent
 * un niveau d'Alchimiste. L'alambic ne brasse pas tant que l'ingrédient est trop avancé.
 */
@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandBlockEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private static void survierpg$begin(World world, BlockPos pos, BlockState state, BrewingStandBlockEntity stand, CallbackInfo ci) {
        SideJobRules.brewingNow = stand;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private static void survierpg$end(World world, BlockPos pos, BlockState state, BrewingStandBlockEntity stand, CallbackInfo ci) {
        SideJobRules.brewingNow = null;
    }

    @Inject(method = "canCraft", at = @At("HEAD"), cancellable = true)
    private static void survierpg$level(DefaultedList<ItemStack> slots, CallbackInfoReturnable<Boolean> cir) {
        if (!SideJobRules.brewingAllowed(SideJobRules.brewingNow, slots.get(3))) cir.setReturnValue(false);
    }
}
