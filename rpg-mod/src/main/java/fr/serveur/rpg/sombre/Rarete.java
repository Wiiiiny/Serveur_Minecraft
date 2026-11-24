package fr.serveur.rpg.sombre;

import fr.serveur.rpg.SurvieRpg;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Raretés des objets et couleur de leur contour : Peu commun (gris), Commun (vert), Rare (bleu), Épique (violet),
 * Exotique (noir, avec des particules jaunes). La table vient de objets_rares.json (envoyée aux joueurs à la connexion) ;
 * un objet peut aussi porter sa rareté dans ses données (survierpg_rarete), et les exotiques sont reconnus par survierpg_exo.
 */
public enum Rarete {
    PEU_COMMUN("Peu commun", 0xFF9A9A9A, Formatting.GRAY),
    COMMUN("Commun", 0xFF58D058, Formatting.GREEN),
    RARE("Rare", 0xFF4A8CFF, Formatting.BLUE),
    EPIQUE("Épique", 0xFFB25CFF, Formatting.LIGHT_PURPLE),
    EXOTIQUE("Exotique", 0xFF000000, Formatting.YELLOW);

    public static final String NBT = "survierpg_rarete";
    public static final String EXO_NBT = "survierpg_exo";

    public final String nom;
    /** Couleur du contour (ARGB). */
    public final int color;
    public final Formatting text;

    Rarete(String nom, int color, Formatting text) {
        this.nom = nom;
        this.color = color;
        this.text = text;
    }

    public static Rarete parse(String s) {
        if (s == null) return null;
        for (Rarete r : values()) if (r.name().equalsIgnoreCase(s) || r.nom.equalsIgnoreCase(s)) return r;
        return null;
    }

    /** Table du serveur (construite depuis la config). */
    private static Map<Item, Rarete> serverTable = new HashMap<>();

    public static Map<Item, Rarete> serverTable() {
        return serverTable;
    }

    /** Recalcule la table depuis objets_rares.json (au démarrage et à /gm recharger). */
    public static void resolve() {
        serverTable = table(SombreConfig.objets().raretes);
    }

    public static Map<Item, Rarete> table(Map<String, String> raw) {
        Map<Item, Rarete> out = new HashMap<>();
        raw.forEach((id, r) -> {
            Identifier ident = Identifier.tryParse(id);
            Rarete rar = parse(r);
            if (ident == null || rar == null) {
                SurvieRpg.LOGGER.warn("objets_rares.json : entrée ignorée {} = {}", id, r);
                return;
            }
            Item item = Registries.ITEM.get(ident);
            if (item != Items.AIR) out.put(item, rar);
        });
        return out;
    }

    public static boolean isExotic(ItemStack stack) {
        NbtCompound n = stack.getNbt();
        return n != null && n.contains(EXO_NBT, NbtElement.COMPOUND_TYPE);
    }

    /** Rareté d'un objet (null = pas de rareté, pas de contour). */
    public static Rarete of(ItemStack stack, Map<Item, Rarete> table) {
        if (stack.isEmpty()) return null;
        NbtCompound n = stack.getNbt();
        if (n != null) {
            if (n.contains(EXO_NBT, NbtElement.COMPOUND_TYPE)) return EXOTIQUE;
            if (n.contains(NBT, NbtElement.STRING_TYPE)) {
                Rarete r = parse(n.getString(NBT));
                if (r != null) return r;
            }
        }
        return table.get(stack.getItem());
    }
}
