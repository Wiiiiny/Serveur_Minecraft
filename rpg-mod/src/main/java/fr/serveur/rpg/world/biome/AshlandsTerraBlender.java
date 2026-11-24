package fr.serveur.rpg.world.biome;

import com.mojang.datafixers.util.Pair;
import fr.serveur.rpg.SurvieRpg;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.noise.NoiseParametersKeys;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.gen.surfacebuilder.MaterialRules;
import terrablender.api.Region;
import terrablender.api.RegionType;
import terrablender.api.Regions;
import terrablender.api.SurfaceRuleManager;
import terrablender.api.TerraBlenderApi;

import java.util.function.Consumer;

/**
 * Biome « Terres cendrées » (Overworld, nouveaux chunks seulement) : sol de tuf, de gravier et d'andésite,
 * arbres morts, rochers, cendre grise qui tombe en continu. Le biome lui-même est un fichier de données
 * (data/survierpg/worldgen/biome/terres_cendrees.json) ; ici on le place dans le monde avec TerraBlender
 * (installé sur le serveur avec Biomes O' Plenty) et on définit son sol.
 * Appelé par l'entrypoint « terrablender » de fabric.mod.json.
 */
public final class AshlandsTerraBlender implements TerraBlenderApi {
    public static final RegistryKey<Biome> TERRES_CENDREES =
            RegistryKey.of(RegistryKeys.BIOME, new Identifier(SurvieRpg.MOD_ID, "terres_cendrees"));
    /** Poids de la région (les régions vanilla pèsent 10) : plus il est haut, plus le biome est fréquent. */
    private static final int REGION_WEIGHT = 4;

    @Override
    public void onTerraBlenderInitialized() {
        Regions.register(new AshlandsRegion());
        SurfaceRuleManager.addSurfaceRules(SurfaceRuleManager.RuleCategory.OVERWORLD, SurvieRpg.MOD_ID, surfaceRules());
        SurvieRpg.LOGGER.info("Biome Terres cendrées enregistré (TerraBlender, poids {})", REGION_WEIGHT);
    }

    /** Dans cette région, les plaines et savanes deviennent des Terres cendrées. */
    private static final class AshlandsRegion extends Region {
        AshlandsRegion() {
            super(new Identifier(SurvieRpg.MOD_ID, "terres_cendrees"), RegionType.OVERWORLD, REGION_WEIGHT);
        }

        @Override
        public void addBiomes(Registry<Biome> registry, Consumer<Pair<MultiNoiseUtil.NoiseHypercube, RegistryKey<Biome>>> mapper) {
            addModifiedVanillaOverworldBiomes(mapper, builder -> {
                builder.replaceBiome(BiomeKeys.PLAINS, TERRES_CENDREES);
                builder.replaceBiome(BiomeKeys.SUNFLOWER_PLAINS, TERRES_CENDREES);
                builder.replaceBiome(BiomeKeys.SAVANNA, TERRES_CENDREES);
            });
        }
    }

    /** Sol : tuf, plaques de gravier et d'andésite, un peu de terre stérile. */
    private static MaterialRules.MaterialRule surfaceRules() {
        MaterialRules.MaterialRule tuff = MaterialRules.block(Blocks.TUFF.getDefaultState());
        MaterialRules.MaterialRule gravel = MaterialRules.block(Blocks.GRAVEL.getDefaultState());
        MaterialRules.MaterialRule andesite = MaterialRules.block(Blocks.ANDESITE.getDefaultState());
        MaterialRules.MaterialRule coarse = MaterialRules.block(Blocks.COARSE_DIRT.getDefaultState());
        MaterialRules.MaterialRule top = MaterialRules.sequence(
                MaterialRules.condition(MaterialRules.noiseThreshold(NoiseParametersKeys.SURFACE, 0.22), gravel),
                MaterialRules.condition(MaterialRules.noiseThreshold(NoiseParametersKeys.SURFACE, -0.45, -0.18), andesite),
                MaterialRules.condition(MaterialRules.noiseThreshold(NoiseParametersKeys.PATCH, 0.55), coarse),
                tuff);
        return MaterialRules.condition(MaterialRules.biome(TERRES_CENDREES), MaterialRules.condition(MaterialRules.surface(),
                MaterialRules.sequence(
                        MaterialRules.condition(MaterialRules.STONE_DEPTH_FLOOR, top),
                        MaterialRules.condition(MaterialRules.STONE_DEPTH_FLOOR_WITH_SURFACE_DEPTH, tuff))));
    }
}
