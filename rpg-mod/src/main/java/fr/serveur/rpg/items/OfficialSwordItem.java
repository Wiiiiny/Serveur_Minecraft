package fr.serveur.rpg.items;

import fr.serveur.rpg.jobs.Job;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolMaterial;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Outil officiel (SwordItem) d'un métier. */
public class OfficialSwordItem extends SwordItem implements OfficialToolItem {
    private final Job job;

    public OfficialSwordItem(ToolMaterial material, Job job, Settings settings) {
        super(material, 3, -2.4f, settings);
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
