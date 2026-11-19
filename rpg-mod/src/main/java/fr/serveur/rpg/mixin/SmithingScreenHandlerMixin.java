package fr.serveur.rpg.mixin;

import fr.serveur.rpg.craft.CraftRules;
import fr.serveur.rpg.tools.OfficialTools;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.SmithingScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Table de forge : on ne peut améliorer (Netherite...) un outil ou une arme contrôlé
 * que s'il est officiel — l'objet garde alors son identifiant.
 */
@Mixin(SmithingScreenHandler.class)
public abstract class SmithingScreenHandlerMixin {
    private static final int BASE_SLOT = 1, RESULT_SLOT = 3;

    @Inject(method = "updateResult", at = @At("TAIL"))
    private void survierpg$officialOnly(CallbackInfo ci) {
        ScreenHandler self = (ScreenHandler) (Object) this;
        ItemStack result = self.getSlot(RESULT_SLOT).getStack();
        if (!CraftRules.isBlocked(result)) return;
        ItemStack base = self.getSlot(BASE_SLOT).getStack();
        if (base.getSubNbt(OfficialTools.NBT_KEY) == null) self.getSlot(RESULT_SLOT).setStackNoCallbacks(ItemStack.EMPTY);
    }
}
