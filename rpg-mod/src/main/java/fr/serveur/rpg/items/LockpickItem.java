package fr.serveur.rpg.items;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Crochet : l'utilisation sur un coffre est gérée par fr.serveur.rpg.protection.Protection. */
public class LockpickItem extends Item {
    public LockpickItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("tooltip.survierpg.crochet.1").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.survierpg.crochet.2").formatted(Formatting.GRAY));
    }
}
