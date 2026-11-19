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
    public long price;

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putInt("id", id);
        if (seller != null) n.putUuid("seller", seller);
        n.putString("sellerName", sellerName);
        n.put("stack", stack.writeNbt(new NbtCompound()));
        n.putLong("price", price);
        return n;
    }

    public static MarketItem fromNbt(NbtCompound n) {
        MarketItem m = new MarketItem();
        m.id = n.getInt("id");
        if (n.containsUuid("seller")) m.seller = n.getUuid("seller");
        m.sellerName = n.getString("sellerName");
        m.stack = ItemStack.fromNbt(n.getCompound("stack"));
        m.price = n.getLong("price");
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
