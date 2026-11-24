package fr.serveur.rpg.jobs;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Formatting;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Les métiers supplémentaires : chaque joueur en choisit un, en plus de son métier principal ({@link Job}).
 * Chacun a ses propres niveaux (1 à {@link Job#MAX_LEVEL}) ; les règles sont dans {@link SideJobRules}
 * et les valeurs dans config/survierpg/metiers2.json ({@link SideJobConfig}).
 */
public enum SideJob {
    INGENIEUR("Ingénieur", Formatting.DARK_RED, "Redstone et pistons", "Seul à poser la redstone et les pistons."),
    CONSTRUCTEUR("Constructeur", Formatting.YELLOW, "Blocs de prestige", "Seul à poser les blocs de prestige. XP en construisant."),
    ENCHANTEUR("Enchanteur", Formatting.LIGHT_PURPLE, "Seul à enchanter", "Seul à enchanter. XP avec les livres et le troc."),
    VOYAGEUR("Voyageur", Formatting.AQUA, "Franchit la limite", "Sa balise lui fait franchir la limite du monde."),
    PACIFICATEUR("Pacificateur", Formatting.BLUE, "Primes de jour", "Primes sur les monstres tués le jour."),
    ALCHIMISTE("Alchimiste", Formatting.DARK_PURPLE, "Alambic réservé", "Seul à utiliser l'alambic."),
    PECHEUR("Pêcheur", Formatting.DARK_AQUA, "Pêche et chasse", "XP en pêchant et en chassant les animaux.");

    public final String displayName;
    public final Formatting color;
    /** Quelques mots pour les cartes du menu B, et un résumé d'une ligne (/metier2). */
    public final String tagline, summary;

    SideJob(String displayName, Formatting color, String tagline, String summary) {
        this.displayName = displayName;
        this.color = color;
        this.tagline = tagline;
        this.summary = summary;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Icône du métier dans le menu. */
    public Item icon() {
        return switch (this) {
            case INGENIEUR -> Items.REDSTONE;
            case CONSTRUCTEUR -> Items.BRICKS;
            case ENCHANTEUR -> Items.ENCHANTED_BOOK;
            case VOYAGEUR -> Items.COMPASS;
            case PACIFICATEUR -> Items.SHIELD;
            case ALCHIMISTE -> Items.BREWING_STAND;
            case PECHEUR -> Items.FISHING_ROD;
        };
    }

    /** Accepte « ingenieur », « Ingénieur », « pêcheur »... */
    public static SideJob parse(String s) {
        if (s == null) return null;
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
        for (SideJob j : values()) if (j.name().equals(n)) return j;
        return null;
    }

    public static List<String> ids() {
        return Arrays.stream(values()).map(SideJob::id).toList();
    }
}
