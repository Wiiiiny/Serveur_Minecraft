package fr.serveur.rpg.faction;

import net.minecraft.block.entity.BannerPattern;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Bannières de faction (serveur et client) : motifs disponibles, objet bannière correspondant. */
public final class Banners {
    public static final int MAX_LAYERS = 6;

    private Banners() {}

    /** Codes des motifs (« bs », « cr »...), sans le fond. */
    public static List<String> patterns() {
        List<String> out = new ArrayList<>();
        for (BannerPattern p : Registries.BANNER_PATTERN) {
            String code = p.getId();
            if (!"b".equals(code)) out.add(code);
        }
        return out;
    }

    public static boolean validPattern(String code) {
        return patterns().contains(code);
    }

    public static Item bannerItem(DyeColor color) {
        Item it = Registries.ITEM.get(new Identifier(color.getName() + "_banner"));
        return it == Items.AIR ? Items.WHITE_BANNER : it;
    }

    public static ItemStack stack(int base, List<Faction.Layer> layers) {
        ItemStack st = new ItemStack(bannerItem(DyeColor.byId(base)));
        if (!layers.isEmpty()) {
            NbtList list = new NbtList();
            for (Faction.Layer l : layers) {
                NbtCompound c = new NbtCompound();
                c.putString("Pattern", l.pattern);
                c.putInt("Color", l.color);
                list.add(c);
            }
            st.getOrCreateSubNbt("BlockEntityTag").put("Patterns", list);
        }
        return st;
    }

    public static ItemStack stack(Faction f) {
        return stack(f.bannerBase, f.layers);
    }

    /** Lit une bannière tenue en main : couleur de fond et motifs. @return null si ce n'est pas une bannière. */
    public static Faction readHeld(ItemStack st) {
        if (!(st.getItem() instanceof net.minecraft.item.BannerItem bi)) return null;
        Faction f = new Faction();
        f.bannerBase = bi.getColor().getId();
        NbtCompound tag = st.getSubNbt("BlockEntityTag");
        if (tag != null) {
            NbtList list = tag.getList("Patterns", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size() && f.layers.size() < MAX_LAYERS; i++) {
                String code = list.getCompound(i).getString("Pattern");
                if (validPattern(code)) f.layers.add(new Faction.Layer(code, list.getCompound(i).getInt("Color")));
            }
        }
        return f;
    }

    /** Couleur ARGB de la faction sur la carte. */
    public static int mapColor(int base) {
        return 0xFF000000 | DyeColor.byId(base).getFireworkColor();
    }
}
