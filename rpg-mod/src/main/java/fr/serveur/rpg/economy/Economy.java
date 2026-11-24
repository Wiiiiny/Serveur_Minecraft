package fr.serveur.rpg.economy;

import fr.serveur.rpg.Msg;
import fr.serveur.rpg.SurvieRpg;
import fr.serveur.rpg.data.PlayerData;
import fr.serveur.rpg.data.RpgState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Point de passage unique de l'argent en banque : chaque gain, dépense, virement ou geste du GM passe ici.
 * - gains (« robinets ») : multiplicateur du régulateur, plafond du jour par source avec rendement dégressif ;
 * - dépenses (« puits ») et virements : refusés si le compte est gelé ;
 * - tout est écrit dans le journal ({@link Ledger}) et compté dans les flux de l'heure ({@link EconomyTicker}) ;
 * - un gain anormal (bien au-dessus de la médiane) gèle le compte et prévient les GM.
 */
public final class Economy {
    /** Sources et destinations de l'argent (clé = nom dans le journal et dans economie.json). */
    public enum Source {
        METIER("metier", true), QUETE("quete", true), EVENEMENT("evenement", true), PRIME("prime", true),
        RACHAT("rachat", true), RAID("raid", true), COFFRE("coffre", true), NIVEAU("niveau", true),
        BOUTIQUE("boutique", false), FORGE("forge", false), CHANGEMENT_METIER("changement_metier", false),
        AMENDE("amende", false), HDV_ACHAT("hdv_achat", false), HDV_VENTE("hdv_vente", false),
        VIREMENT_ENVOYE("virement_envoye", false), VIREMENT_RECU("virement_recu", false),
        DEPOT("depot", false), RETRAIT("retrait", false), GM("gm", false), ANNULATION("annulation", false),
        RESTAURATION("restauration", false), FRAIS_RAID("frais_raid", false), RANCON("rancon", false),
        FACTION("faction", false);

        public final String key;
        /** Vrai pour les sources qui créent de l'argent (plafonnées, régulées, surveillées). */
        public final boolean faucet;

        Source(String key, boolean faucet) {
            this.key = key;
            this.faucet = faucet;
        }
    }

    /** Flux de l'heure en cours, par source (positif = entrée, négatif = sortie), remis à zéro par EconomyTicker. */
    static final Map<String, Long> HOUR_FLOWS = new HashMap<>();
    /** Gains « robinets » récents par joueur (instant, montant), pour la détection d'anomalie. */
    private static final Map<UUID, Deque<long[]>> RECENT_GAINS = new HashMap<>();

    private Economy() {}

    // ------------------------------------------------------------------ gains

    /**
     * Crédite un gain (« robinet ») : régulateur, plafond du jour et rendement dégressif, journal, anomalies.
     * Les fractions de pièce s'accumulent jusqu'à faire une pièce entière.
     * @return le nombre de pièces réellement versées maintenant.
     */
    public static long earn(MinecraftServer server, UUID id, Source source, double amount, String detail) {
        RpgState state = RpgState.get(server);
        PlayerData d = state.players.get(id);
        if (d == null || amount <= 0) return 0;
        d.rollDailyCaps();
        double raw = amount * (source.faucet ? state.econGainMult : 1.0);
        double already = d.earnedToday.getOrDefault(source.key, 0.0);
        double credited = diminish(already, raw, EconomyConfig.cap(source.key));
        d.earnedToday.put(source.key, already + raw);
        d.moneyFraction += credited;
        long whole = (long) Math.floor(d.moneyFraction + 1e-9);
        d.moneyFraction = Math.max(0, d.moneyFraction - whole);
        state.markDirty();
        if (whole <= 0) return 0;
        apply(server, state, id, d, source, whole, detail);
        if (source.faucet) checkAnomaly(server, state, id, d, whole);
        return whole;
    }

    /** Les métiers paient à chaque action : pièces par point d'XP de métier (plafond du jour « metier »). */
    public static long payJob(ServerPlayerEntity player, int xp) {
        if (xp <= 0) return 0;
        return earn(player.getServer(), player.getUuid(), Source.METIER, xp * EconomyConfig.get().metierPiecesParXp, "XP " + xp);
    }

    /** « · +2 pièces » à ajouter à la ligne d'XP (vide si rien n'a été payé). */
    public static Text paySuffix(long coins) {
        return coins > 0 ? Text.literal("  · +" + Bank.format(coins)).formatted(Formatting.GOLD) : Text.empty();
    }

    /** Rendement dégressif : 100 % jusqu'au plafond, puis réduit jusqu'au double, puis très réduit. */
    static double diminish(double already, double amount, double cap) {
        if (cap <= 0) return amount;
        EconomyConfig.Data c = EconomyConfig.get();
        double[] bounds = {cap, 2 * cap, Double.MAX_VALUE};
        double[] rates = {1.0, c.rendementApresPlafond, c.rendementApresDouble};
        double out = 0, from = already, left = amount;
        for (int i = 0; i < 3 && left > 0; i++) {
            if (from >= bounds[i]) continue;
            double part = Math.min(left, bounds[i] - from);
            out += part * rates[i];
            from += part;
            left -= part;
        }
        return out;
    }

    /** Ce que ce joueur a déjà gagné aujourd'hui avec cette source, et son plafond (« 40 / 150 »). */
    public static String capInfo(PlayerData d, Source source) {
        d.rollDailyCaps();
        int cap = EconomyConfig.cap(source.key);
        long done = Math.round(d.earnedToday.getOrDefault(source.key, 0.0));
        return cap <= 0 ? String.valueOf(done) : done + " / " + cap;
    }

    // ------------------------------------------------------------------ dépenses, virements, GM

    /** Dépense : null si c'est fait, sinon la raison du refus (solde insuffisant, compte gelé). */
    public static String spend(MinecraftServer server, UUID id, Source source, long amount, String detail) {
        RpgState state = RpgState.get(server);
        PlayerData d = state.players.get(id);
        if (d == null) return "Compte inconnu.";
        if (amount <= 0) return null;
        if (d.frozen) return "Ton compte est gelé : contacte un GM.";
        if (d.money < amount) return "Solde insuffisant (" + Bank.format(d.money) + ").";
        apply(server, state, id, d, source, -amount, detail);
        return null;
    }

    /** Amende : retire au plus ce qu'il y a sur le compte (même gelé). @return le montant retiré. */
    public static long fine(MinecraftServer server, UUID id, Source source, long amount, String detail) {
        RpgState state = RpgState.get(server);
        PlayerData d = state.players.get(id);
        if (d == null) return 0;
        long taken = Math.min(d.money, Math.max(0, amount));
        if (taken > 0) apply(server, state, id, d, source, -taken, detail);
        return taken;
    }

    /** Versement qui ne crée pas d'argent (vente à l'HDV, virement reçu, remboursement...). */
    public static void credit(MinecraftServer server, UUID id, Source source, long amount, String detail) {
        RpgState state = RpgState.get(server);
        PlayerData d = state.players.get(id);
        if (d == null || amount <= 0) return;
        apply(server, state, id, d, source, amount, detail);
    }

    /** Virement entre joueurs : plafond du jour, délai pour les nouveaux comptes, comptes gelés. */
    public static String transfer(MinecraftServer server, UUID from, UUID to, long amount) {
        RpgState state = RpgState.get(server);
        PlayerData a = state.players.get(from), b = state.players.get(to);
        if (a == null || b == null) return "Joueur inconnu.";
        if (from.equals(to)) return "Tu ne peux pas te virer de l'argent à toi-même.";
        if (amount <= 0) return "Montant invalide.";
        EconomyConfig.Data c = EconomyConfig.get();
        long hours = (System.currentTimeMillis() - a.firstSeen) / 3_600_000L;
        if (hours < c.virementDelaiNouveauHeures) {
            return "Les virements sont possibles " + c.virementDelaiNouveauHeures + " h après ta première connexion (encore "
                    + (c.virementDelaiNouveauHeures - hours) + " h).";
        }
        if (b.frozen) return "Le compte de " + b.name + " est gelé.";
        a.rollDailyCaps();
        if (!c.virementsIllimites && a.transferredToday + amount > c.virementPlafondJour) {
            return "Plafond des virements : " + Bank.format(c.virementPlafondJour) + " par jour (reste "
                    + Bank.format(Math.max(0, c.virementPlafondJour - a.transferredToday)) + ").";
        }
        String e = spend(server, from, Source.VIREMENT_ENVOYE, amount, "à " + b.name);
        if (e != null) return e;
        a.transferredToday += amount;
        credit(server, to, Source.VIREMENT_RECU, amount, "de " + a.name);
        return null;
    }

    /** Geste du GM : ajoute (delta) ou fixe (absolute) un solde. Toujours journalisé. */
    public static void admin(MinecraftServer server, UUID id, long value, boolean absolute, String gm) {
        RpgState state = RpgState.get(server);
        PlayerData d = state.players.get(id);
        if (d == null) return;
        long target = Math.max(0, absolute ? value : d.money + value);
        if (target != d.money) apply(server, state, id, d, Source.GM, target - d.money, "par " + gm);
    }

    /** Annule une transaction du journal (mouvement inverse, journalisé à son tour). */
    public static void reverse(MinecraftServer server, Ledger.Entry e, String gm) {
        RpgState state = RpgState.get(server);
        PlayerData d = state.players.get(e.player());
        if (d == null) return;
        long delta = -e.amount();
        if (delta < 0) delta = -Math.min(d.money, -delta); // on ne descend pas sous zéro
        if (delta != 0) apply(server, state, e.player(), d, Source.ANNULATION, delta, "transaction #" + e.id() + " par " + gm);
    }

    /** Remet un solde à une valeur de sauvegarde (journalisé). */
    public static void restore(MinecraftServer server, UUID id, long money, String what) {
        RpgState state = RpgState.get(server);
        PlayerData d = state.players.get(id);
        if (d == null || d.money == money) return;
        apply(server, state, id, d, Source.RESTAURATION, money - d.money, what);
    }

    private static void apply(MinecraftServer server, RpgState state, UUID id, PlayerData d, Source source, long delta, String detail) {
        long before = d.money;
        d.money = Math.max(0, d.money + delta);
        state.markDirty();
        HOUR_FLOWS.merge(source.key, delta, Long::sum);
        Ledger.write(server, new Ledger.Entry(state.nextTxId++, LocalDateTime.now(), id, d.name, source.key, d.money - before, before, d.money, detail));
    }

    // ------------------------------------------------------------------ anomalies

    private static void checkAnomaly(MinecraftServer server, RpgState state, UUID id, PlayerData d, long amount) {
        long now = System.currentTimeMillis();
        RECENT_GAINS.computeIfAbsent(id, u -> new ArrayDeque<>()).addLast(new long[]{now, amount});
        Map<UUID, Long> hour = new HashMap<>();
        RECENT_GAINS.forEach((u, q) -> {
            while (!q.isEmpty() && now - q.peekFirst()[0] > 3_600_000L) q.pollFirst();
            long sum = 0;
            for (long[] g : q) sum += g[1];
            if (sum > 0) hour.put(u, sum);
        });
        RECENT_GAINS.values().removeIf(Deque::isEmpty);
        if (d.frozen || hour.size() < 3) return; // il faut quelques joueurs pour que la médiane veuille dire quelque chose
        List<Long> values = new ArrayList<>(hour.values());
        values.sort(Long::compare);
        long median = values.get(values.size() / 2);
        long mine = hour.getOrDefault(id, 0L);
        EconomyConfig.Data c = EconomyConfig.get();
        if (mine >= c.anomalieGainMinimum && mine > c.anomalieFacteurMediane * Math.max(1, median)) {
            d.frozen = true;
            state.markDirty();
            alert(server, "Compte de " + d.name + " gelé : " + mine + " pièces gagnées en 1 h (médiane " + median + "). /gm economie degeler " + d.name);
        }
    }

    /** Alerte pour un humain : journal du serveur, GM connectés, et liste affichée dans le menu GM. */
    public static void alert(MinecraftServer server, String message) {
        RpgState state = RpgState.get(server);
        String line = LocalDateTime.now().withNano(0).toString().replace('T', ' ') + " — " + message;
        state.econAlerts.add(line);
        while (state.econAlerts.size() > 20) state.econAlerts.remove(0);
        state.markDirty();
        SurvieRpg.LOGGER.warn("[économie] {}", message);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.hasPermissionLevel(2)) Msg.send(p, Text.literal("⚠ " + message).formatted(Formatting.RED));
        }
    }

    /** Pour EconomyTicker : flux de l'heure écoulée (copie), puis remise à zéro. */
    static Map<String, Long> drainHourFlows() {
        Map<String, Long> copy = new HashMap<>(HOUR_FLOWS);
        HOUR_FLOWS.clear();
        return copy;
    }
}
