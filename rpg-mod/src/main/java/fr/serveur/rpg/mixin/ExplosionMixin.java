package fr.serveur.rpg.mixin;

import fr.serveur.rpg.protection.Protection;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Les explosions ne détruisent rien dans les zones protégées ni les coffres possédés. */
@Mixin(Explosion.class)
public abstract class ExplosionMixin {
    @Shadow @Final private World world;

    @Shadow public abstract List<BlockPos> getAffectedBlocks();

    @Inject(method = "affectWorld", at = @At("HEAD"))
    private void survierpg$shieldBlocks(boolean particles, CallbackInfo ci) {
        if (world.isClient) return;
        getAffectedBlocks().removeIf(pos -> Protection.isShielded(world, pos));
    }
}
