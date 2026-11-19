package fr.serveur.rpg.mixin;

import fr.serveur.rpg.craft.CraftRules;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Établi et inventaire 2x2 : les objets contrôlés par le GM ne sortent pas du craft. */
@Mixin(CraftingScreenHandler.class)
public abstract class CraftingScreenHandlerMixin {
    @Inject(method = "updateResult", at = @At("TAIL"))
    private static void survierpg$blockControlledCrafts(ScreenHandler handler, World world, PlayerEntity player,
                                                        RecipeInputInventory craftingInventory, CraftingResultInventory resultInventory,
                                                        CallbackInfo ci) {
        if (world.isClient || !(player instanceof ServerPlayerEntity sp) || sp.isCreative()) return;
        if (!CraftRules.isBlocked(resultInventory.getStack(0))) return;
        resultInventory.setStack(0, ItemStack.EMPTY);
        handler.setPreviousTrackedSlot(0, ItemStack.EMPTY);
        sp.networkHandler.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(handler.syncId, handler.nextRevision(), 0, ItemStack.EMPTY));
        sp.sendMessage(Text.literal("Craft contrôlé : apporte les matériaux au GM pour obtenir l'objet officiel.").formatted(Formatting.RED), true);
    }
}
