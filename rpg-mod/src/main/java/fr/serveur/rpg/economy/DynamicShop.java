package fr.serveur.rpg.economy;

import fr.serveur.rpg.data.MarketItem;
import fr.serveur.rpg.data.RpgState;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Boutique du serveur à prix dynamiques (offre et demande). Chaque article a un prix de base, un stock cible
 * et des bornes : prix = clamp(base × (stock_cible / stock)^0,25, min, max). Acheter fait baisser le stock (le prix monte),
 * revendre au serveur le fait monter (le prix baisse), et le stock revient doucement vers sa cible.
 * Le serveur rachète {@code ecartAchatVente} moins cher qu'il ne vend : c'est un puits d'argent permanent.
 * Une transaction ne peut pas faire varier le prix de plus de 10 %.
 */
public final class DynamicShop {
    private DynamicShop() {}

    /** Prix « théorique » pour un stock donné (non arrondi, non borné). */
    private static double rawPrice(MarketItem m, double stock) {
        return m.basePrice * Math.pow(Math.max(1e-3, m.stockTarget) / Math.max(1, stock), 0.25);
    }

    /** Prix de vente du serveur (ce que paie le joueur), arrondi et borné. */
    public static long buyPrice(MarketItem m) {
        double p = Math.max(m.minPrice, Math.min(m.maxPrice, rawPrice(m, m.stock)));
        return Math.max(1, Math.round(p));
    }

    /** Prix de rachat par le serveur (ce que reçoit le joueur). */
    public static long sellPrice(MarketItem m) {
        return Math.max(1, (long) Math.floor(buyPrice(m) / (1 + EconomyConfig.get().ecartAchatVente)));
    }

    /** Recalcule le prix affiché après un changement de stock ou de réglage. */
    public static void refresh(MarketItem m) {
        m.price = buyPrice(m);
    }

    /** Change le stock de {@code delta} lots, sans que le prix varie de plus que la limite par transaction. */
    private static void moveStock(MarketItem m, double delta) {
        double before = rawPrice(m, m.stock);
        double limit = EconomyConfig.get().variationPrixMaxParTransaction;
        double after = Math.max(0.5, m.stock + delta);
        double p = rawPrice(m, after);
        if (p > before * (1 + limit)) after = stockFor(m, before * (1 + limit));
        else if (p < before * (1 - limit)) after = stockFor(m, before * (1 - limit));
        m.stock = after;
        refresh(m);
    }

    /** Stock qui donne ce prix : inverse de la formule. */
    private static double stockFor(MarketItem m, double price) {
        return m.stockTarget / Math.pow(price / Math.max(1, m.basePrice), 4);
    }

    /** Le stock revient vers sa cible ({@code retourStockParHeure} de l'écart par heure), appelé chaque minute. */
    public static void regenerate(RpgState state) {
        double rate = EconomyConfig.get().retourStockParHeure / 60.0;
        for (MarketItem m : state.shop) {
            m.stock += (m.stockTarget - m.stock) * rate;
            refresh(m);
        }
    }

    /** Valeur de référence d'un objet (prix de base unitaire dans la boutique), ou -1 s'il n'y est pas. */
    public static double referenceUnitPrice(RpgState state, ItemStack stack) {
        for (MarketItem m : state.shop) {
            if (ItemStack.canCombine(m.stack, stack) && m.stack.getCount() > 0) return (double) m.basePrice / m.stack.getCount();
        }
        return -1;
    }

    // ------------------------------------------------------------------ achats et ventes

    /** Le joueur achète un lot au serveur. @return null si c'est fait, sinon la raison. */
    public static String buy(ServerPlayerEntity player, MarketItem m) {
        if (!m.sold) return "Le serveur ne vend pas cet article.";
        long price = buyPrice(m);
        String e = Economy.spend(player.getServer(), player.getUuid(), Economy.Source.BOUTIQUE, price,
                m.stack.getCount() + "x " + m.stack.getName().getString());
        if (e != null) return e;
        moveStock(m, -1);
        RpgState.get(player.getServer()).markDirty();
        ItemStack s = m.stack.copy();
        if (!player.getInventory().insertStack(s)) player.dropItem(s, false);
        return null;
    }

    /** Le joueur revend un lot au serveur (il doit l'avoir sur lui). @return null si c'est fait (montant versé dans {@code received[0]}), sinon la raison. */
    public static String sell(ServerPlayerEntity player, MarketItem m, long[] received) {
        if (!m.buyback) return "Le serveur ne rachète pas cet article.";
        int lot = m.stack.getCount();
        if (count(player.getInventory(), m.stack) < lot) return "Il te faut " + lot + "x " + m.stack.getName().getString() + " sur toi.";
        long price = sellPrice(m);
        remove(player.getInventory(), m.stack, lot);
        long paid = Economy.earn(player.getServer(), player.getUuid(), Economy.Source.RACHAT, price,
                lot + "x " + m.stack.getName().getString());
        moveStock(m, +1);
        RpgState.get(player.getServer()).markDirty();
        received[0] = paid;
        return null;
    }

    private static int count(PlayerInventory inv, ItemStack like) {
        int n = 0;
        for (int i = 0; i < inv.size(); i++) if (ItemStack.canCombine(inv.getStack(i), like)) n += inv.getStack(i).getCount();
        return n;
    }

    private static void remove(PlayerInventory inv, ItemStack like, int amount) {
        for (int i = 0; i < inv.size() && amount > 0; i++) {
            ItemStack s = inv.getStack(i);
            if (!ItemStack.canCombine(s, like)) continue;
            int take = Math.min(amount, s.getCount());
            s.decrement(take);
            amount -= take;
        }
    }

    /** Nouvel article : bornes et stock par défaut autour du prix de base. */
    public static MarketItem create(RpgState state, ItemStack stack, long basePrice, boolean sold, boolean buyback) {
        MarketItem m = new MarketItem();
        m.id = state.nextMarketId++;
        m.sellerName = "Serveur";
        m.stack = stack.copy();
        m.basePrice = Math.max(1, basePrice);
        m.minPrice = Math.max(1, basePrice / 2);
        m.maxPrice = Math.max(2, basePrice * 2);
        m.stockTarget = 100;
        m.stock = 100;
        m.sold = sold;
        m.buyback = buyback;
        refresh(m);
        return m;
    }

    /** Articles ajoutés par les mises à jour du mod (une seule fois par monde). */
    public static void seed(RpgState state) {
        if (state.shopSeedVersion >= 1) return;
        Object[][] fish = {{Items.COD, 16, 6}, {Items.SALMON, 16, 8}, {Items.TROPICAL_FISH, 4, 6}, {Items.PUFFERFISH, 4, 5}};
        for (Object[] f : fish) state.shop.add(create(state, new ItemStack((Item) f[0], (int) f[1]), (int) f[2], false, true));
        state.shopSeedVersion = 1;
        state.markDirty();
    }

    /** Version 2 : butin du raid racheté par le serveur, balise de retour et Brise-statue vendus. */
    public static void seedRaid(RpgState state) {
        if (state.shopSeedVersion >= 2) return;
        state.shop.add(create(state, new ItemStack(fr.serveur.rpg.items.RaidItems.BALISE_RETOUR), 40, true, false));
        state.shop.add(create(state, new ItemStack(fr.serveur.rpg.items.RaidItems.BRISE_STATUE), 250, true, false));
        state.shop.add(create(state, new ItemStack(fr.serveur.rpg.items.RaidItems.RELIQUE_ARDENTE), 15, false, true));
        state.shop.add(create(state, new ItemStack(fr.serveur.rpg.items.RaidItems.IDOLE_BASALTE), 45, false, true));
        state.shop.add(create(state, new ItemStack(fr.serveur.rpg.items.RaidItems.COURONNE_DECHUE), 160, false, true));
        state.shop.add(create(state, new ItemStack(fr.serveur.rpg.items.RaidItems.FRAGMENT_TENEBRES), 20, false, true));
        state.shopSeedVersion = 2;
        state.markDirty();
    }
}
