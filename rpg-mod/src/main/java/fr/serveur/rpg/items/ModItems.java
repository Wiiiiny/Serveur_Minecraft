package fr.serveur.rpg.items;

import fr.serveur.rpg.SurvieRpg;
import fr.serveur.rpg.jobs.Job;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.ToolMaterials;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

import java.util.EnumMap;
import java.util.Map;

/**
 * Items du serveur Survie RPG et onglet « Survie RPG » du menu créatif.
 * - 20 outils officiels (4 métiers x 5 matériaux), manche aux couleurs du métier ;
 * - Pioche des ténèbres, Larme de vie, Crochet.
 */
public final class ModItems {
    public enum Tier {
        BOIS("bois", ToolMaterials.WOOD, 6.0f, -3.2f, 0, -3.0f),
        PIERRE("pierre", ToolMaterials.STONE, 7.0f, -3.2f, -1, -2.0f),
        FER("fer", ToolMaterials.IRON, 6.0f, -3.1f, -2, -1.0f),
        DIAMANT("diamant", ToolMaterials.DIAMOND, 5.0f, -3.0f, -3, 0.0f),
        NETHERITE("netherite", ToolMaterials.NETHERITE, 5.0f, -3.0f, -4, 0.0f);

        public final String id;
        public final ToolMaterials material;
        final float axeDamage, axeSpeed, hoeSpeed;
        final int hoeDamage;

        Tier(String id, ToolMaterials material, float axeDamage, float axeSpeed, int hoeDamage, float hoeSpeed) {
            this.id = id;
            this.material = material;
            this.axeDamage = axeDamage;
            this.axeSpeed = axeSpeed;
            this.hoeDamage = hoeDamage;
            this.hoeSpeed = hoeSpeed;
        }

        public static Tier of(ToolMaterial material) {
            for (Tier t : values()) if (t.material == material) return t;
            return null;
        }
    }

    /** Netherite incassable (durabilité 0 = pas de barre d'usure). */
    private static final ToolMaterial DARK = new ToolMaterial() {
        @Override public int getDurability() { return 0; }
        @Override public float getMiningSpeedMultiplier() { return 9.0f; }
        @Override public float getAttackDamage() { return 4.0f; }
        @Override public int getMiningLevel() { return 4; }
        @Override public int getEnchantability() { return 0; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
    };

    public static final Map<Job, Map<Tier, Item>> OFFICIAL = new EnumMap<>(Job.class);

    public static final Item PIOCHE_TENEBRES = new DarkPickaxeItem(DARK,
            new Item.Settings().maxCount(1).fireproof().rarity(Rarity.EPIC));
    public static final Item LARME_DE_VIE = new TearItem(new Item.Settings().maxCount(16).rarity(Rarity.RARE));
    public static final Item CROCHET = new LockpickItem(new Item.Settings().maxCount(16).rarity(Rarity.UNCOMMON));
    /** Balise du Voyageur : objet du métier, jamais perdu. */
    public static final Item BALISE = new TravelBeaconItem(new Item.Settings().maxCount(1).fireproof().rarity(Rarity.RARE));
    /** Ce que donne la Pierre obscure : on ne récupère jamais le bloc, donc impossible de le reposer. */
    public static final Item ECLAT_OBSCUR = new Item(new Item.Settings().rarity(Rarity.UNCOMMON)) {
        @Override
        public void appendTooltip(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.World world,
                                  java.util.List<Text> tooltip, net.minecraft.client.item.TooltipContext context) {
            tooltip.add(Text.translatable("tooltip.survierpg.eclat").formatted(net.minecraft.util.Formatting.GRAY));
        }
    };
    public static final Item PIECE_OR = new Item(new Item.Settings().rarity(Rarity.UNCOMMON)) {
        @Override
        public void appendTooltip(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.World world,
                                  java.util.List<Text> tooltip, net.minecraft.client.item.TooltipContext context) {
            tooltip.add(Text.translatable("tooltip.survierpg.piece").formatted(net.minecraft.util.Formatting.GRAY));
        }
    };

    private ModItems() {}

    public static void register() {
        register("pioche_tenebres", PIOCHE_TENEBRES);
        register("larme_de_vie", LARME_DE_VIE);
        register("crochet", CROCHET);
        register("piece_or", PIECE_OR);
        register("eclat_obscur", ECLAT_OBSCUR);
        register("balise", BALISE);
        RaidItems.register();
        fr.serveur.rpg.sombre.SombreItems.register();
        fr.serveur.rpg.faction.FactionItems.register();
        fr.serveur.rpg.blocks.ModBlocks.register();

        for (Job job : Job.values()) {
            Map<Tier, Item> byTier = new EnumMap<>(Tier.class);
            for (Tier tier : Tier.values()) {
                Item.Settings settings = new Item.Settings().rarity(Rarity.UNCOMMON);
                if (tier == Tier.NETHERITE) settings.fireproof();
                Item item = switch (job) {
                    case MINEUR -> new OfficialPickaxeItem(tier.material, job, settings);
                    case BUCHERON -> new OfficialAxeItem(tier.material, tier.axeDamage, tier.axeSpeed, job, settings);
                    case FERMIER -> new OfficialHoeItem(tier.material, tier.hoeDamage, tier.hoeSpeed, job, settings);
                    case AVENTURIER -> new OfficialSwordItem(tier.material, job, settings);
                };
                register("outil_" + job.id() + "_" + tier.id, item);
                byTier.put(tier, item);
            }
            OFFICIAL.put(job, byTier);
        }

        Registry.register(Registries.ITEM_GROUP, new Identifier(SurvieRpg.MOD_ID, "main"), FabricItemGroup.builder()
                .icon(() -> new ItemStack(PIOCHE_TENEBRES))
                .displayName(Text.translatable("itemGroup.survierpg.main"))
                .entries((context, entries) -> {
                    entries.add(PIOCHE_TENEBRES);
                    entries.add(LARME_DE_VIE);
                    entries.add(CROCHET);
                    entries.add(PIECE_OR);
                    entries.add(ECLAT_OBSCUR);
                    entries.add(BALISE);
                    RaidItems.ALL.forEach(entries::add);
                    fr.serveur.rpg.sombre.SombreItems.ALL.forEach(entries::add);
                    entries.add(fr.serveur.rpg.faction.FactionItems.ETENDARD);
                    entries.add(fr.serveur.rpg.blocks.ModBlocks.PIERRE_OBSCURE_ITEM);
                    Registries.ITEM.getOrEmpty(new Identifier("nightfall", "shadow_hood")).ifPresent(entries::add);
                    for (Job job : Job.values()) for (Tier tier : Tier.values()) entries.add(OFFICIAL.get(job).get(tier));
                })
                .build());
    }

    private static void register(String id, Item item) {
        Registry.register(Registries.ITEM, new Identifier(SurvieRpg.MOD_ID, id), item);
    }

    /** L'outil officiel d'un métier pour un matériau vanilla (null si le matériau n'existe pas, ex. or). */
    public static Item official(Job job, ToolMaterial material) {
        Tier tier = Tier.of(material);
        return tier == null ? null : OFFICIAL.get(job).get(tier);
    }
}
