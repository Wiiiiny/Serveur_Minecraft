package fr.serveur.rpg.mixin;

import fr.serveur.rpg.jobs.SideJobRules;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ForgingScreenHandler;
import net.minecraft.screen.Property;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Enclume : seul l'Enchanteur peut y appliquer un livre enchanté ou fusionner des objets enchantés.
 * Réparer avec des matériaux et renommer restent possibles pour tout le monde.
 */
@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerMixin extends ForgingScreenHandler {
    @Shadow @Final private Property levelCost;

    protected AnvilScreenHandlerMixin(ScreenHandlerType<?> type, int syncId, PlayerInventory inv, ScreenHandlerContext ctx) {
        super(type, syncId, inv, ctx);
    }

    @Inject(method = "updateResult", at = @At("TAIL"))
    private void survierpg$enchanterOnly(CallbackInfo ci) {
        if (player.getWorld().isClient || !SideJobRules.isMagic(input.getStack(1))) return;
        if (SideJobRules.canUseMagicOnAnvil(player)) return;
        if (!output.getStack(0).isEmpty()) {
            player.sendMessage(Text.literal("Seul l'Enchanteur peut appliquer des enchantements à l'enclume.").formatted(Formatting.RED), true);
        }
        output.setStack(0, ItemStack.EMPTY);
        levelCost.set(0);
        sendContentUpdates();
    }

    @Inject(method = "onTakeOutput", at = @At("HEAD"))
    private void survierpg$xp(PlayerEntity taker, ItemStack stack, CallbackInfo ci) {
        if (!taker.getWorld().isClient && SideJobRules.isMagic(input.getStack(1))) SideJobRules.afterAnvilMagic(taker);
    }
}
