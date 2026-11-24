package fr.serveur.rpg.raid;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.serveur.rpg.SurvieRpg;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
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
 * Réglages de la zone Nether de raid : config/survierpg/raid.json (règles) et config/survierpg/loot.json (butin).
 * Les salles sont dans config/survierpg/raid/salles/{exterieur,intermediaire,coeur}/*.schem.
 * Après une modification : /gm recharger (la prochaine génération utilise les nouvelles valeurs).
 */
public final class RaidConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("survierpg");

    /** Un niveau de menace (choisi au hasard à chaque cycle, selon son poids). */
    public static final class Threat {
        public String nom;
        public int poids, fraisEntree, statues;
        public double vieBoss, bonusExtraction, monstres;

        public Threat() {}

        Threat(String nom, int poids, int fraisEntree, int statues, double vieBoss, double bonusExtraction, double monstres) {
            this.nom = nom;
            this.poids = poids;
            this.fraisEntree = fraisEntree;
            this.statues = statues;
            this.vieBoss = vieBoss;
            this.bonusExtraction = bonusExtraction;
            this.monstres = monstres;
        }
    }

    public static final class Data {
        /** La zone est régénérée toutes les N heures, avec une annonce M minutes avant. */
        public double cycleHeures = 3;
        public int annonceMinutes = 5;
        public List<Threat> niveaux = new ArrayList<>();

        /** Taille de la zone (blocs, carré centré sur 0 0), taille d'une cellule, hauteur du sol. */
        public int tailleZone = 400, cellule = 16, hauteurSol = 64;
        /** Anneaux, en cellules depuis le centre : cœur jusqu'à R1, intermédiaire jusqu'à R2, extérieur au-delà. */
        public int rayonCoeur = 2, rayonIntermediaire = 6;
        /** Nombre de salles d'arrivée (anneau extérieur), d'arènes (intermédiaire) et d'arènes du cœur. */
        public int sallesArrivee = 4, arenesIntermediaires = 2, arenesCoeur = 1;
        /** Voleurs placés au plus par génération (aux marqueurs [voleur], tirés au sort). */
        public int voleursMax = 5;
        /** Probabilité d'ajouter un passage en plus de ceux qui garantissent un chemin (boucles dans le labyrinthe). */
        public double passagesEnPlus = 0.15;
        /** Bloc qui bouche une porte sans issue. */
        public String blocScellement = "minecraft:polished_blackstone_bricks";
        /** Blocs posés par tick pendant la génération (plus bas = moins de ralentissement, génération plus longue). */
        public int blocsParTick = 12000;

        public double coffrePiegeChance = 0.05;
        public int coffreCorpsMinutes = 10;
        public int baliseCanalisationSecondes = 10;
        /** Extraction finale : clés nécessaires, ouverture du portail (s), durée d'ouverture (s), pourcentage de la valeur du butin versé. */
        public int clesRequises = 2, extractionOuvertureSecondes = 45, extractionOuverteSecondes = 60;
        public double extractionBonusPourcentValeur = 20;
        /** Mini-boss : PV de base, pourcentage de PV en plus par joueur proche (au-delà du premier), délai entre deux attaques spéciales (s). */
        public double bossVieBase = 120, bossVieParJoueur = 50;
        public int bossAttaqueSecondes = 9;
        /** Réparation du Brise-statue (pièces), frais par usage manquant. */
        public int reparationBriseStatue = 25;
    }

    /** Une entrée de table de butin. */
    public static final class LootEntry {
        public String objet;
        public int poids = 1, min = 1, max = 1;
        /** Enchantement aléatoire (niveau d'enchantement 10 à 30). */
        public boolean enchante;

        public LootEntry() {}

        LootEntry(String objet, int poids, int min, int max, boolean enchante) {
            this.objet = objet;
            this.poids = poids;
            this.min = min;
            this.max = max;
            this.enchante = enchante;
        }
    }

    public static final class LootData {
        /** Nombre d'objets tirés par coffre, par palier (min, max). */
        public Map<String, int[]> tirages = new LinkedHashMap<>();
        public Map<String, List<LootEntry>> paliers = new LinkedHashMap<>();
        /** Valeur (pièces) d'un objet pour le bonus d'extraction, quand il n'est pas dans la boutique. */
        public Map<String, Integer> valeurs = new LinkedHashMap<>();
    }

    private static Data data = defaults();
    private static LootData loot = defaultLoot();

    private RaidConfig() {}

    public static Data get() {
        return data;
    }

    public static LootData loot() {
        return loot;
    }

    public static Path roomsDir() {
        return DIR.resolve("raid").resolve("salles");
    }

    public static void load() {
        data = read(DIR.resolve("raid.json"), Data.class, defaults());
        loot = read(DIR.resolve("loot.json"), LootData.class, defaultLoot());
        if (data.niveaux.isEmpty()) data.niveaux = defaults().niveaux;
    }

    private static <T> T read(Path file, Class<T> type, T fallback) {
        T value = fallback;
        try {
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    T loaded = GSON.fromJson(r, type);
                    if (loaded != null) value = loaded;
                }
            } else {
                Files.createDirectories(file.getParent());
                try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                    GSON.toJson(fallback, w);
                }
            }
        } catch (Exception e) {
            SurvieRpg.LOGGER.error("Impossible de lire {} : valeurs par défaut", file, e);
        }
        return value;
    }

    private static Data defaults() {
        Data d = new Data();
        d.niveaux.add(new Threat("Normal", 5, 20, 4, 1.0, 1.0, 1.0));
        d.niveaux.add(new Threat("Difficile", 3, 50, 8, 1.5, 1.5, 1.4));
        d.niveaux.add(new Threat("Cauchemar", 1, 120, 14, 2.2, 2.5, 1.8));
        return d;
    }

    private static LootData defaultLoot() {
        LootData l = new LootData();
        l.tirages.put("1", new int[]{3, 5});
        l.tirages.put("2", new int[]{4, 6});
        l.tirages.put("3", new int[]{6, 9});
        l.paliers.put("1", new ArrayList<>(List.of(
                new LootEntry("minecraft:gold_ingot", 10, 1, 4, false), new LootEntry("minecraft:iron_ingot", 10, 2, 6, false),
                new LootEntry("minecraft:quartz", 8, 4, 10, false), new LootEntry("minecraft:coal", 8, 4, 12, false),
                new LootEntry("minecraft:arrow", 6, 8, 16, false), new LootEntry("minecraft:golden_carrot", 5, 2, 5, false),
                new LootEntry("survierpg:relique_ardente", 9, 1, 2, false), new LootEntry("survierpg:piece_or", 6, 2, 6, false),
                new LootEntry("survierpg:balise_retour", 2, 1, 1, false), new LootEntry("minecraft:iron_sword", 3, 1, 1, true))));
        l.paliers.put("2", new ArrayList<>(List.of(
                new LootEntry("minecraft:diamond", 6, 1, 2, false), new LootEntry("minecraft:blaze_rod", 5, 2, 4, false),
                new LootEntry("minecraft:book", 5, 1, 1, true), new LootEntry("minecraft:diamond_sword", 3, 1, 1, true),
                new LootEntry("survierpg:idole_basalte", 8, 1, 1, false), new LootEntry("survierpg:relique_ardente", 6, 1, 3, false),
                new LootEntry("survierpg:fragment_tenebres", 7, 1, 2, false), new LootEntry("survierpg:piece_or", 8, 5, 12, false),
                new LootEntry("minecraft:netherite_scrap", 2, 1, 1, false), new LootEntry("survierpg:balise_retour", 4, 1, 1, false))));
        l.paliers.put("3", new ArrayList<>(List.of(
                new LootEntry("survierpg:couronne_dechue", 5, 1, 1, false), new LootEntry("survierpg:idole_basalte", 6, 1, 2, false),
                new LootEntry("minecraft:netherite_scrap", 5, 1, 2, false), new LootEntry("minecraft:diamond", 8, 2, 5, false),
                new LootEntry("minecraft:diamond_chestplate", 3, 1, 1, true), new LootEntry("minecraft:book", 6, 1, 1, true),
                new LootEntry("survierpg:fragment_tenebres", 6, 2, 4, false), new LootEntry("survierpg:piece_or", 8, 15, 30, false),
                new LootEntry("minecraft:totem_of_undying", 2, 1, 1, false), new LootEntry("survierpg:balise_retour", 4, 1, 2, false))));
        l.valeurs.put("minecraft:diamond", 15);
        l.valeurs.put("minecraft:netherite_scrap", 60);
        l.valeurs.put("minecraft:gold_ingot", 3);
        l.valeurs.put("minecraft:iron_ingot", 2);
        l.valeurs.put("minecraft:blaze_rod", 4);
        l.valeurs.put("minecraft:totem_of_undying", 120);
        l.valeurs.put("minecraft:enchanted_book", 20);
        l.valeurs.put("survierpg:fragment_tenebres", 15);
        l.valeurs.put("survierpg:piece_or", 1);
        return l;
    }
}
