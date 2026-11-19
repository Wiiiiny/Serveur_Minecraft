package fr.serveur.rpg.mixin;

import fr.serveur.rpg.protection.Protection;
import net.minecraft.block.FireBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Le feu ne brûle pas les blocs des zones protégées ni les coffres possédés. */
@Mixin(FireBlock.class)
public abstract class FireBlockMixin {
    @Inject(method = "trySpreadingFire", at = @At("HEAD"), cancellable = true)
    private void survierpg$noBurnProtected(World world, BlockPos pos, int spreadFactor, Random random, int currentAge, CallbackInfo ci) {
        if (Protection.isShielded(world, pos)) ci.cancel();
    }
}
