package fr.serveur.rpg.gui;

import fr.serveur.rpg.Msg;
import fr.serveur.rpg.data.MarketItem;
import fr.serveur.rpg.data.PlayerData;
import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.economy.Bank;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** Hôtel des ventes (/hdv) et boutique du GM (/boutique) : menus coffre de 6 lignes. */
public final class MarketMenus {
    private static final int PER_PAGE = 45;
    private static final int SLOT_PREV = 45, SLOT_INFO = 49, SLOT_NEXT = 53;

    private MarketMenus() {}

    public static void openAuctions(ServerPlayerEntity player, int page) {
        open(player, page, false);
    }

    public static void openShop(ServerPlayerEntity player, int page) {
        open(player, page, true);
    }

    private static void open(ServerPlayerEntity player, int page, boolean shop) {
        RpgState state = RpgState.get(player.getServer());
        List<MarketItem> items = shop ? state.shop : state.auctions;
        int pages = Math.max(1, (items.size() + PER_PAGE - 1) / PER_PAGE);
        int p = Math.max(0, Math.min(page, pages - 1));

        SimpleInventory inv = new SimpleInventory(54);
        int[] ids = new int[PER_PAGE];
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = p * PER_PAGE + i;
            if (idx >= items.size()) break;
            MarketItem m = items.get(idx);
            ids[i] = m.id;
            inv.setStack(i, display(m, shop, player.getUuid().equals(m.seller)));
        }
        ItemStack filler = button(Items.BLACK_STAINED_GLASS_PANE, " ", Formatting.BLACK);
        for (int i = 45; i < 54; i++) inv.setStack(i, filler.copy());
        if (p > 0) inv.setStack(SLOT_PREV, button(Items.ARROW, "Page précédente", Formatting.YELLOW));
        if (p < pages - 1) inv.setStack(SLOT_NEXT, button(Items.ARROW, "Page suivante", Formatting.YELLOW));
        PlayerData me = state.player(player);
        inv.setStack(SLOT_INFO, button(Items.GLOWSTONE_DUST, "Ton solde : " + Bank.format(me.money), Formatting.GOLD));

        Text title = Text.literal((shop ? "Boutique du GM" : "Hôtel des ventes") + " — " + (p + 1) + "/" + pages);
        ChestMenu.open(player, title, inv, (pl, slot, type, button) -> {
            if (slot == SLOT_PREV && p > 0) reopen(pl, p - 1, shop);
            else if (slot == SLOT_NEXT && p < pages - 1) reopen(pl, p + 1, shop);
            else if (slot < PER_PAGE && ids[slot] != 0 && type == SlotActionType.PICKUP) {
                if (shop) buyFromShop(pl, ids[slot]);
                else clickAuction(pl, ids[slot]);
                reopen(pl, p, shop);
            }
        });
    }

    private static void reopen(ServerPlayerEntity player, int page, boolean shop) {
        player.getServer().execute(() -> open(player, page, shop));
    }

    private static void clickAuction(ServerPlayerEntity buyer, int id) {
        RpgState state = RpgState.get(buyer.getServer());
        MarketItem m = state.auctions.stream().filter(a -> a.id == id).findFirst().orElse(null);
        if (m == null) {
            Msg.send(buyer, Text.literal("Cet objet n'est plus en vente.").formatted(Formatting.RED));
            return;
        }
        if (buyer.getUuid().equals(m.seller)) {
            state.auctions.remove(m);
            state.markDirty();
            giveOrDrop(buyer, m.stack.copy());
            Msg.send(buyer, Text.literal("Objet retiré de la vente.").formatted(Formatting.YELLOW));
            return;
        }
        PlayerData data = state.player(buyer);
        if (data.money < m.price) {
            Msg.send(buyer, Text.literal("Solde insuffisant (" + Bank.format(data.money) + "). Dépose de la poudre avec /banque deposer.").formatted(Formatting.RED));
            return;
        }
        data.money -= m.price;
        state.auctions.remove(m);
        state.markDirty();
        Bank.credit(buyer.getServer(), m.seller, m.price);
        giveOrDrop(buyer, m.stack.copy());
        buyer.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1f, 1f);
        Msg.send(buyer, Text.literal("Achat : " + m.stack.getCount() + "x ").formatted(Formatting.GREEN)
                .append(m.stack.getName()).append(" pour " + Bank.format(m.price) + "."));
        Bank.notify(buyer.getServer(), m.seller, "Hôtel des ventes : " + buyer.getGameProfile().getName() + " a acheté "
                + m.stack.getCount() + "x " + m.stack.getName().getString() + " — +" + Bank.format(m.price) + " sur ton compte.");
    }

    private static void buyFromShop(ServerPlayerEntity buyer, int id) {
        RpgState state = RpgState.get(buyer.getServer());
        MarketItem m = state.shop.stream().filter(a -> a.id == id).findFirst().orElse(null);
        if (m == null) return;
        PlayerData data = state.player(buyer);
        if (data.money < m.price) {
            Msg.send(buyer, Text.literal("Solde insuffisant (" + Bank.format(data.money) + ").").formatted(Formatting.RED));
            return;
        }
        data.money -= m.price;
        state.markDirty();
        giveOrDrop(buyer, m.stack.copy());
        buyer.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1f, 1f);
        Msg.send(buyer, Text.literal("Achat : " + m.stack.getCount() + "x ").formatted(Formatting.GREEN)
                .append(m.stack.getName()).append(" pour " + Bank.format(m.price) + "."));
    }

    private static void giveOrDrop(ServerPlayerEntity player, ItemStack stack) {
        if (!player.getInventory().insertStack(stack)) player.dropItem(stack, false);
    }

    private static ItemStack display(MarketItem m, boolean shop, boolean own) {
        ItemStack s = m.stack.copy();
        NbtCompound display = s.getOrCreateSubNbt("display");
        NbtList lore = display.getList("Lore", NbtElement.STRING_TYPE).copy();
        lore.add(line("Prix : " + Bank.format(m.price), Formatting.GOLD));
        if (!shop) lore.add(line("Vendeur : " + m.sellerName, Formatting.GRAY));
        lore.add(line(own ? "Clic : retirer de la vente" : "Clic : acheter", Formatting.YELLOW));
        display.put("Lore", lore);
        return s;
    }

    private static NbtString line(String text, Formatting color) {
        return NbtString.of(Text.Serializer.toJson(Text.literal(text).styled(st -> st.withItalic(false).withColor(color))));
    }

    private static ItemStack button(net.minecraft.item.Item item, String name, Formatting color) {
        ItemStack s = new ItemStack(item);
        s.setCustomName(Text.literal(name).styled(st -> st.withItalic(false).withColor(color)));
        return s;
    }
}
