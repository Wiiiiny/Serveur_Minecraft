package fr.serveur.rpg.sombre;

import fr.serveur.rpg.Msg;
import fr.serveur.rpg.SurvieRpg;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Objets du monde sombre : Éclat de Lumière, Détecteur d'ombre. */
public final class SombreItems {
    /** Éclat de Lumière : prisme jaune brillant qui appelle le jour (voir {@link Lumiere}). */
    public static class EclatItem extends Item {
        public EclatItem(Settings settings) {
            super(settings);
        }

        @Override
        public boolean hasGlint(ItemStack stack) {
            return true;
        }

        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
            ItemStack stack = user.getStackInHand(hand);
            if (world.isClient) return TypedActionResult.success(stack, true);
            if (!(user instanceof ServerPlayerEntity p)) return TypedActionResult.fail(stack);
            String e = Lumiere.use(p);
            if (e != null) {
                p.sendMessage(Text.literal(e).formatted(Formatting.GRAY), true);
                return TypedActionResult.fail(stack);
            }
            if (!p.getAbilities().creativeMode) stack.decrement(1);
            return TypedActionResult.success(stack);
        }

        @Override
        public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> lines, TooltipContext context) {
            lines.add(Text.translatable("tooltip.survierpg.eclat_lumiere").formatted(Formatting.GRAY));
        }
    }

    /**
     * Détecteur d'ombre : clic droit pour l'activer ; pendant 2 minutes, tenu en main, il indique la direction et la distance
     * du coffre caché le plus proche (particules et barre d'action), puis il disparaît.
     */
    public static class DetecteurItem extends Item {
        static final String FIN = "survierpg_fin";

        public DetecteurItem(Settings settings) {
            super(settings);
        }

        static long end(ItemStack stack) {
            NbtCompound n = stack.getNbt();
            return n == null ? 0 : n.getLong(FIN);
        }

        @Override
        public boolean hasGlint(ItemStack stack) {
            return end(stack) > 0;
        }

        @Override
        public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
            ItemStack stack = user.getStackInHand(hand);
            if (world.isClient) return TypedActionResult.success(stack, true);
            if (!(user instanceof ServerPlayerEntity p)) return TypedActionResult.fail(stack);
            long fin = end(stack);
            if (fin > 0) {
                p.sendMessage(Text.literal("Détecteur actif : encore " + Msg.duration(Math.max(0, (fin - world.getTime()) / 20)) + ".")
                        .formatted(Formatting.LIGHT_PURPLE), true);
                return TypedActionResult.pass(stack);
            }
            int secs = Math.max(5, SombreConfig.get().obscurcie.detecteurSecondes);
            stack.getOrCreateNbt().putLong(FIN, world.getTime() + secs * 20L);
            world.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1f, 0.6f);
            Msg.send(p, Text.literal("Le détecteur s'éveille : " + Msg.duration(secs) + " pour trouver un coffre caché. Garde-le en main.")
                    .formatted(Formatting.LIGHT_PURPLE));
            Obscurcie.addXp(p, SombreConfig.get().obscurcie.xpDetecteur, false);
            CoffresCaches.guide(p, p.getServerWorld());
            return TypedActionResult.success(stack);
        }

        @Override
        public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
            if (world.isClient || !(entity instanceof ServerPlayerEntity p)) return;
            long fin = end(stack);
            if (fin <= 0) return;
            long now = world.getTime();
            if (now >= fin) {
                stack.setCount(0);
                Msg.send(p, Text.literal("Le Détecteur d'ombre se dissipe. Il faudra en obtenir un autre.").formatted(Formatting.GRAY));
                world.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.PLAYERS, 0.8f, 0.6f);
                return;
            }
            boolean held = selected || p.getOffHandStack() == stack;
            if (held && now % 10 == 0) CoffresCaches.guide(p, (ServerWorld) world);
        }

        @Override
        public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> lines, TooltipContext context) {
            long fin = end(stack);
            if (fin > 0 && world != null) {
                lines.add(Text.literal("Actif : encore " + Msg.duration(Math.max(0, (fin - world.getTime()) / 20))).formatted(Formatting.LIGHT_PURPLE));
            } else {
                lines.add(Text.translatable("tooltip.survierpg.detecteur").formatted(Formatting.GRAY));
            }
        }
    }

    public static final Item ECLAT_LUMIERE = new EclatItem(new Item.Settings().maxCount(16).fireproof().rarity(Rarity.EPIC));
    public static final Item DETECTEUR = new DetecteurItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE));
    public static final List<Item> ALL = List.of(ECLAT_LUMIERE, DETECTEUR);

    private SombreItems() {}

    public static void register() {
        Registry.register(Registries.ITEM, new Identifier(SurvieRpg.MOD_ID, "eclat_lumiere"), ECLAT_LUMIERE);
        Registry.register(Registries.ITEM, new Identifier(SurvieRpg.MOD_ID, "detecteur_ombre"), DETECTEUR);
    }
}
