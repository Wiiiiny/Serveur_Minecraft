package fr.serveur.rpg.craft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.serveur.rpg.SurvieRpg;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Crafts contrôlés par le GM : les recettes vanilla des outils et des armes ne donnent rien.
 * Le joueur apporte les matériaux au GM, qui fabrique l'objet officiel (/gm outil donner...).
 * Fichier : config/survierpg/crafts.json (entrées « minecraft:bow » ou « #minecraft:swords »).
 */
public final class CraftRules {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("survierpg").resolve("crafts.json");

    public static final class Data {
        public List<String> bloques = new ArrayList<>(List.of(
                "#minecraft:pickaxes", "#minecraft:axes", "#minecraft:hoes", "#minecraft:shovels", "#minecraft:swords",
                "minecraft:bow", "minecraft:crossbow", "minecraft:shield"));
    }

    private static Data data = new Data();
    private static Set<Item> blocked = Set.of();

    private CraftRules() {}

    public static Data data() {
        return data;
    }

    public static boolean isBlocked(ItemStack result) {
        return !result.isEmpty() && blocked.contains(result.getItem());
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                    Data d = GSON.fromJson(r, Data.class);
                    if (d != null) data = d;
                }
            } else {
                save();
            }
        } catch (Exception e) {
            SurvieRpg.LOGGER.error("Impossible de lire {}", FILE, e);
        }
        resolve();
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(data, w);
            }
        } catch (Exception e) {
            SurvieRpg.LOGGER.error("Impossible d'écrire {}", FILE, e);
        }
    }

    public static void resolve() {
        Set<Item> set = new HashSet<>();
        for (String entry : data.bloques) {
            try {
                if (entry.startsWith("#")) {
                    TagKey<Item> tag = TagKey.of(RegistryKeys.ITEM, new Identifier(entry.substring(1)));
                    for (RegistryEntry<Item> e : Registries.ITEM.iterateEntries(tag)) set.add(e.value());
                } else {
                    Identifier id = new Identifier(entry);
                    if (Registries.ITEM.containsId(id)) set.add(Registries.ITEM.get(id));
                }
            } catch (Exception ignored) {
            }
        }
        blocked = set;
        SurvieRpg.LOGGER.info("Crafts bloqués : {} objets", set.size());
    }

    public static boolean isValidEntry(String entry) {
        try {
            if (entry.startsWith("#")) {
                return Registries.ITEM.iterateEntries(TagKey.of(RegistryKeys.ITEM, new Identifier(entry.substring(1)))).iterator().hasNext();
            }
            return Registries.ITEM.containsId(new Identifier(entry));
        } catch (Exception e) {
            return false;
        }
    }
}
