package fr.serveur.rpg.mixin;

import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.mining.PlacedBlocks;
import fr.serveur.rpg.protection.Protection;
import net.minecraft.block.ChestBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * - Note les blocs posés par les joueurs (pas d'XP quand on les recasse).
 * - Un coffre posé appartient à celui qui le pose ; interdit de le coller au coffre d'un autre.
 */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;", at = @At("HEAD"), cancellable = true)
    private void survierpg$noForeignDoubleChest(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
        if (context.getWorld().isClient || !(context.getPlayer() instanceof ServerPlayerEntity player)) return;
        if (!(((BlockItem) (Object) this).getBlock() instanceof ChestBlock) || player.isCreative()) return;
        RpgState rs = RpgState.get(player.getServer());
        if (Protection.touchesForeignChest(rs, context.getWorld(), context.getBlockPos(), player.getUuid())) {
            player.sendMessage(Text.literal("Impossible de coller ton coffre à celui d'un autre joueur.").formatted(Formatting.RED), true);
            cir.setReturnValue(ActionResult.FAIL);
        }
    }

    @Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;", at = @At("RETURN"))
    private void survierpg$trackPlaced(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
        if (context.getWorld().isClient || context.getPlayer() == null || !cir.getReturnValue().isAccepted()) return;
        PlacedBlocks.add(context.getWorld(), context.getBlockPos());
        if (context.getPlayer() instanceof ServerPlayerEntity player
                && context.getWorld().getBlockState(context.getBlockPos()).getBlock() instanceof ChestBlock) {
            RpgState.get(player.getServer()).setChestOwner(context.getWorld(), context.getBlockPos(), player.getUuid());
        }
    }
}
