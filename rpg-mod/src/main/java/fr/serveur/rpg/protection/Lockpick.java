package fr.serveur.rpg.protection;

import fr.serveur.rpg.Msg;
import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.economy.Bank;
import fr.serveur.rpg.gui.ChestMenu;
import fr.serveur.rpg.tools.OfficialTools;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Crochet (passe-partout) : permet de tenter de cambrioler le coffre d'un autre joueur.
 * - consommé à chaque tentative ;
 * - en cas de réussite, le voleur choisit jusqu'à 3 types d'objets et prend toute leur quantité ;
 * - le propriétaire n'est averti que si le cambriolage réussit.
 */
public final class Lockpick {
    public static final int MAX_TYPES = 3;
    private static final int SLOT_CONFIRM = 49;

    private Lockpick() {}

    public static ItemStack create(int count) {
        return new ItemStack(fr.serveur.rpg.items.ModItems.CROCHET, count);
    }

    public static boolean isLockpick(ItemStack s) {
        if (s.isOf(fr.serveur.rpg.items.ModItems.CROCHET)) return true;
        NbtCompound tag = s.getSubNbt(OfficialTools.NBT_KEY); // anciens crochets (crochet de fil + NBT)
        return tag != null && "crochet".equals(tag.getString("type"));
    }

    public static void attempt(ServerPlayerEntity thief, ServerWorld world, BlockPos pos, BlockState state, UUID owner, ItemStack pick) {
        pick.decrement(1);
        RpgState rs = RpgState.get(thief.getServer());
        if (world.random.nextInt(100) >= rs.lockpickChance) {
            world.playSound(null, pos, SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 0.8f, 1.4f);
            thief.sendMessage(Text.literal("Le crochet se brise... raté.").formatted(Formatting.RED), true);
            return;
        }
        world.playSound(null, pos, SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, SoundCategory.BLOCKS, 0.6f, 1.6f);
        openSelection(thief, world, pos, state, owner, new LinkedHashSet<>());
    }

    private static Inventory chestInventory(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock chest)) return null;
        return ChestBlock.getInventory(chest, state, world, pos, true);
    }

    /** Types d'objets du coffre, avec leur quantité totale. */
    private static Map<Item, Integer> contents(Inventory inv) {
        Map<Item, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty()) m.merge(s.getItem(), s.getCount(), Integer::sum);
        }
        return m;
    }

    private static void openSelection(ServerPlayerEntity thief, ServerWorld world, BlockPos pos, BlockState state, UUID owner, Set<Item> chosen) {
        Inventory chest = chestInventory(world, pos);
        if (chest == null) return;
        Map<Item, Integer> content = contents(chest);
        List<Item> types = new ArrayList<>(content.keySet());

        SimpleInventory inv = new SimpleInventory(54);
        for (int i = 0; i < Math.min(45, types.size()); i++) {
            Item item = types.get(i);
            ItemStack icon = new ItemStack(item, Math.min(64, content.get(item)));
            boolean selected = chosen.contains(item);
            OfficialTools.setLore(icon,
                    Text.literal("Quantité : " + content.get(item)).formatted(Formatting.GRAY),
                    Text.literal(selected ? "✔ Sélectionné" : "Clic : sélectionner").formatted(selected ? Formatting.GREEN : Formatting.YELLOW));
            if (selected) {
                net.minecraft.nbt.NbtList glint = new net.minecraft.nbt.NbtList();
                glint.add(new NbtCompound());
                icon.getOrCreateNbt().put("Enchantments", glint);
            }
            inv.setStack(i, icon);
        }
        ItemStack confirm = new ItemStack(chosen.isEmpty() ? Items.GRAY_DYE : Items.LIME_DYE);
        confirm.setCustomName(Text.literal("Voler " + chosen.size() + "/" + MAX_TYPES + " type(s)").styled(s -> s.withItalic(false).withColor(Formatting.GREEN)));
        inv.setStack(SLOT_CONFIRM, confirm);

        ChestMenu.open(thief, Text.literal("Cambriolage — choisis " + MAX_TYPES + " types max"), inv, (pl, slot, type, button) -> {
            if (type != SlotActionType.PICKUP) return;
            if (slot == SLOT_CONFIRM && !chosen.isEmpty()) {
                pl.closeHandledScreen();
                steal(pl, world, pos, owner, chosen);
                return;
            }
            if (slot < Math.min(45, types.size())) {
                Item item = types.get(slot);
                if (!chosen.remove(item)) {
                    if (chosen.size() >= MAX_TYPES) {
                        pl.sendMessage(Text.literal("Maximum " + MAX_TYPES + " types d'objets.").formatted(Formatting.RED), true);
                        return;
                    }
                    chosen.add(item);
                }
                pl.getServer().execute(() -> openSelection(pl, world, pos, state, owner, chosen));
            }
        });
    }

    private static void steal(ServerPlayerEntity thief, ServerWorld world, BlockPos pos, UUID owner, Set<Item> chosen) {
        Inventory chest = chestInventory(world, pos);
        if (chest == null || thief.squaredDistanceTo(pos.toCenterPos()) > 64) {
            thief.sendMessage(Text.literal("Le coffre n'est plus accessible.").formatted(Formatting.RED), true);
            return;
        }
        int total = 0;
        for (int i = 0; i < chest.size(); i++) {
            ItemStack s = chest.getStack(i);
            if (s.isEmpty() || !chosen.contains(s.getItem())) continue;
            ItemStack taken = s.copy();
            chest.setStack(i, ItemStack.EMPTY);
            total += taken.getCount();
            if (!thief.getInventory().insertStack(taken)) thief.dropItem(taken, false);
        }
        chest.markDirty();
        if (total == 0) return;
        Msg.send(thief, Text.literal("Cambriolage réussi : " + total + " objets volés.").formatted(Formatting.DARK_RED));
        Bank.notify(thief.getServer(), owner, "Ton coffre en " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                + " a été cambriolé ! " + total + " objets ont disparu.");
    }
}
