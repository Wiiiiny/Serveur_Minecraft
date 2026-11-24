package fr.serveur.rpg.mixin;

import fr.serveur.rpg.jobs.SideJobRules;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Table d'enchantement : réservée à l'Enchanteur, lignes 2 et 3 débloquées avec le niveau ; XP à chaque enchantement. */
@Mixin(EnchantmentScreenHandler.class)
public abstract class EnchantmentScreenHandlerMixin {
    @Inject(method = "onButtonClick", at = @At("HEAD"), cancellable = true)
    private void survierpg$checkLine(PlayerEntity player, int id, CallbackInfoReturnable<Boolean> cir) {
        if (!(player instanceof ServerPlayerEntity sp) || id < 0 || id > 2) return;
        String refusal = SideJobRules.checkEnchantLine(sp, id);
        if (refusal != null) {
            sp.sendMessage(Text.literal(refusal).formatted(Formatting.RED), true);
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "onButtonClick", at = @At("RETURN"))
    private void survierpg$xp(PlayerEntity player, int id, CallbackInfoReturnable<Boolean> cir) {
        if (player instanceof ServerPlayerEntity sp && id >= 0 && id <= 2 && cir.getReturnValueZ()) SideJobRules.afterEnchant(sp, id);
    }
}
