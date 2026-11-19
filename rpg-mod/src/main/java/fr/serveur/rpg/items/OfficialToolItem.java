package fr.serveur.rpg.items;

import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.jobs.Job;
import fr.serveur.rpg.tools.OfficialTools;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.List;
import java.util.UUID;

/**
 * Comportement commun des outils officiels (pioche, hache, houe, épée).
 * Un outil sans identifiant (sorti du menu créatif ou d'un /give du GM) reçoit son identifiant
 * unique dès qu'il se trouve dans un inventaire de joueur : seul le GM peut en obtenir.
 */
public interface OfficialToolItem {
    Job job();

    static void tick(ItemStack stack, World world, Entity holder, Job job) {
        if (world.isClient || holder.getServer() == null || OfficialTools.idOf(stack) != null) return;
        OfficialTools.register(stack, job, RpgState.get(holder.getServer()));
    }

    static void tooltip(ItemStack stack, Job job, List<Text> tooltip) {
        tooltip.add(Text.translatable("tooltip.survierpg.official", Text.translatable("job.survierpg." + job.id()))
                .formatted(job.color));
        UUID id = OfficialTools.idOf(stack);
        tooltip.add(id == null
                ? Text.translatable("tooltip.survierpg.unregistered").formatted(Formatting.DARK_GRAY)
                : Text.translatable("tooltip.survierpg.id", id.toString().substring(0, 8)).formatted(Formatting.DARK_GRAY));
    }
}
