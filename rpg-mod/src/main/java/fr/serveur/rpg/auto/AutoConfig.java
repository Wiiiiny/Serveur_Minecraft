package fr.serveur.rpg.auto;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.serveur.rpg.SurvieRpg;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Réglages des automatisations (forge, quêtes du jour, événements, changement de métier).
 * Fichier : config/survierpg/automatisation.json — modifiable aussi depuis l'onglet GM (touche B).
 */
public final class AutoConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("survierpg").resolve("automatisation.json");

    public static final class ForgeTier {
        public int niveau;
        public int pieces;
        /** Matériau de la tête de l'outil (objet ou #tag). Vide = gratuit (outil en bois). */
        public String matiere = "";
        /** Objets supplémentaires (ex. lingot de Netherite). */
        public Map<String, Integer> extra = new LinkedHashMap<>();

        ForgeTier() {}

        ForgeTier(int niveau, int pieces, String matiere) {
            this.niveau = niveau;
            this.pieces = pieces;
            this.matiere = matiere;
        }
    }

    public static final class Data {
        public int prixChangementMetier = 50;
        public int quetesParJour = 3;
        public double multiplicateurRecompenses = 1.0;
        public boolean evenementsAuto = true;
        public int evenementIntervalleMinutes = 120;
        public int evenementDureeMinutes = 15;
        public int evenementJoueursMin = 2;
        public List<Integer> evenementRecompenses = new ArrayList<>(List.of(100, 50, 25));
        public Map<String, ForgeTier> forge = defaultForge();
    }

    private static Map<String, ForgeTier> defaultForge() {
        Map<String, ForgeTier> m = new LinkedHashMap<>();
        m.put("bois", new ForgeTier(1, 0, ""));
        m.put("pierre", new ForgeTier(3, 5, "#minecraft:stone_tool_materials"));
        m.put("fer", new ForgeTier(8, 20, "minecraft:iron_ingot"));
        m.put("diamant", new ForgeTier(15, 60, "minecraft:diamond"));
        ForgeTier netherite = new ForgeTier(22, 150, "minecraft:diamond");
        netherite.extra.put("minecraft:netherite_ingot", 1);
        m.put("netherite", netherite);
        return m;
    }

    private static Data data = new Data();

    private AutoConfig() {}

    public static Data get() {
        return data;
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                    Data d = GSON.fromJson(r, Data.class);
                    if (d != null) {
                        if (d.forge == null || d.forge.isEmpty()) d.forge = defaultForge();
                        if (d.evenementRecompenses == null) d.evenementRecompenses = new ArrayList<>(List.of(100, 50, 25));
                        data = d;
                    }
                }
            }
            save();
        } catch (Exception e) {
            SurvieRpg.LOGGER.error("Impossible de lire {}", FILE, e);
        }
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
}
