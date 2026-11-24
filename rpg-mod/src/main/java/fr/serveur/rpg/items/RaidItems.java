package fr.serveur.rpg.items;

import fr.serveur.rpg.SurvieRpg;
import fr.serveur.rpg.raid.ReturnBeacon;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Objets de la zone Nether de raid. */
public final class RaidItems {
    /** Objet simple avec une ligne d'aide. */
    public static class Described extends Item {
        private final String tooltip;
        private final boolean glint;

        public Described(Settings settings, String tooltip, boolean glint) {
            super(settings);
            this.tooltip = tooltip;
            this.glint = glint;
        }

        @Override
        public boolean hasGlint(ItemStack stack) {
            return glint || super.hasGlint(stack);
        }

        @Override
        public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> lines, TooltipContext context) {
            lines.add(Text.translatable(tooltip).formatted(Formatting.GRAY));
        }
    }

    /** Balise de retour : canalisation de 10 s dans le raid, puis retour au hub avec le butin. */
    public static class ReturnBeaconItem extends Described {
        public ReturnBeaconItem(Settings settings) {
            super(settings, "tooltip.survierpg.balise_retour", true);
        }

        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
            ItemStack stack = user.getStackInHand(hand);
            if (world.isClient) return TypedActionResult.success(stack, true);
            return user instanceof ServerPlayerEntity sp && ReturnBeacon.start(sp, hand) ? TypedActionResult.success(stack) : TypedActionResult.fail(stack);
        }
    }

    public static final Item BALISE_RETOUR = new ReturnBeaconItem(new Item.Settings().maxCount(16).fireproof().rarity(Rarity.UNCOMMON));
    public static final Item CLE_RAID = new Described(new Item.Settings().maxCount(16).fireproof().rarity(Rarity.RARE), "tooltip.survierpg.cle_raid", true);
    public static final Item FRAGMENT_TENEBRES = new Described(new Item.Settings().fireproof().rarity(Rarity.UNCOMMON), "tooltip.survierpg.fragment_tenebres", false);
    /** 12 statues, pas de réparation à l'enclume (réparation contre pièces dans le menu Raid). */
    public static final Item BRISE_STATUE = new Described(new Item.Settings().maxDamage(12).fireproof().rarity(Rarity.RARE), "tooltip.survierpg.brise_statue", false);
    public static final Item RELIQUE_ARDENTE = new Described(new Item.Settings().fireproof().rarity(Rarity.UNCOMMON), "tooltip.survierpg.relique", false);
    public static final Item IDOLE_BASALTE = new Described(new Item.Settings().fireproof().rarity(Rarity.RARE), "tooltip.survierpg.relique", false);
    public static final Item COURONNE_DECHUE = new Described(new Item.Settings().maxCount(1).fireproof().rarity(Rarity.EPIC), "tooltip.survierpg.relique", true);

    public static final List<Item> ALL = List.of(BALISE_RETOUR, CLE_RAID, FRAGMENT_TENEBRES, BRISE_STATUE, RELIQUE_ARDENTE, IDOLE_BASALTE, COURONNE_DECHUE);

    private RaidItems() {}

    static void register() {
        reg("balise_retour", BALISE_RETOUR);
        reg("cle_raid", CLE_RAID);
        reg("fragment_tenebres", FRAGMENT_TENEBRES);
        reg("brise_statue", BRISE_STATUE);
        reg("relique_ardente", RELIQUE_ARDENTE);
        reg("idole_basalte", IDOLE_BASALTE);
        reg("couronne_dechue", COURONNE_DECHUE);
    }

    private static void reg(String id, Item item) {
        Registry.register(Registries.ITEM, new Identifier(SurvieRpg.MOD_ID, id), item);
    }
}
