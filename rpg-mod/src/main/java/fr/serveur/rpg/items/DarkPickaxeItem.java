package fr.serveur.rpg.items;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Pioche des ténèbres : incassable, rapide sur la Pierre obscure (sable des âmes)
 * et inutile sur tout le reste (les règles de récolte refusent les autres blocs).
 */
public class DarkPickaxeItem extends PickaxeItem {
    public DarkPickaxeItem(ToolMaterial material, Settings settings) {
        super(material, 1, -2.8f, settings);
    }

    @Override
    public float getMiningSpeedMultiplier(ItemStack stack, BlockState state) {
        return state.isOf(Blocks.SOUL_SAND) ? 12.0f : 1.0f;
    }

    @Override
    public boolean isSuitableFor(BlockState state) {
        return state.isOf(Blocks.SOUL_SAND);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("tooltip.survierpg.pioche.1").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.survierpg.pioche.2").formatted(Formatting.DARK_PURPLE));
    }
}
