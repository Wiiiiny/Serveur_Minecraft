package fr.serveur.rpg.mixin;

import fr.serveur.rpg.data.RpgState;
import net.minecraft.block.entity.Hopper;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Un entonnoir (ou wagonnet entonnoir) ne peut pas vider un coffre qui appartient à un joueur. */
@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {
    @Inject(method = "getInputInventory", at = @At("RETURN"), cancellable = true)
    private static void survierpg$noStealingWithHoppers(World world, Hopper hopper, CallbackInfoReturnable<Inventory> cir) {
        if (cir.getReturnValue() == null || !(world instanceof ServerWorld sw)) return;
        BlockPos above = BlockPos.ofFloored(hopper.getHopperX(), hopper.getHopperY() + 1.0, hopper.getHopperZ());
        if (RpgState.get(sw.getServer()).chestOwner(world, above) != null) cir.setReturnValue(null);
    }

    /** Le butin verrouillé d'un mort (réservé au tueur ou à personne) n'est pas aspiré. */
    @Inject(method = "extract(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/entity/ItemEntity;)Z", at = @At("HEAD"), cancellable = true)
    private static void survierpg$noLockedLoot(Inventory inventory, net.minecraft.entity.ItemEntity item, CallbackInfoReturnable<Boolean> cir) {
        if (item.getCommandTags().contains(fr.serveur.rpg.death.DeathSystem.LOCKED_TAG)) cir.setReturnValue(false);
    }
}
