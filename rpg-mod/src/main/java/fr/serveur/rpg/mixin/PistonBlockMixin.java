package fr.serveur.rpg.mixin;

import fr.serveur.rpg.data.RpgState;
import net.minecraft.block.PistonBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Les pistons ne fonctionnent pas à moins de 13 blocs d'une zone protégée (portée max d'un piston). */
@Mixin(PistonBlock.class)
public abstract class PistonBlockMixin {
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void survierpg$noPistonNearProtected(World world, BlockPos pos, Direction dir, boolean retract,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (world instanceof ServerWorld sw && RpgState.get(sw.getServer()).nearProtected(world, pos, 13)) {
            cir.setReturnValue(false);
        }
    }
}
