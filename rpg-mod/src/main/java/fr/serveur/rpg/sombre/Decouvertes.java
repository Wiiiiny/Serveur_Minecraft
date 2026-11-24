package fr.serveur.rpg.sombre;

import fr.serveur.rpg.Msg;
import fr.serveur.rpg.data.MarketItem;
import fr.serveur.rpg.data.PlayerData;
import fr.serveur.rpg.data.RpgState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Boutique de découverte : un objet ne s'achète à la boutique qu'une fois ramassé au moins une fois (présent dans
 * l'inventaire du joueur). Les objets inconnus y sont masqués. Les objets exotiques ne s'y achètent jamais.
 * Quelques objets de base (lanterne, torches, pain...) sont toujours connus (monde_sombre.json, decouverte.toujoursConnus).
 */
public final class Decouvertes {
    private Decouvertes() {}

    public static String key(Item item) {
        return Registries.ITEM.getId(item).toString();
    }

    public static boolean known(PlayerData d, ItemStack stack) {
        String k = key(stack.getItem());
        return d.discovered.contains(k) || SombreConfig.get().decouverte.toujoursConnus.contains(k);
    }

    /** Peut-on acheter cet article de la boutique ? null si oui, sinon la raison. */
    public static String canBuy(PlayerData d, MarketItem m) {
        if (Rarete.isExotic(m.stack)) return "Les objets exotiques ne s'achètent pas : ils se trouvent.";
        if (!known(d, m.stack)) return "Objet inconnu : ramasse-le une première fois pour pouvoir l'acheter.";
        return null;
    }

    /** Articles vendus par la boutique : connus / total (pour l'affichage). */
    public static int[] progress(RpgState state, PlayerData d) {
        int known = 0, total = 0;
        for (MarketItem m : state.shop) {
            if (!m.sold || Rarete.isExotic(m.stack)) continue;
            total++;
            if (known(d, m.stack)) known++;
        }
        return new int[]{known, total};
    }

    /** Note les objets de l'inventaire comme découverts (appelé toutes les 2 secondes). */
    public static void scan(ServerPlayerEntity p, PlayerData d, RpgState state) {
        List<ItemStack> unlocked = new ArrayList<>();
        int fresh = 0;
        for (int i = 0; i < p.getInventory().size(); i++) {
            ItemStack st = p.getInventory().getStack(i);
            if (st.isEmpty()) continue;
            if (!d.discovered.add(key(st.getItem()))) continue;
            fresh++;
            for (MarketItem m : state.shop) {
                if (m.sold && m.stack.isOf(st.getItem()) && !Rarete.isExotic(m.stack)
                        && !SombreConfig.get().decouverte.toujoursConnus.contains(key(st.getItem()))) {
                    unlocked.add(m.stack);
                    break;
                }
            }
        }
        if (fresh == 0) return;
        state.markDirty();
        Personnage.addXp(p, fresh * (double) SombreConfig.get().personnage.xpDecouverte, null);
        if (!unlocked.isEmpty()) {
            MutableText t = Text.literal("Découverte : ").formatted(Formatting.AQUA);
            for (int i = 0; i < unlocked.size(); i++) {
                if (i > 0) t.append(Text.literal(", ").formatted(Formatting.GRAY));
                t.append(unlocked.get(i).getName().copy().formatted(Formatting.WHITE));
            }
            Msg.send(p, t.append(Text.literal(" — désormais en vente à la boutique.").formatted(Formatting.GRAY)));
        }
    }
}
