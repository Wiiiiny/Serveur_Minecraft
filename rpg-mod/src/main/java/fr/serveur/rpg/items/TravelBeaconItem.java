package fr.serveur.rpg.items;

import fr.serveur.rpg.world.TravelBeacon;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Balise du Voyageur : clic droit pour franchir la limite de la zone jouable (voir {@link TravelBeacon}). */
public class TravelBeaconItem extends Item {
    public TravelBeaconItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(stack, true);
        return user instanceof ServerPlayerEntity sp && TravelBeacon.use(sp) ? TypedActionResult.success(stack) : TypedActionResult.fail(stack);
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("tooltip.survierpg.balise.1").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.survierpg.balise.2").formatted(Formatting.DARK_AQUA));
    }
}
