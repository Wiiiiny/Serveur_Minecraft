package fr.serveur.rpg.tools;

import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.jobs.Job;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

/**
 * Outils officiels : des outils vanilla marqués d'un identifiant unique enregistré côté serveur.
 * Un outil fabriqué avec une recette vanilla n'a pas cet identifiant et ne permet pas de récolter.
 */
public final class OfficialTools {
    public static final String NBT_KEY = "SurvieRPG";

    private OfficialTools() {}

    /**
     * Crée un outil officiel. Les outils du mod (survierpg:outil_...) portent déjà leur nom et leur
     * texture ; un outil vanilla (ex. en or) reçoit un nom et une description.
     */
    public static ItemStack create(Item item, Job job, RpgState state) {
        ItemStack stack = new ItemStack(item);
        UUID id = register(stack, job, state);
        if (!(item instanceof fr.serveur.rpg.items.OfficialToolItem)) {
            stack.setCustomName(Text.literal(job.toolName + " officielle — " + job.displayName)
                    .styled(s -> s.withItalic(false).withColor(job.color)));
            setLore(stack,
                    Text.literal("Outil officiel du métier " + job.displayName).formatted(Formatting.GRAY),
                    Text.literal("ID " + id.toString().substring(0, 8)).formatted(Formatting.DARK_GRAY));
        }
        return stack;
    }

    /** Donne un identifiant unique à l'outil et l'enregistre comme officiel. */
    public static UUID register(ItemStack stack, Job job, RpgState state) {
        UUID id = UUID.randomUUID();
        NbtCompound tag = stack.getOrCreateSubNbt(NBT_KEY);
        tag.putString("type", "outil");
        tag.putUuid("id", id);
        tag.putString("job", job.name());
        state.officialTools.add(id);
        state.markDirty();
        return id;
    }

    public static void setLore(ItemStack stack, Text... lines) {
        NbtCompound display = stack.getOrCreateSubNbt("display");
        NbtList lore = new NbtList();
        for (Text line : lines) {
            lore.add(NbtString.of(Text.Serializer.toJson(Text.empty().append(line).styled(s -> s.withItalic(false)))));
        }
        display.put("Lore", lore);
    }

    public static UUID idOf(ItemStack stack) {
        NbtCompound tag = stack.getSubNbt(NBT_KEY);
        if (tag == null || !"outil".equals(tag.getString("type")) || !tag.containsUuid("id")) return null;
        return tag.getUuid("id");
    }

    /** Métier de l'outil s'il est officiel et toujours valide, sinon null. */
    public static Job jobOf(ItemStack stack, RpgState state) {
        UUID id = idOf(stack);
        if (id == null || !state.officialTools.contains(id)) return null;
        return Job.parse(stack.getSubNbt(NBT_KEY).getString("job"));
    }
}
