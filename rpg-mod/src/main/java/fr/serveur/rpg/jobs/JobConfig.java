package fr.serveur.rpg.jobs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.serveur.rpg.SurvieRpg;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Paliers des métiers : quels blocs chaque niveau débloque, et combien d'XP ils rapportent.
 * Fichier : config/survierpg/metiers.json (modifiable à la main ou avec /gm palier).
 * Une entrée peut être un bloc (« minecraft:stone ») ou un tag (« #minecraft:logs »).
 */
public final class JobConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("survierpg").resolve("metiers.json");

    // ------------------------------------------------------------- format JSON

    public static final class Data {
        public int xpBase = 50;
        public int xpParNiveau = 30;
        /** XP de métier pour l'Aventurier, par point de vie maximum du mob tué. */
        public double xpAventurierParPv = 0.5;
        /** Blocs cassables à la main, par tout le monde. */
        public List<String> mainNue = new ArrayList<>();
        public Map<String, List<Palier>> metiers = new LinkedHashMap<>();
    }

    public static final class Palier {
        public int niveau;
        public int xp;
        public List<String> blocs = new ArrayList<>();

        public Palier() {}

        Palier(int niveau, int xp, String... blocs) {
            this.niveau = niveau;
            this.xp = xp;
            this.blocs = new ArrayList<>(Arrays.asList(blocs));
        }
    }

    public record Tier(int level, int xp) {}

    // ------------------------------------------------------------- état chargé

    private static Data data = defaults();
    private static Set<Block> handBlocks = Set.of();
    private static Map<Job, Map<Block, Tier>> jobBlocks = new EnumMap<>(Job.class);

    private JobConfig() {}

    public static Data data() {
        return data;
    }

    public static int xpNeeded(int level) {
        return data.xpBase + data.xpParNiveau * level;
    }

    public static boolean isHandBreakable(Block block) {
        return handBlocks.contains(block);
    }

    public static Tier tier(Job job, Block block) {
        Map<Block, Tier> m = jobBlocks.get(job);
        return m == null ? null : m.get(block);
    }

    /** Le premier métier (autre que {@code except}) auquel ce bloc est réservé, ou null. */
    public static Job reservedFor(Block block, Job except) {
        for (Map.Entry<Job, Map<Block, Tier>> e : jobBlocks.entrySet()) {
            if (e.getKey() != except && e.getValue().containsKey(block)) return e.getKey();
        }
        return null;
    }

    // ------------------------------------------------------------- chargement

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
        Set<Block> hand = new HashSet<>();
        for (String s : data.mainNue) expand(s, hand::add);

        Map<Job, Map<Block, Tier>> jobs = new EnumMap<>(Job.class);
        data.metiers.forEach((jobName, paliers) -> {
            Job job = Job.parse(jobName);
            if (job == null) {
                SurvieRpg.LOGGER.warn("Métier inconnu dans la config : {}", jobName);
                return;
            }
            Map<Block, Tier> m = jobs.computeIfAbsent(job, j -> new HashMap<>());
            for (Palier p : paliers) {
                Tier t = new Tier(Math.max(1, p.niveau), Math.max(0, p.xp));
                for (String s : p.blocs) {
                    expand(s, b -> m.merge(b, t, (old, neu) -> neu.level() < old.level() ? neu : old));
                }
            }
        });
        handBlocks = hand;
        jobBlocks = jobs;
        SurvieRpg.LOGGER.info("Paliers chargés : {} blocs à la main, {}", hand.size(),
                jobs.entrySet().stream().map(e -> e.getKey().displayName + "=" + e.getValue().size()).toList());
    }

    private static void expand(String entry, Consumer<Block> out) {
        try {
            if (entry.startsWith("#")) {
                TagKey<Block> tag = TagKey.of(RegistryKeys.BLOCK, new Identifier(entry.substring(1)));
                for (RegistryEntry<Block> e : Registries.BLOCK.iterateEntries(tag)) out.accept(e.value());
            } else {
                Identifier id = new Identifier(entry);
                if (Registries.BLOCK.containsId(id)) out.accept(Registries.BLOCK.get(id));
                else SurvieRpg.LOGGER.warn("Bloc inconnu dans la config : {}", entry);
            }
        } catch (Exception e) {
            SurvieRpg.LOGGER.warn("Entrée invalide dans la config : {}", entry);
        }
    }

    /** Vérifie qu'une entrée (bloc ou tag) existe. */
    public static boolean isValidEntry(String entry) {
        int[] count = {0};
        expand(entry, b -> count[0]++);
        return count[0] > 0;
    }

    // ------------------------------------------------------------- valeurs par défaut

    private static Data defaults() {
        Data d = new Data();
        d.mainNue = new ArrayList<>(List.of(
                "#minecraft:dirt", "#minecraft:leaves", "#minecraft:flowers", "#minecraft:saplings",
                "minecraft:grass", "minecraft:tall_grass", "minecraft:fern", "minecraft:large_fern",
                "minecraft:dead_bush", "minecraft:vine", "minecraft:glow_lichen", "minecraft:moss_carpet",
                "minecraft:sand", "minecraft:red_sand", "minecraft:gravel", "minecraft:clay", "minecraft:mud",
                "minecraft:snow", "minecraft:snow_block",
                "minecraft:torch", "minecraft:wall_torch", "minecraft:soul_torch", "minecraft:soul_wall_torch"));

        d.metiers.put("MINEUR", new ArrayList<>(List.of(
                new Palier(1, 1, "minecraft:stone", "minecraft:cobblestone", "minecraft:mossy_cobblestone",
                        "minecraft:granite", "minecraft:diorite", "minecraft:andesite", "minecraft:tuff",
                        "minecraft:calcite", "minecraft:sandstone", "minecraft:red_sandstone",
                        "minecraft:dripstone_block", "minecraft:pointed_dripstone", "#minecraft:terracotta",
                        "minecraft:ice", "minecraft:packed_ice"),
                new Palier(1, 3, "minecraft:coal_ore"),
                new Palier(3, 2, "minecraft:deepslate", "minecraft:cobbled_deepslate"),
                new Palier(5, 4, "minecraft:copper_ore", "minecraft:deepslate_copper_ore", "minecraft:deepslate_coal_ore"),
                new Palier(8, 6, "minecraft:iron_ore", "minecraft:deepslate_iron_ore", "minecraft:blue_ice"),
                new Palier(12, 8, "minecraft:lapis_ore", "minecraft:deepslate_lapis_ore",
                        "minecraft:redstone_ore", "minecraft:deepslate_redstone_ore"),
                new Palier(15, 10, "minecraft:gold_ore", "minecraft:deepslate_gold_ore", "minecraft:nether_gold_ore",
                        "minecraft:nether_quartz_ore", "minecraft:netherrack", "minecraft:basalt",
                        "minecraft:blackstone", "minecraft:magma_block"),
                new Palier(18, 12, "minecraft:amethyst_block", "minecraft:amethyst_cluster", "minecraft:obsidian",
                        "minecraft:crying_obsidian", "minecraft:end_stone"),
                new Palier(20, 18, "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore"),
                new Palier(22, 22, "minecraft:emerald_ore", "minecraft:deepslate_emerald_ore"),
                new Palier(25, 40, "minecraft:ancient_debris"))));

        d.metiers.put("BUCHERON", new ArrayList<>(List.of(
                new Palier(1, 2, "#minecraft:oak_logs", "#minecraft:dark_oak_logs", "#minecraft:birch_logs"),
                new Palier(5, 3, "#minecraft:spruce_logs"),
                new Palier(8, 4, "#minecraft:jungle_logs", "#minecraft:acacia_logs"),
                new Palier(12, 5, "#minecraft:mangrove_logs", "#minecraft:cherry_logs", "minecraft:bamboo_block"),
                new Palier(15, 6, "#minecraft:logs"),
                new Palier(18, 7, "minecraft:brown_mushroom_block", "minecraft:red_mushroom_block",
                        "minecraft:mushroom_stem", "minecraft:bee_nest"),
                new Palier(22, 10, "#minecraft:crimson_stems", "#minecraft:warped_stems", "minecraft:shroomlight",
                        "minecraft:nether_wart_block", "minecraft:warped_wart_block"))));

        d.metiers.put("FERMIER", new ArrayList<>(List.of(
                new Palier(1, 2, "minecraft:wheat", "minecraft:carrots", "minecraft:potatoes"),
                new Palier(5, 3, "minecraft:beetroots", "minecraft:pumpkin", "minecraft:melon"),
                new Palier(8, 3, "minecraft:sugar_cane", "minecraft:bamboo", "minecraft:cactus", "minecraft:kelp",
                        "minecraft:kelp_plant"),
                new Palier(12, 4, "minecraft:sweet_berry_bush", "minecraft:cocoa", "minecraft:hay_block",
                        "minecraft:brown_mushroom", "minecraft:red_mushroom"),
                new Palier(15, 6, "minecraft:nether_wart", "minecraft:cave_vines",
                        "minecraft:cave_vines_plant"),
                new Palier(20, 8, "minecraft:chorus_plant", "minecraft:chorus_flower", "minecraft:torchflower_crop",
                        "minecraft:pitcher_crop"))));

        d.metiers.put("AVENTURIER", new ArrayList<>(List.of(
                new Palier(1, 2, "minecraft:cobweb"),
                new Palier(10, 4, "minecraft:sculk", "minecraft:sculk_vein"),
                new Palier(20, 8, "minecraft:sculk_sensor", "minecraft:sculk_shrieker", "minecraft:sculk_catalyst"))));
        return d;
    }
}
