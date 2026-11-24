package fr.serveur.rpg.sombre;

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
 * Réglages du monde sombre :
 * - config/survierpg/monde_sombre.json : Éclat de Lumière, niveau du personnage, Obscurcie, coffres cachés, boutique de découverte ;
 * - config/survierpg/objets_rares.json : rareté de chaque objet et liste des objets exotiques.
 * Après une modification à la main : /gm recharger.
 */
public final class SombreConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("survierpg");
    private static final Path FILE = DIR.resolve("monde_sombre.json");
    private static final Path OBJETS = DIR.resolve("objets_rares.json");

    public static final class Lumiere {
        /** Délai entre l'activation d'un Éclat et le lever du jour (annonce et compte à rebours). */
        public int preparationSecondes = 60;
        /** Annonce « le jour s'achève » ce nombre de secondes avant la fin. */
        public int avertissementFinSecondes = 60;
        // La durée du jour invoqué et la nuit minimale entre deux Éclats sont les réglages « Durée du jour / de la nuit »
        // du tableau de bord GM (onglet Monde).
    }

    public static final class Personnage {
        public int niveauMax = 100;
        /** XP pour passer du niveau n au niveau n+1 : xpBase + xpParNiveau × (n − 1). */
        public int xpBase = 100, xpParNiveau = 50;
        /** Mort : part de l'XP du niveau en cours perdue (jamais de niveau perdu). */
        public double pertePourcentMort = 10;
        /** Pièces versées à chaque niveau : piecesParNiveau × niveau atteint. */
        public int piecesParNiveau = 10;
        /** Part de l'XP de métier (principal et supplémentaire) reversée au personnage. */
        public double partXpMetiers = 0.10;
        public int xpMonstre = 3, xpBoss = 25, xpQuete = 20, xpCoffre = 30, xpDecouverte = 2, xpNiveauMetier = 15;
    }

    public static final class Obscurcie {
        /** XP pour passer du niveau n au niveau n+1 : xpBase + xpParNiveau × (n − 1). Pas de niveau maximum. */
        public int xpBase = 80, xpParNiveau = 30;
        /** Tous les {@code palierEclat} niveaux : une chance sur deux (par défaut) de recevoir un Éclat de Lumière. */
        public int palierEclat = 5;
        public double chanceEclat = 0.5;
        /** Tous les {@code palierDetecteur} niveaux : un détecteur de coffres (dure {@code detecteurSecondes} une fois activé). */
        public int palierDetecteur = 10;
        public int detecteurSecondes = 120, detecteurPortee = 800;
        /** Part de l'XP de métier reversée à l'Obscurcie. */
        public double partXpMetiers = 0.10;
        public int xpMonstreNuit = 5, xpBossNuit = 30, xpQuete = 15, xpCoffre = 25, xpEclat = 20, xpDetecteur = 5;
        /** Exploration nocturne : xpExploration tous les explorationBlocs parcourus, plafonné par jour. */
        public int xpExploration = 2, explorationBlocs = 64, explorationPlafondJour = 150;
        /** Collecte de ressources : XP par bloc cassé. */
        public Map<String, Integer> xpRessources = new LinkedHashMap<>();
    }

    public static final class Lot {
        public String objet = "minecraft:air";
        public int poids = 1, min = 1, max = 1;
        /** > 0 : enchantement aléatoire de ce niveau (livres, outils, armures). */
        public int niveauEnchantement;

        public Lot() {}

        Lot(String objet, int poids, int min, int max, int niveauEnchantement) {
            this.objet = objet;
            this.poids = poids;
            this.min = min;
            this.max = max;
            this.niveauEnchantement = niveauEnchantement;
        }
    }

    public static final class Coffres {
        /** Nombre de coffres cachés visés : base + parJoueur × joueurs connectés, au plus maxActifs. Ils restent jusqu'à leur ouverture. */
        public int baseActifs = 6, parJoueur = 3, maxActifs = 40;
        /** Apparition autour d'un joueur, entre ces deux distances (blocs), dans les chunks déjà chargés. */
        public int distanceMin = 64, distanceMax = 320;
        public int intervalleSecondes = 45;
        /** Membres du groupe (/raid groupe) à moins de ce rayon : ils ouvrent ensemble, un seul remporte le butin. */
        public int rayonGroupe = 12;
        /** Niveau de la zone : +1 tous les N blocs depuis le point d'apparition du monde, jusqu'au maximum. */
        public int niveauZoneTousLesBlocs = 600, niveauZoneMax = 5;
        public Map<String, Integer> poids = new LinkedHashMap<>();
        public Map<String, Double> chancePiege = new LinkedHashMap<>();
        /** Nombre d'objets tirés (× (1 + 0,25 × (niveau de zone − 1))). */
        public Map<String, Integer> tirages = new LinkedHashMap<>();
        /** Pièces d'or versées au gagnant (× niveau de zone). */
        public Map<String, Integer> pieces = new LinkedHashMap<>();
        public Map<String, Double> chanceEclat = new LinkedHashMap<>();
        public Map<String, List<Lot>> butin = new LinkedHashMap<>();
        /** Gardien du coffre de boss : vie de base + vie par joueur présent. */
        public int bossVie = 80, bossVieParJoueur = 30;
    }

    public static final class Decouverte {
        /** Objets toujours visibles et achetables à la boutique, même jamais ramassés. */
        public List<String> toujoursConnus = new ArrayList<>();
    }

    public static final class Data {
        public Lumiere lumiere = new Lumiere();
        public Personnage personnage = new Personnage();
        public Obscurcie obscurcie = new Obscurcie();
        public Coffres coffres = new Coffres();
        public Decouverte decouverte = new Decouverte();
    }

    public static final class Effet {
        public String effet = "minecraft:night_vision";
        public int niveau;

        public Effet() {}

        Effet(String effet, int niveau) {
            this.effet = effet;
            this.niveau = niveau;
        }
    }

    public static final class Exotique {
        public String id = "", nom = "", objet = "minecraft:stick";
        public List<String> lore = new ArrayList<>();
        public Map<String, Integer> enchantements = new LinkedHashMap<>();
        /** Valeur de l'objet, en Pièces d'or (affichée ; les exotiques ne s'achètent jamais à la boutique). */
        public long prix;
        /** Où le porter pour profiter des effets : main, secondaire, armure, inventaire (ou aucun). */
        public String emplacement = "aucun";
        public List<Effet> effets = new ArrayList<>();
    }

    public static final class Objets {
        /** Rareté de chaque objet (PEU_COMMUN gris, COMMUN vert, RARE bleu, EPIQUE violet). Les autres objets n'ont pas de contour. */
        public Map<String, String> raretes = new LinkedHashMap<>();
        /** Un exotique perdu (détruit, disparu) peut revenir dans un coffre exotique après ce délai. */
        public double reintroductionHeures = 12;
        public List<Exotique> exotiques = new ArrayList<>();
    }

    private static Data data = defaults();
    private static Objets objets = defaultObjets();

    private SombreConfig() {}

    public static Data get() {
        return data;
    }

    public static Objets objets() {
        return objets;
    }

    public static void load() {
        data = read(FILE, Data.class, SombreConfig::defaults);
        Data d = defaults();
        // Nouvelles clés ajoutées par une mise à jour : on complète sans écraser les réglages existants.
        d.obscurcie.xpRessources.forEach(data.obscurcie.xpRessources::putIfAbsent);
        d.coffres.poids.forEach(data.coffres.poids::putIfAbsent);
        d.coffres.chancePiege.forEach(data.coffres.chancePiege::putIfAbsent);
        d.coffres.tirages.forEach(data.coffres.tirages::putIfAbsent);
        d.coffres.pieces.forEach(data.coffres.pieces::putIfAbsent);
        d.coffres.chanceEclat.forEach(data.coffres.chanceEclat::putIfAbsent);
        d.coffres.butin.forEach(data.coffres.butin::putIfAbsent);
        write(FILE, data);
        objets = read(OBJETS, Objets.class, SombreConfig::defaultObjets);
        write(OBJETS, objets);
    }

    private static <T> T read(Path file, Class<T> type, java.util.function.Supplier<T> fallback) {
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                T t = GSON.fromJson(r, type);
                if (t != null) return t;
            } catch (Exception e) {
                SurvieRpg.LOGGER.error("{} illisible, valeurs par défaut utilisées", file.getFileName(), e);
                return fallback.get();
            }
        }
        return fallback.get();
    }

    private static void write(Path file, Object o) {
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(o, w);
            }
        } catch (IOException e) {
            SurvieRpg.LOGGER.error("Écriture de {} impossible", file.getFileName(), e);
        }
    }

    // ------------------------------------------------------------- valeurs par défaut

    private static Data defaults() {
        Data d = new Data();
        Map<String, Integer> r = d.obscurcie.xpRessources;
        r.put("survierpg:pierre_obscure", 3);
        for (String ore : List.of("coal", "copper", "iron", "gold", "redstone", "lapis")) {
            int xp = ore.equals("coal") || ore.equals("copper") ? 1 : 2;
            r.put("minecraft:" + ore + "_ore", xp);
            r.put("minecraft:deepslate_" + ore + "_ore", xp);
        }
        for (String ore : List.of("diamond", "emerald")) {
            r.put("minecraft:" + ore + "_ore", 5);
            r.put("minecraft:deepslate_" + ore + "_ore", 5);
        }
        r.put("minecraft:nether_quartz_ore", 1);
        r.put("minecraft:nether_gold_ore", 1);
        r.put("minecraft:ancient_debris", 8);
        r.put("minecraft:amethyst_cluster", 2);

        Coffres c = d.coffres;
        put(c.poids, 40, 22, 12, 3, 5, 6, 12);
        c.chancePiege.put("CLASSIQUE", 0.10);
        c.chancePiege.put("ENFOUI", 0.15);
        c.chancePiege.put("MAUDIT", 0.70);
        c.chancePiege.put("EXOTIQUE", 0.40);
        c.chancePiege.put("LEGENDAIRE", 0.30);
        c.chancePiege.put("BOSS", 0.0);
        c.chancePiege.put("MAGIQUE", 0.25);
        put(c.tirages, 3, 4, 4, 2, 6, 6, 4);
        put(c.pieces, 15, 25, 30, 60, 120, 90, 35);
        c.chanceEclat.put("LEGENDAIRE", 0.10);
        c.chanceEclat.put("BOSS", 0.15);
        c.chanceEclat.put("MAGIQUE", 0.04);

        c.butin.put("CLASSIQUE", List.of(
                new Lot("minecraft:bread", 10, 2, 6, 0), new Lot("minecraft:torch", 10, 4, 12, 0), new Lot("minecraft:iron_ingot", 8, 1, 4, 0),
                new Lot("minecraft:gold_ingot", 5, 1, 3, 0), new Lot("minecraft:arrow", 6, 4, 12, 0), new Lot("minecraft:lantern", 4, 1, 1, 0),
                new Lot("survierpg:eclat_obscur", 6, 2, 6, 0), new Lot("minecraft:cooked_beef", 6, 2, 5, 0), new Lot("minecraft:coal", 6, 3, 8, 0)));
        c.butin.put("ENFOUI", List.of(
                new Lot("minecraft:iron_ingot", 8, 2, 6, 0), new Lot("minecraft:gold_ingot", 8, 2, 5, 0), new Lot("minecraft:diamond", 3, 1, 2, 0),
                new Lot("minecraft:emerald", 4, 1, 3, 0), new Lot("minecraft:lapis_lazuli", 5, 3, 8, 0), new Lot("survierpg:eclat_obscur", 6, 3, 8, 0),
                new Lot("minecraft:golden_apple", 2, 1, 1, 0), new Lot("minecraft:name_tag", 1, 1, 1, 0)));
        c.butin.put("MAUDIT", List.of(
                new Lot("minecraft:gold_ingot", 6, 2, 6, 0), new Lot("minecraft:diamond", 4, 1, 2, 0), new Lot("minecraft:wither_skeleton_skull", 1, 1, 1, 0),
                new Lot("minecraft:enchanted_book", 4, 1, 1, 20), new Lot("minecraft:ender_pearl", 5, 1, 3, 0), new Lot("minecraft:bone", 6, 3, 8, 0),
                new Lot("survierpg:fragment_tenebres", 4, 1, 3, 0), new Lot("minecraft:iron_sword", 3, 1, 1, 18)));
        c.butin.put("EXOTIQUE", List.of(
                new Lot("minecraft:gold_ingot", 6, 3, 8, 0), new Lot("minecraft:diamond", 4, 1, 3, 0), new Lot("minecraft:emerald", 4, 2, 5, 0)));
        c.butin.put("LEGENDAIRE", List.of(
                new Lot("minecraft:diamond", 8, 2, 5, 0), new Lot("minecraft:netherite_scrap", 3, 1, 2, 0), new Lot("minecraft:golden_apple", 5, 1, 3, 0),
                new Lot("minecraft:enchanted_golden_apple", 1, 1, 1, 0), new Lot("minecraft:totem_of_undying", 2, 1, 1, 0),
                new Lot("minecraft:enchanted_book", 5, 1, 1, 30), new Lot("minecraft:diamond_sword", 3, 1, 1, 30),
                new Lot("minecraft:diamond_chestplate", 2, 1, 1, 30), new Lot("survierpg:larme_de_vie", 3, 1, 1, 0)));
        c.butin.put("BOSS", List.of(
                new Lot("minecraft:diamond", 8, 2, 4, 0), new Lot("minecraft:gold_ingot", 8, 4, 10, 0), new Lot("minecraft:netherite_scrap", 2, 1, 1, 0),
                new Lot("minecraft:golden_apple", 5, 1, 2, 0), new Lot("minecraft:experience_bottle", 6, 4, 10, 0),
                new Lot("minecraft:diamond_axe", 3, 1, 1, 25), new Lot("minecraft:enchanted_book", 4, 1, 1, 25),
                new Lot("survierpg:fragment_tenebres", 5, 2, 4, 0)));
        c.butin.put("MAGIQUE", List.of(
                new Lot("minecraft:enchanted_book", 10, 1, 1, 22), new Lot("minecraft:experience_bottle", 8, 4, 12, 0),
                new Lot("minecraft:lapis_lazuli", 8, 6, 16, 0), new Lot("minecraft:amethyst_shard", 6, 3, 8, 0),
                new Lot("minecraft:glowstone_dust", 6, 4, 10, 0), new Lot("minecraft:ender_pearl", 4, 1, 2, 0),
                new Lot("minecraft:golden_carrot", 5, 3, 8, 0), new Lot("minecraft:bow", 3, 1, 1, 22)));

        d.decouverte.toujoursConnus = new ArrayList<>(List.of("minecraft:lantern", "minecraft:torch", "minecraft:bread",
                "minecraft:white_bed", "minecraft:phantom_membrane", "survierpg:crochet", "survierpg:larme_de_vie",
                "survierpg:balise_retour", "survierpg:brise_statue"));
        return d;
    }

    /** Ordre : CLASSIQUE, ENFOUI, MAUDIT, EXOTIQUE, LEGENDAIRE, BOSS, MAGIQUE. */
    private static void put(Map<String, Integer> m, int... v) {
        String[] keys = {"CLASSIQUE", "ENFOUI", "MAUDIT", "EXOTIQUE", "LEGENDAIRE", "BOSS", "MAGIQUE"};
        for (int i = 0; i < keys.length; i++) m.put(keys[i], v[i]);
    }

    private static Objets defaultObjets() {
        Objets o = new Objets();
        Map<String, String> r = o.raretes;
        for (String s : List.of("iron_ingot", "gold_ingot", "copper_ingot", "lapis_lazuli", "redstone", "quartz", "amethyst_shard",
                "ender_pearl", "blaze_rod", "slime_ball", "glowstone_dust", "iron_sword", "iron_pickaxe", "iron_axe", "iron_helmet",
                "iron_chestplate", "iron_leggings", "iron_boots", "bow", "crossbow", "shield", "lantern", "soul_lantern")) {
            r.put("minecraft:" + s, "PEU_COMMUN");
        }
        r.put("survierpg:eclat_obscur", "PEU_COMMUN");
        r.put("survierpg:relique_ardente", "PEU_COMMUN");
        r.put("survierpg:crochet", "PEU_COMMUN");
        for (String s : List.of("diamond", "emerald", "golden_apple", "name_tag", "saddle", "experience_bottle", "enchanted_book",
                "golden_carrot", "ghast_tear", "wither_skeleton_skull", "diamond_sword", "diamond_pickaxe", "diamond_axe",
                "diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots")) {
            r.put("minecraft:" + s, "COMMUN");
        }
        r.put("survierpg:fragment_tenebres", "COMMUN");
        r.put("survierpg:balise_retour", "COMMUN");
        r.put("survierpg:idole_basalte", "COMMUN");
        r.put("survierpg:larme_de_vie", "COMMUN");
        for (String s : List.of("netherite_scrap", "ancient_debris", "totem_of_undying", "trident", "heart_of_the_sea", "nautilus_shell",
                "shulker_shell", "echo_shard", "netherite_sword", "netherite_pickaxe", "netherite_axe", "netherite_helmet",
                "netherite_chestplate", "netherite_leggings", "netherite_boots")) {
            r.put("minecraft:" + s, "RARE");
        }
        r.put("survierpg:brise_statue", "RARE");
        r.put("survierpg:cle_raid", "RARE");
        r.put("survierpg:balise", "RARE");
        r.put("survierpg:detecteur_ombre", "RARE");
        for (String s : List.of("netherite_ingot", "enchanted_golden_apple", "nether_star", "elytra", "beacon", "dragon_egg", "dragon_head")) {
            r.put("minecraft:" + s, "EPIQUE");
        }
        r.put("survierpg:couronne_dechue", "EPIQUE");
        r.put("survierpg:eclat_lumiere", "EPIQUE");
        r.put("survierpg:pioche_tenebres", "EPIQUE");

        o.exotiques.add(exo("lame_crepuscule", "Lame du Crépuscule", "minecraft:netherite_sword", 6000, "main",
                List.of("Forgée à la dernière heure du dernier jour.", "Elle boit la lumière qu'elle tranche."),
                Map.of("minecraft:sharpness", 6, "minecraft:fire_aspect", 2, "minecraft:looting", 3), List.of()));
        o.exotiques.add(exo("arc_dernier_veilleur", "Arc du Dernier Veilleur", "minecraft:bow", 5000, "main",
                List.of("Le veilleur tira sa dernière flèche vers l'aube.", "Elle n'est jamais retombée."),
                Map.of("minecraft:power", 6, "minecraft:infinity", 1, "minecraft:flame", 1, "minecraft:punch", 2), List.of()));
        o.exotiques.add(exo("couronne_eclipse", "Couronne de l'Éclipse", "minecraft:netherite_helmet", 5500, "armure",
                List.of("Portée par le roi qui éteignit le soleil.", "Elle voit dans le noir pour son porteur."),
                Map.of("minecraft:protection", 5, "minecraft:respiration", 3), List.of(new Effet("minecraft:night_vision", 0))));
        o.exotiques.add(exo("plastron_brume", "Plastron de Brume", "minecraft:netherite_chestplate", 6500, "armure",
                List.of("Tissé dans la brume qui recouvre le monde.", "Les coups s'y perdent comme des pas dans la nuit."),
                Map.of("minecraft:protection", 5, "minecraft:thorns", 3), List.of(new Effet("minecraft:resistance", 0))));
        o.exotiques.add(exo("bottes_marcheur_ombre", "Bottes du Marcheur d'Ombre", "minecraft:netherite_boots", 4500, "armure",
                List.of("Celui qui les chausse ne laisse aucune trace.", "Même les morts ne l'entendent pas venir."),
                Map.of("minecraft:feather_falling", 5, "minecraft:soul_speed", 3, "minecraft:depth_strider", 3), List.of(new Effet("minecraft:speed", 0))));
        o.exotiques.add(exo("trident_maree_noire", "Trident de la Marée noire", "minecraft:trident", 5000, "main",
                List.of("Repêché dans un océan sans étoiles.", "Il revient toujours vers la main qui le craint."),
                Map.of("minecraft:loyalty", 3, "minecraft:impaling", 6, "minecraft:channeling", 1), List.of()));
        o.exotiques.add(exo("egide_aube_perdue", "Égide de l'Aube perdue", "minecraft:shield", 4000, "secondaire",
                List.of("Le dernier rayon du soleil s'y est figé.", "Rien ne fait reculer celui qui la porte."),
                Map.of("minecraft:unbreaking", 3), List.of(new Effet("minecraft:resistance", 0))));
        o.exotiques.add(exo("lanterne_eternelle", "Lanterne éternelle", "minecraft:soul_lantern", 3500, "secondaire",
                List.of("Sa flamme brûle depuis avant la Grande Nuit.", "La brume recule devant elle."),
                Map.of(), List.of(new Effet("minecraft:night_vision", 0))));
        o.exotiques.add(exo("hache_bucher_noir", "Hache du Bûcher noir", "minecraft:netherite_axe", 5000, "main",
                List.of("Elle a abattu le dernier arbre qui voyait le soleil.", "Ses coups résonnent jusque sous la terre."),
                Map.of("minecraft:sharpness", 5, "minecraft:efficiency", 5), List.of(new Effet("minecraft:strength", 0))));
        o.exotiques.add(exo("amulette_sans_lumiere", "Amulette des Sans-Lumière", "minecraft:heart_of_the_sea", 3000, "inventaire",
                List.of("Le cœur d'une étoile tombée dans l'abîme.", "Son porteur respire là où la lumière meurt."),
                Map.of(), List.of(new Effet("minecraft:water_breathing", 0), new Effet("minecraft:regeneration", 0))));
        return o;
    }

    private static Exotique exo(String id, String nom, String objet, long prix, String emplacement, List<String> lore,
                                Map<String, Integer> ench, List<Effet> effets) {
        Exotique e = new Exotique();
        e.id = id;
        e.nom = nom;
        e.objet = objet;
        e.prix = prix;
        e.emplacement = emplacement;
        e.lore = new ArrayList<>(lore);
        e.enchantements = new LinkedHashMap<>(new java.util.TreeMap<>(ench));
        e.effets = new ArrayList<>(effets);
        return e;
    }
}
