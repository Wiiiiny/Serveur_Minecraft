package fr.serveur.rpg.blocks;

import fr.serveur.rpg.SurvieRpg;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.GenerationStep;

/**
 * Pierre obscure : minerai violet qui se génère en filons dans l'Overworld, le Nether et l'End.
 * Seule la Pioche des ténèbres la récolte, et elle donne un Éclat obscur (jamais le bloc).
 * 4 Éclats obscurs = 1 Pièce d'or.
 * Génération : data/survierpg/worldgen/{configured_feature,placed_feature}/pierre_obscure.json
 */
public final class ModBlocks {
    public static final Block PIERRE_OBSCURE = new Block(AbstractBlock.Settings.create()
            .mapColor(MapColor.PURPLE)
            .strength(3.0f, 6.0f)
            .requiresTool()
            .luminance(state -> 3)
            .sounds(BlockSoundGroup.DEEPSLATE));
    public static final Item PIERRE_OBSCURE_ITEM = new BlockItem(PIERRE_OBSCURE, new Item.Settings());

    private ModBlocks() {}

    public static void register() {
        Identifier id = new Identifier(SurvieRpg.MOD_ID, "pierre_obscure");
        Registry.register(Registries.BLOCK, id, PIERRE_OBSCURE);
        Registry.register(Registries.ITEM, id, PIERRE_OBSCURE_ITEM);

        BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld().or(BiomeSelectors.foundInTheNether()).or(BiomeSelectors.foundInTheEnd())
                        // Biome ajouté par TerraBlender : on le cite pour être sûr qu'il ait aussi sa Pierre obscure.
                        .or(BiomeSelectors.includeByKey(fr.serveur.rpg.world.biome.AshlandsTerraBlender.TERRES_CENDREES)),
                GenerationStep.Feature.UNDERGROUND_ORES,
                RegistryKey.of(RegistryKeys.PLACED_FEATURE, id));
    }
}
