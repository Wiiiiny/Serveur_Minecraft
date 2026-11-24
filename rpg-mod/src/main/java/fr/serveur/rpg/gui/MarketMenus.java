package fr.serveur.rpg.gui;

import fr.serveur.rpg.Msg;
import fr.serveur.rpg.data.MarketItem;
import fr.serveur.rpg.data.PlayerData;
import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.economy.Bank;
import fr.serveur.rpg.economy.DynamicShop;
import fr.serveur.rpg.economy.Economy;
import fr.serveur.rpg.economy.EconomyConfig;
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
            boolean hidden = shop && m.sold && fr.serveur.rpg.sombre.Decouvertes.canBuy(state.player(player), m) != null;
            inv.setStack(i, hidden ? button(Items.GRAY_STAINED_GLASS_PANE, "Objet inconnu — à découvrir", Formatting.DARK_GRAY)
                    : display(m, shop, player.getUuid().equals(m.seller)));
        }
        ItemStack filler = button(Items.BLACK_STAINED_GLASS_PANE, " ", Formatting.BLACK);
        for (int i = 45; i < 54; i++) inv.setStack(i, filler.copy());
        if (p > 0) inv.setStack(SLOT_PREV, button(Items.ARROW, "Page précédente", Formatting.YELLOW));
        if (p < pages - 1) inv.setStack(SLOT_NEXT, button(Items.ARROW, "Page suivante", Formatting.YELLOW));
        PlayerData me = state.player(player);
        inv.setStack(SLOT_INFO, button(fr.serveur.rpg.items.ModItems.PIECE_OR, "Ton solde : " + Bank.format(me.money), Formatting.GOLD));

        Text title = Text.literal((shop ? "Boutique du royaume" : "Hôtel des ventes") + " — " + (p + 1) + "/" + pages);
        ChestMenu.open(player, title, inv, (pl, slot, type, button) -> {
            if (slot == SLOT_PREV && p > 0) reopen(pl, p - 1, shop);
            else if (slot == SLOT_NEXT && p < pages - 1) reopen(pl, p + 1, shop);
            else if (slot < PER_PAGE && ids[slot] != 0 && type == SlotActionType.PICKUP) {
                if (shop && button == 1) sellToShop(pl, ids[slot]);
                else if (shop) buyFromShop(pl, ids[slot]);
                else clickAuction(pl, ids[slot]);
                reopen(pl, p, shop);
            }
        });
    }

    private static void reopen(ServerPlayerEntity player, int page, boolean shop) {
        player.getServer().execute(() -> open(player, page, shop));
    }

    /** Résultat d'un achat, affiché dans le chat et dans l'interface (touche B). */
    public record Result(boolean ok, String message) {}

    private static Result reply(ServerPlayerEntity player, boolean ok, String message) {
        Msg.send(player, Text.literal(message).formatted(ok ? Formatting.GREEN : Formatting.RED));
        return new Result(ok, message);
    }

    /** Clic sur une annonce : achat, ou retrait si c'est la sienne. */
    public static Result clickAuction(ServerPlayerEntity buyer, int id) {
        RpgState state = RpgState.get(buyer.getServer());
        MarketItem m = state.auctions.stream().filter(a -> a.id == id).findFirst().orElse(null);
        if (m == null) return reply(buyer, false, "Cet objet n'est plus en vente.");
        if (buyer.getUuid().equals(m.seller)) {
            state.auctions.remove(m);
            state.markDirty();
            giveOrDrop(buyer, m.stack.copy());
            return reply(buyer, true, "Objet retiré de la vente.");
        }
        PlayerData data = state.player(buyer);
        if (data.money < m.price) return reply(buyer, false, "Solde insuffisant (" + Bank.format(data.money) + "). Dépose des pièces à la banque.");
        String what = m.stack.getCount() + "x " + m.stack.getName().getString();
        String error = Economy.spend(buyer.getServer(), buyer.getUuid(), Economy.Source.HDV_ACHAT, m.price, what + " de " + m.sellerName);
        if (error != null) return reply(buyer, false, error);
        state.auctions.remove(m);
        state.markDirty();
        // Taxe de l'HDV (puits d'argent), ajustée par le régulateur.
        long tax = Math.round(m.price * EconomyConfig.get().taxeHdv * state.econTaxMult);
        long net = Math.max(0, m.price - tax);
        Economy.credit(buyer.getServer(), m.seller, Economy.Source.HDV_VENTE, net, what + " à " + buyer.getGameProfile().getName() + ", taxe " + tax);
        giveOrDrop(buyer, m.stack.copy());
        buyer.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1f, 1f);
        Bank.notify(buyer.getServer(), m.seller, "Hôtel des ventes : " + buyer.getGameProfile().getName() + " a acheté "
                + what + " — +" + Bank.format(net) + " sur ton compte (taxe " + Bank.format(tax) + ").");
        return reply(buyer, true, "Achat : " + m.stack.getCount() + "x " + m.stack.getName().getString() + " pour " + Bank.format(m.price) + ".");
    }

    public static Result buyFromShop(ServerPlayerEntity buyer, int id) {
        RpgState state = RpgState.get(buyer.getServer());
        MarketItem m = state.shop.stream().filter(a -> a.id == id).findFirst().orElse(null);
        if (m == null) return reply(buyer, false, "Cet article n'existe plus.");
        String unknown = m.sold ? fr.serveur.rpg.sombre.Decouvertes.canBuy(state.player(buyer), m) : null;
        if (unknown != null) return reply(buyer, false, unknown);
        long price = DynamicShop.buyPrice(m);
        String error = DynamicShop.buy(buyer, m);
        if (error != null) return reply(buyer, false, error);
        buyer.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1f, 1f);
        return reply(buyer, true, "Achat : " + m.stack.getCount() + "x " + m.stack.getName().getString() + " pour " + Bank.format(price) + ".");
    }

    /** Revente d'un lot au serveur (articles marqués « rachat »). */
    public static Result sellToShop(ServerPlayerEntity seller, int id) {
        RpgState state = RpgState.get(seller.getServer());
        MarketItem m = state.shop.stream().filter(a -> a.id == id).findFirst().orElse(null);
        if (m == null) return reply(seller, false, "Cet article n'existe plus.");
        long[] paid = {0};
        String error = DynamicShop.sell(seller, m, paid);
        if (error != null) return reply(seller, false, error);
        seller.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1f, 0.8f);
        return reply(seller, true, "Vendu : " + m.stack.getCount() + "x " + m.stack.getName().getString() + " pour " + Bank.format(paid[0])
                + (paid[0] == 0 ? " (plafond du jour atteint)" : "") + ".");
    }

    private static void giveOrDrop(ServerPlayerEntity player, ItemStack stack) {
        if (!player.getInventory().insertStack(stack)) player.dropItem(stack, false);
    }

    private static ItemStack display(MarketItem m, boolean shop, boolean own) {
        ItemStack s = m.stack.copy();
        NbtCompound display = s.getOrCreateSubNbt("display");
        NbtList lore = display.getList("Lore", NbtElement.STRING_TYPE).copy();
        if (shop) {
            if (m.sold) lore.add(line("Prix : " + Bank.format(DynamicShop.buyPrice(m)) + " — clic gauche : acheter", Formatting.GOLD));
            if (m.buyback) lore.add(line("Rachat : " + Bank.format(DynamicShop.sellPrice(m)) + " — clic droit : vendre", Formatting.YELLOW));
            lore.add(line("Prix qui varient avec l'offre et la demande", Formatting.DARK_GRAY));
        } else {
            lore.add(line("Prix : " + Bank.format(m.price), Formatting.GOLD));
            lore.add(line("Vendeur : " + m.sellerName, Formatting.GRAY));
            lore.add(line(own ? "Clic : retirer de la vente" : "Clic : acheter", Formatting.YELLOW));
        }
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
