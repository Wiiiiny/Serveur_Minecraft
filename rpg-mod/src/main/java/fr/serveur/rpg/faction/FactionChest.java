package fr.serveur.rpg.faction;

import fr.serveur.rpg.sombre.Journal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Coffre commun d'une faction (54 cases), ouvert par tous les membres. À la fermeture, ce qui a été déposé ou retiré
 * pendant que le joueur l'avait ouvert est inscrit dans l'historique de la faction et le journal « factions ».
 */
public final class FactionChest extends SimpleInventory {
    private final Faction faction;
    private final Map<UUID, Map<Item, Integer>> opened = new HashMap<>();

    public FactionChest(Faction faction) {
        super(54);
        this.faction = faction;
    }

    private Map<Item, Integer> count() {
        Map<Item, Integer> m = new HashMap<>();
        for (int i = 0; i < size(); i++) {
            ItemStack s = getStack(i);
            if (!s.isEmpty()) m.merge(s.getItem(), s.getCount(), Integer::sum);
        }
        return m;
    }

    @Override
    public void onOpen(PlayerEntity player) {
        super.onOpen(player);
        if (!player.getWorld().isClient) opened.put(player.getUuid(), count());
    }

    @Override
    public void onClose(PlayerEntity player) {
        super.onClose(player);
        Map<Item, Integer> before = opened.remove(player.getUuid());
        if (before == null || !(player instanceof ServerPlayerEntity sp)) return;
        Map<Item, Integer> after = count();
        StringBuilder in = new StringBuilder(), out = new StringBuilder();
        java.util.Set<Item> all = new java.util.HashSet<>(before.keySet());
        all.addAll(after.keySet());
        for (Item it : all) {
            int d = after.getOrDefault(it, 0) - before.getOrDefault(it, 0);
            if (d == 0) continue;
            StringBuilder sb = d > 0 ? in : out;
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(Math.abs(d)).append("× ").append(new ItemStack(it).getName().getString());
            Journal.write(sp.getServer(), "factions", d > 0 ? "coffre_depot" : "coffre_retrait", "faction", faction.name,
                    "joueur", sp.getGameProfile().getName(), "objet", Registries.ITEM.getId(it).toString(), "quantite", Math.abs(d));
        }
        String name = sp.getGameProfile().getName();
        if (!in.isEmpty()) faction.log(name + " dépose au coffre : " + in);
        if (!out.isEmpty()) faction.log(name + " retire du coffre : " + out);
        FactionState.get(sp.getServer()).markDirty();
    }

    @Override
    public void markDirty() {
        super.markDirty();
    }
}
