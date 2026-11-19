package fr.serveur.rpg.jobs;

import net.minecraft.item.AxeItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.SwordItem;
import net.minecraft.util.Formatting;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/** Les quatre métiers de base. Ajouter un métier = ajouter une ligne ici + ses paliers dans la config. */
public enum Job {
    MINEUR("Mineur", "Pioche", Formatting.GRAY, i -> i instanceof PickaxeItem),
    BUCHERON("Bûcheron", "Hache", Formatting.GOLD, i -> i instanceof AxeItem),
    FERMIER("Fermier", "Houe", Formatting.GREEN, i -> i instanceof HoeItem),
    AVENTURIER("Aventurier", "Épée", Formatting.RED, i -> i instanceof SwordItem);

    public static final int MAX_LEVEL = 25;

    public final String displayName;
    public final String toolName;
    public final Formatting color;
    private final Predicate<Item> toolType;

    Job(String displayName, String toolName, Formatting color, Predicate<Item> toolType) {
        this.displayName = displayName;
        this.toolName = toolName;
        this.color = color;
        this.toolType = toolType;
    }

    public boolean isToolType(Item item) {
        return toolType.test(item);
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Accepte « mineur », « Bûcheron », « bucheron »... */
    public static Job parse(String s) {
        if (s == null) return null;
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
        for (Job j : values()) if (j.name().equals(n)) return j;
        return null;
    }

    public static Job forTool(Item item) {
        for (Job j : values()) if (j.isToolType(item)) return j;
        return null;
    }

    public static List<String> ids() {
        return Arrays.stream(values()).map(Job::id).toList();
    }
}
