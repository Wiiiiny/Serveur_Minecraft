package fr.serveur.rpg.items;

import fr.serveur.rpg.jobs.Job;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolMaterial;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Outil officiel (AxeItem) d'un métier. */
public class OfficialAxeItem extends AxeItem implements OfficialToolItem {
    private final Job job;

    public OfficialAxeItem(ToolMaterial material, float attackDamage, float attackSpeed, Job job, Settings settings) {
        super(material, attackDamage, attackSpeed, settings);
        this.job = job;
    }

    @Override
    public Job job() {
        return job;
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, world, entity, slot, selected);
        OfficialToolItem.tick(stack, world, entity, job);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        OfficialToolItem.tooltip(stack, job, tooltip);
    }
}
