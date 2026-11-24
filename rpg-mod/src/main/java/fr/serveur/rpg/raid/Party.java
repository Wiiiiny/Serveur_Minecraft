package fr.serveur.rpg.raid;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Groupes de raid (1 à 4 joueurs) : on entre ensemble au même point d'arrivée, et le coffre de corps
 * d'un membre est accessible aux autres. Les groupes ne survivent pas à un redémarrage.
 */
public final class Party {
    public static final int MAX = 4;
    private static final Map<UUID, List<UUID>> GROUPS = new HashMap<>(); // joueur -> liste partagée des membres
    private static final Map<UUID, UUID> INVITES = new HashMap<>();      // invité -> qui l'a invité

    private Party() {}

    public static List<UUID> members(UUID player) {
        List<UUID> g = GROUPS.get(player);
        return g == null ? List.of(player) : List.copyOf(g);
    }

    public static boolean sameGroup(UUID a, UUID b) {
        return a.equals(b) || members(a).contains(b);
    }

    public static String invite(ServerPlayerEntity from, ServerPlayerEntity to) {
        if (from == to) return "Tu ne peux pas t'inviter toi-même.";
        if (members(from.getUuid()).size() >= MAX) return "Ton groupe est complet (" + MAX + " joueurs).";
        if (GROUPS.containsKey(to.getUuid())) return to.getGameProfile().getName() + " est déjà dans un groupe.";
        INVITES.put(to.getUuid(), from.getUuid());
        return null;
    }

    public static String accept(ServerPlayerEntity p) {
        UUID from = INVITES.remove(p.getUuid());
        if (from == null) return "Aucune invitation en attente.";
        List<UUID> g = GROUPS.computeIfAbsent(from, u -> {
            List<UUID> l = new ArrayList<>();
            l.add(u);
            return l;
        });
        if (g.size() >= MAX) return "Ce groupe est complet.";
        leave(p.getUuid());
        g.add(p.getUuid());
        GROUPS.put(p.getUuid(), g);
        return null;
    }

    public static void leave(UUID player) {
        List<UUID> g = GROUPS.remove(player);
        if (g == null) return;
        g.remove(player);
        if (g.size() <= 1) g.forEach(GROUPS::remove);
    }
}
