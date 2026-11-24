package fr.serveur.rpg.jobs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.serveur.rpg.SurvieRpg;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.util.DyeColor;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Réglages des métiers supplémentaires : config/survierpg/metiers2.json.
 * Après une modification à la main : /gm recharger (comme metiers.json).
 */
public final class SideJobConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("survierpg").resolve("metiers2.json");

    public static final class Data {
        /** Prix d'un changement de métier supplémentaire (le premier choix est gratuit). */
        public int prixChangement = 50;
        /** XP maximum gagnée par jour en posant des blocs (Ingénieur et Constructeur). */
        public int plafondXpPoseJour = 600;

        /** Ingénieur : blocs de redstone qu'il est seul à pouvoir poser (niveau requis, XP à la pose). */
        public List<JobConfig.Palier> ingenieur = new ArrayList<>();
        /** Constructeur : blocs de prestige qu'il est seul à pouvoir poser (niveau requis, XP à la pose). */
        public List<JobConfig.Palier> constructeur = new ArrayList<>();
        /** Constructeur : XP pour un bloc ordinaire posé. */
        public int constructeurXpBlocOrdinaire = 1;

        /** Enchanteur : niveau requis pour les 2e et 3e lignes de la table d'enchantement. */
        public int enchanteurNiveauLigne2 = 8, enchanteurNiveauLigne3 = 15;
        public int enchanteurXpParNiveauDepense = 4;
        /** XP pour un livre enchanté trouvé dans un coffre (ou pêché). */
        public int enchanteurXpLivreTrouve = 15;
        /** XP pour un échange avec un villageois (le double si on obtient un livre enchanté). */
        public int enchanteurXpTroc = 5;
        /** XP pour un livre (ou objet enchanté) appliqué à l'enclume. */
        public int enchanteurXpEnclume = 6;

        /** Voyageur : recharge de la balise, en minutes, et ce qu'on retire par niveau (minimum 5 min). */
        public double voyageurRechargeMinutes = 30, voyageurRechargeMoinsParNiveau = 0.8;
        /** Durée de la canalisation (immobile), en secondes. */
        public int voyageurCanalisationSecondes = 10;
        /** Distance d'arrivée de l'autre côté de la limite, en blocs. */
        public int voyageurDistanceSortie = 24;
        public int voyageurXpSaut = 25;
        /** Au-delà de la limite : 1 XP tous les N blocs parcourus. */
        public int voyageurBlocsParXp = 40;

        /** Pacificateur : XP par point de vie max du monstre tué (de jour). */
        public double pacificateurXpParPv = 0.6;
        /** Prime (pièces, versée en banque) par monstre tué de jour, +1 tous les N niveaux. */
        public int pacificateurPrime = 1, pacificateurPrimePlusTousLesNiveaux = 10;
        /** Prime multipliée pendant un événement. */
        public double pacificateurMultiplicateurEvenement = 2;
        public int pacificateurPlafondPrimesJour = 40;
        /** Amende (pièces retirées de la banque) par monstre tué la nuit. */
        public int pacificateurAmendeNuit = 2;

        /** Alchimiste : niveaux pour les améliorations (durée = redstone, puissance = glowstone), jetables, persistantes. */
        public int alchimisteNiveauDuree = 5, alchimisteNiveauPuissance = 5, alchimisteNiveauJetable = 8, alchimisteNiveauPersistante = 15;
        public int alchimisteXpPotion = 4, alchimisteXpJetable = 6, alchimisteXpPersistante = 9, alchimisteXpAmelioration = 2;

        /** Pêcheur : XP par prise (identifiant d'objet), XP des trésors et des autres prises. */
        public Map<String, Integer> pecheurXp = new LinkedHashMap<>();
        public int pecheurXpTresor = 12, pecheurXpAutre = 1;
        /** Chasse : XP par point de vie max de l'animal tué. */
        public double pecheurXpChasseParPv = 0.25;
        /** Niveaux qui ajoutent +1 Appât (poisson plus rapide) et +1 Chance de la mer. */
        public List<Integer> pecheurNiveauxAppat = new ArrayList<>(List.of(10, 20));
        public List<Integer> pecheurNiveauxChance = new ArrayList<>(List.of(15, 25));
    }

    public record Tier(SideJob job, int level, int xp) {}

    private static Data data = defaults();
    private static Map<Block, Tier> reserved = new HashMap<>();

    private SideJobConfig() {}

    public static Data data() {
        return data;
    }

    /** Le métier supplémentaire (avec niveau et XP) qui est seul à pouvoir poser ce bloc, ou null. */
    public static Tier reservedPlacement(Block block) {
        return reserved.get(block);
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                    Data loaded = GSON.fromJson(r, Data.class);
                    if (loaded != null) data = loaded;
                }
            } else {
                data = defaults();
                save();
            }
        } catch (Exception e) {
            SurvieRpg.LOGGER.error("Impossible de lire {} : on garde la config précédente", FILE, e);
        }
        resolve();
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(data, w);
            }
        } catch (IOException e) {
            SurvieRpg.LOGGER.error("Impossible d'écrire {}", FILE, e);
        }
    }

    /** Transforme les noms de blocs / tags en vrais blocs (à refaire après chaque /reload). */
    public static void resolve() {
        Map<Block, Tier> m = new HashMap<>();
        addAll(m, SideJob.INGENIEUR, data.ingenieur);
        addAll(m, SideJob.CONSTRUCTEUR, data.constructeur);
        reserved = m;
        SurvieRpg.LOGGER.info("Métiers supplémentaires : {} blocs réservés à la pose", m.size());
    }

    private static void addAll(Map<Block, Tier> m, SideJob job, List<JobConfig.Palier> paliers) {
        if (paliers == null) return;
        for (JobConfig.Palier p : paliers) {
            Tier t = new Tier(job, Math.max(1, p.niveau), Math.max(0, p.xp));
            for (String s : p.blocs) {
                JobConfig.expand(s, b -> m.merge(b, t, (old, neu) -> neu.level() < old.level() ? neu : old));
            }
        }
    }

    // ------------------------------------------------------------- valeurs par défaut

    private static Data defaults() {
        Data d = new Data();
        d.ingenieur = new ArrayList<>(List.of(
                new JobConfig.Palier(1, 2, "minecraft:redstone_wire", "minecraft:redstone_torch", "minecraft:redstone_wall_torch",
                        "minecraft:repeater", "minecraft:redstone_lamp", "minecraft:redstone_block", "minecraft:target"),
                new JobConfig.Palier(5, 3, "minecraft:piston", "minecraft:comparator", "minecraft:daylight_detector",
                        "minecraft:tripwire_hook", "minecraft:tripwire"),
                new JobConfig.Palier(8, 3, "minecraft:powered_rail", "minecraft:detector_rail", "minecraft:activator_rail"),
                new JobConfig.Palier(10, 4, "minecraft:sticky_piston", "minecraft:observer", "minecraft:dropper"),
                new JobConfig.Palier(15, 5, "minecraft:dispenser", "minecraft:hopper"),
                new JobConfig.Palier(20, 6, "minecraft:calibrated_sculk_sensor", "minecraft:lightning_rod")));

        List<String> glass = new ArrayList<>(), concrete = new ArrayList<>(), glazed = new ArrayList<>();
        for (DyeColor c : DyeColor.values()) {
            glass.add("minecraft:" + c.getName() + "_stained_glass");
            glass.add("minecraft:" + c.getName() + "_stained_glass_pane");
            concrete.add("minecraft:" + c.getName() + "_concrete");
            glazed.add("minecraft:" + c.getName() + "_glazed_terracotta");
        }
        d.constructeur = new ArrayList<>(List.of(
                new JobConfig.Palier(1, 2, "minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks",
                        "minecraft:chiseled_stone_bricks", "minecraft:stone_brick_stairs", "minecraft:stone_brick_slab",
                        "minecraft:stone_brick_wall", "minecraft:mossy_stone_brick_stairs", "minecraft:mossy_stone_brick_slab",
                        "minecraft:mossy_stone_brick_wall", "minecraft:smooth_stone", "minecraft:smooth_stone_slab"),
                new JobConfig.Palier(5, 2, glass.toArray(String[]::new)),
                new JobConfig.Palier(10, 3, concrete.toArray(String[]::new)),
                new JobConfig.Palier(15, 3, glazed.toArray(String[]::new)),
                new JobConfig.Palier(20, 4, "minecraft:quartz_block", "minecraft:quartz_bricks", "minecraft:quartz_pillar",
                        "minecraft:chiseled_quartz_block", "minecraft:smooth_quartz", "minecraft:quartz_stairs", "minecraft:quartz_slab",
                        "minecraft:smooth_quartz_stairs", "minecraft:smooth_quartz_slab", "minecraft:deepslate_bricks",
                        "minecraft:deepslate_tiles", "minecraft:polished_blackstone_bricks")));

        d.pecheurXp.put("minecraft:cod", 2);
        d.pecheurXp.put("minecraft:salmon", 3);
        d.pecheurXp.put("minecraft:pufferfish", 4);
        d.pecheurXp.put("minecraft:tropical_fish", 6);
        return d;
    }
}
