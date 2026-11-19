package fr.serveur.rpg.mixin;

import fr.serveur.rpg.data.RpgState;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Le Nether et l'End restent fermés tant que le GM ne les a pas ouverts. */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerPortalMixin {
    @Inject(method = "moveToWorld", at = @At("HEAD"), cancellable = true)
    private void survierpg$lockedDimensions(ServerWorld destination, CallbackInfoReturnable<Entity> cir) {
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        if (self.isCreative() || self.isSpectator()) return;
        RpgState rs = RpgState.get(self.getServer());
        boolean locked = (destination.getRegistryKey() == World.NETHER && !rs.netherOpen)
                || (destination.getRegistryKey() == World.END && !rs.endOpen);
        if (locked) {
            self.sendMessage(Text.literal("Cette dimension est scellée. Seul le GM peut l'ouvrir.").formatted(Formatting.DARK_PURPLE), true);
            cir.setReturnValue(self);
        }
    }
}
