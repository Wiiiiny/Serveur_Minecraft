package fr.serveur.rpg.faction;

import fr.serveur.rpg.SurvieRpg;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Étendard de revendication (boutique de faction) et Cœur de faction (posé au centre de la base, indestructible à la pioche). */
public final class FactionItems {
    public static class EtendardItem extends Item {
        public EtendardItem(Settings settings) {
            super(settings);
        }

        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
            ItemStack stack = user.getStackInHand(hand);
            if (world.isClient) return TypedActionResult.success(stack, true);
            if (!(user instanceof ServerPlayerEntity p)) return TypedActionResult.fail(stack);
            String e = Territories.claim(p);
            if (e != null) {
                p.sendMessage(Text.literal(e).formatted(Formatting.RED), true);
                return TypedActionResult.fail(stack);
            }
            if (!p.getAbilities().creativeMode) stack.decrement(1);
            return TypedActionResult.success(stack);
        }

        @Override
        public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> lines, TooltipContext context) {
            lines.add(Text.translatable("tooltip.survierpg.etendard").formatted(Formatting.GRAY));
        }
    }

    public static final Item ETENDARD = new EtendardItem(new Item.Settings().maxCount(16).rarity(Rarity.RARE));
    /** Indestructible à la main ou à l'explosion : il se « frappe » (voir Wars.hitHeart) pendant une attaque permise. */
    public static final Block COEUR = new Block(AbstractBlock.Settings.create().mapColor(MapColor.PURPLE).strength(-1.0f, 3_600_000f)
            .dropsNothing().luminance(state -> 12).sounds(BlockSoundGroup.AMETHYST_BLOCK).pistonBehavior(PistonBehavior.BLOCK));
    public static final Item COEUR_ITEM = new BlockItem(COEUR, new Item.Settings().rarity(Rarity.EPIC));

    private FactionItems() {}

    public static void register() {
        Registry.register(Registries.ITEM, new Identifier(SurvieRpg.MOD_ID, "etendard"), ETENDARD);
        Identifier coeur = new Identifier(SurvieRpg.MOD_ID, "coeur_faction");
        Registry.register(Registries.BLOCK, coeur, COEUR);
        Registry.register(Registries.ITEM, coeur, COEUR_ITEM);
    }
}
