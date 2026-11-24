package fr.serveur.rpg.data;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Un objet en vente (hôtel des ventes, ou boutique du GM si {@code seller} est null). */
public final class MarketItem {
    public int id;
    public UUID seller;
    public String sellerName = "";
    public ItemStack stack = ItemStack.EMPTY;
    /** Prix actuel (HDV : fixé par le vendeur ; boutique : prix de vente du serveur, recalculé selon le stock). */
    public long price;
    /** HDV : date de mise en vente (millisecondes), pour l'expiration. */
    public long listedAt;
    /** Boutique à prix dynamiques : prix de base, stock (en lots), stock cible, bornes, et rachat par le serveur. */
    public long basePrice, minPrice, maxPrice;
    public double stock, stockTarget;
    /** Le serveur vend cet article (achat par les joueurs) et/ou le rachète. */
    public boolean sold = true, buyback;

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putInt("id", id);
        if (seller != null) n.putUuid("seller", seller);
        n.putString("sellerName", sellerName);
        n.put("stack", stack.writeNbt(new NbtCompound()));
        n.putLong("price", price);
        n.putLong("listedAt", listedAt);
        n.putLong("basePrice", basePrice);
        n.putLong("minPrice", minPrice);
        n.putLong("maxPrice", maxPrice);
        n.putDouble("stock", stock);
        n.putDouble("stockTarget", stockTarget);
        n.putBoolean("sold", sold);
        n.putBoolean("buyback", buyback);
        return n;
    }

    public static MarketItem fromNbt(NbtCompound n) {
        MarketItem m = new MarketItem();
        m.id = n.getInt("id");
        if (n.containsUuid("seller")) m.seller = n.getUuid("seller");
        m.sellerName = n.getString("sellerName");
        m.stack = ItemStack.fromNbt(n.getCompound("stack"));
        m.price = n.getLong("price");
        m.listedAt = n.contains("listedAt") ? n.getLong("listedAt") : System.currentTimeMillis();
        // Articles d'avant les prix dynamiques : le prix fixé par le GM devient le prix de base.
        m.basePrice = n.contains("basePrice") ? n.getLong("basePrice") : m.price;
        m.minPrice = n.contains("minPrice") ? n.getLong("minPrice") : Math.max(1, m.basePrice / 2);
        m.maxPrice = n.contains("maxPrice") ? n.getLong("maxPrice") : Math.max(2, m.basePrice * 2);
        m.stockTarget = n.contains("stockTarget") ? n.getDouble("stockTarget") : 100;
        m.stock = n.contains("stock") ? n.getDouble("stock") : m.stockTarget;
        m.sold = !n.contains("sold") || n.getBoolean("sold");
        m.buyback = n.getBoolean("buyback");
        return m;
    }

    public static NbtList toNbtList(List<MarketItem> items) {
        NbtList list = new NbtList();
        items.forEach(i -> list.add(i.toNbt()));
        return list;
    }

    public static List<MarketItem> fromNbtList(NbtList list) {
        List<MarketItem> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            MarketItem m = fromNbt(list.getCompound(i));
            if (!m.stack.isEmpty()) out.add(m);
        }
        return out;
    }
}
