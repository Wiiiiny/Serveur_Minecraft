package fr.serveur.rpg.protection;

import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.mining.HarvestRules;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DoubleBlockProperties;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BoneMealItem;
import net.minecraft.item.BucketItem;
import net.minecraft.item.FireChargeItem;
import net.minecraft.item.FlintAndSteelItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MiningToolItem;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * Zones protégées (mairie) et protection des coffres.
 * Les explosions, pistons, feu et entonnoirs sont gérés par des mixins (package mixin).
 */
public final class Protection {
    private Protection() {}

    public static void register() {
        // Pas de PVP si l'attaquant OU la victime est dans une zone protégée.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity victim) || !(source.getAttacker() instanceof PlayerEntity attacker)) return true;
            if (attacker == victim) return true;
            RpgState rs = RpgState.get(victim.getServer());
            return !(rs.isProtected(victim.getWorld(), victim.getBlockPos()) || rs.isProtected(attacker.getWorld(), attacker.getBlockPos()));
        });
        AttackEntityCallback.EVENT.register((player, world, hand, target, hit) -> {
            if (world.isClient || !(target instanceof PlayerEntity) || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            RpgState rs = RpgState.get(sp.getServer());
            if (rs.isProtected(world, player.getBlockPos()) || rs.isProtected(world, target.getBlockPos())) {
                sp.sendMessage(Text.literal("Le PVP est interdit à la mairie.").formatted(Formatting.RED), true);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        // Clic droit sur un bloc : coffres verrouillés, et pas de pose / outils dans une zone protégée.
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity sp) || HarvestRules.isExempt(sp)) return ActionResult.PASS;
            RpgState rs = RpgState.get(sp.getServer());
            BlockPos pos = hit.getBlockPos();
            BlockState state = world.getBlockState(pos);

            if (state.getBlock() instanceof ChestBlock) {
                UUID owner = chestOwnerIncludingOtherHalf(rs, world, pos, state);
                // Accroupi avec un objet en main = on pose un bloc contre le coffre, sans l'ouvrir.
                boolean placing = sp.isSneaking() && (!sp.getMainHandStack().isEmpty() || !sp.getOffHandStack().isEmpty());
                if (owner != null && !owner.equals(sp.getUuid()) && !placing) {
                    ItemStack pick = Lockpick.isLockpick(sp.getMainHandStack()) ? sp.getMainHandStack()
                            : Lockpick.isLockpick(sp.getOffHandStack()) ? sp.getOffHandStack() : ItemStack.EMPTY;
                    if (!pick.isEmpty()) {
                        Lockpick.attempt(sp, (ServerWorld) world, pos, state, owner, pick);
                    } else {
                        sp.sendMessage(Text.literal("Coffre verrouillé — il appartient à "
                                + nameOf(rs, owner) + ". Il faudrait un Crochet...").formatted(Formatting.RED), true);
                    }
                    return ActionResult.FAIL;
                }
            }

            ItemStack held = sp.getStackInHand(hand);
            BlockPos target = pos.offset(hit.getSide());
            if (isWorldEditingItem(held.getItem()) && (rs.isProtected(world, pos) || rs.isProtected(world, target))) {
                sp.sendMessage(Text.literal("Zone protégée : impossible de modifier les blocs ici.").formatted(Formatting.RED), true);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        // Seaux vidés en visant l'air, etc.
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack held = player.getStackInHand(hand);
            if (world.isClient || !(player instanceof ServerPlayerEntity sp) || HarvestRules.isExempt(sp)) return TypedActionResult.pass(held);
            if (held.getItem() instanceof BucketItem && RpgState.get(sp.getServer()).nearProtected(world, sp.getBlockPos(), 6)) {
                sp.sendMessage(Text.literal("Zone protégée : pas de seau ici.").formatted(Formatting.RED), true);
                return TypedActionResult.fail(held);
            }
            return TypedActionResult.pass(held);
        });
    }

    private static boolean isWorldEditingItem(Item item) {
        return item instanceof BlockItem || item instanceof BucketItem || item instanceof FlintAndSteelItem
                || item instanceof FireChargeItem || item instanceof MiningToolItem || item instanceof BoneMealItem
                || item instanceof SpawnEggItem;
    }

    /** Propriétaire du coffre, en regardant aussi l'autre moitié d'un coffre double. */
    public static UUID chestOwnerIncludingOtherHalf(RpgState rs, World world, BlockPos pos, BlockState state) {
        UUID owner = rs.chestOwner(world, pos);
        if (owner != null) return owner;
        if (ChestBlock.getDoubleBlockType(state) != DoubleBlockProperties.Type.SINGLE) {
            return rs.chestOwner(world, pos.offset(ChestBlock.getFacing(state)));
        }
        return null;
    }

    /** Vrai si poser un coffre ici le collerait au coffre d'un autre joueur. */
    public static boolean touchesForeignChest(RpgState rs, World world, BlockPos pos, UUID placer) {
        for (Direction d : Direction.Type.HORIZONTAL) {
            UUID owner = rs.chestOwner(world, pos.offset(d));
            if (owner != null && !owner.equals(placer)) return true;
        }
        return false;
    }

    public static String nameOf(RpgState rs, UUID id) {
        var d = rs.players.get(id);
        return d == null ? "un autre joueur" : d.name;
    }

    /** Le bloc est-il intouchable par les mécanismes (explosions, pistons, feu) ? */
    public static boolean isShielded(World world, BlockPos pos) {
        if (!(world instanceof ServerWorld sw)) return false;
        RpgState rs = RpgState.get(sw.getServer());
        return rs.isProtected(world, pos) || rs.chestOwner(world, pos) != null;
    }

    public static boolean isEntityInProtectedZone(Entity e) {
        return e.getWorld() instanceof ServerWorld sw && RpgState.get(sw.getServer()).isProtected(e.getWorld(), e.getBlockPos());
    }
}
