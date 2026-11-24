package fr.serveur.rpg.mixin;

import fr.serveur.rpg.jobs.SideJobRules;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Coffre à butin (donjon, temple...) ouvert pour la première fois : XP d'Enchanteur pour les livres enchantés trouvés. */
@Mixin(LootableContainerBlockEntity.class)
public abstract class LootableContainerBlockEntityMixin {
    @Shadow protected Identifier lootTableId;
    @Unique private boolean survierpg$hadLoot;

    @Inject(method = "checkLootInteraction", at = @At("HEAD"))
    private void survierpg$before(PlayerEntity player, CallbackInfo ci) {
        survierpg$hadLoot = lootTableId != null;
    }

    @Inject(method = "checkLootInteraction", at = @At("RETURN"))
    private void survierpg$after(PlayerEntity player, CallbackInfo ci) {
        if (!survierpg$hadLoot || player == null || lootTableId != null) return;
        survierpg$hadLoot = false;
        SideJobRules.afterLootGenerated(player, (LootableContainerBlockEntity) (Object) this);
    }
}
