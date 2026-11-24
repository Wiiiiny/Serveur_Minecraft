package fr.serveur.rpg.mixin;

import fr.serveur.rpg.jobs.SideJobRules;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.village.TradeOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Troc avec un villageois (ou un marchand ambulant) : XP d'Enchanteur. */
@Mixin(MerchantEntity.class)
public abstract class MerchantEntityMixin {
    @Inject(method = "trade", at = @At("HEAD"))
    private void survierpg$trade(TradeOffer offer, CallbackInfo ci) {
        MerchantEntity self = (MerchantEntity) (Object) this;
        if (!self.getWorld().isClient && self.getCustomer() != null) SideJobRules.onTrade(self.getCustomer(), offer);
    }
}
