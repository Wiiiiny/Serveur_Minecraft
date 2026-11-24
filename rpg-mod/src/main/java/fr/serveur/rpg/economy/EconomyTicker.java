package fr.serveur.rpg.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.serveur.rpg.Msg;
import fr.serveur.rpg.SurvieRpg;
import fr.serveur.rpg.data.MarketItem;
import fr.serveur.rpg.data.PlayerData;
import fr.serveur.rpg.data.RpgState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Tâches automatiques de l'économie (« zéro GM ») :
 * - chaque minute : stock de la boutique qui revient vers sa cible, annonces de l'HDV expirées rendues ;
 * - toutes les 15 min : sauvegarde des soldes (world/survierpg/economie/sauvegardes) ;
 * - toutes les heures : métriques (masse monétaire, médiane, flux par source) et régulateur.
 */
public final class EconomyTicker {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");
    private static long ticks, lastHour = System.currentTimeMillis(), lastBackup = System.currentTimeMillis();

    private EconomyTicker() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(EconomyTicker::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            ServerPlayerEntity p = handler.player;
            RpgState state = RpgState.get(server);
            PlayerData d = state.player(p);
            d.lastSeen = System.currentTimeMillis();
            if (!d.mailbox.isEmpty()) {
                for (NbtCompound n : d.mailbox) {
                    ItemStack s = ItemStack.fromNbt(n);
                    if (!p.getInventory().insertStack(s)) p.dropItem(s, false);
                }
                Msg.send(p, Text.literal(d.mailbox.size() + " objet(s) t'attendaient (annonces de l'HDV expirées) : ils sont dans ton inventaire.")
                        .formatted(Formatting.GOLD));
                d.mailbox.clear();
            }
            if (d.frozen) Msg.send(p, Text.literal("Ton compte est gelé : contacte un GM.").formatted(Formatting.RED));
            state.markDirty();
        }));
    }

    private static void tick(MinecraftServer server) {
        if (++ticks % 1200 != 0) return; // une fois par minute
        RpgState state = RpgState.get(server);
        long now = System.currentTimeMillis();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) state.player(p).lastSeen = now;
        DynamicShop.regenerate(state);
        expireAuctions(server, state, now);
        EconomyConfig.Data c = EconomyConfig.get();
        if (now - lastBackup >= c.sauvegardeMinutes * 60_000L) {
            lastBackup = now;
            backup(server, state);
        }
        if (now - lastHour >= 3_600_000L) {
            lastHour = now;
            hourly(server, state);
        }
        state.markDirty();
    }

    // ------------------------------------------------------------------ HDV

    private static void expireAuctions(MinecraftServer server, RpgState state, long now) {
        long max = EconomyConfig.get().dureeAnnonceJours * 86_400_000L;
        Iterator<MarketItem> it = state.auctions.iterator();
        while (it.hasNext()) {
            MarketItem m = it.next();
            if (m.listedAt <= 0 || now - m.listedAt < max) continue;
            it.remove();
            returnItem(server, state, m.seller, m.stack.copy(), "Ton annonce a expiré : " + m.stack.getCount() + "x " + m.stack.getName().getString() + " t'est rendu.");
        }
    }

    /** Rend un objet à un joueur : dans son inventaire s'il est là, sinon à sa prochaine connexion. */
    public static void returnItem(MinecraftServer server, RpgState state, UUID owner, ItemStack stack, String message) {
        if (owner == null) return;
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(owner);
        if (p != null) {
            if (!p.getInventory().insertStack(stack)) p.dropItem(stack, false);
            Msg.send(p, Text.literal(message).formatted(Formatting.GOLD));
        } else {
            PlayerData d = state.players.get(owner);
            if (d != null) {
                d.mailbox.add(stack.writeNbt(new NbtCompound()));
                d.pendingMessages.add(message);
            }
        }
        state.markDirty();
    }

    // ------------------------------------------------------------------ métriques et régulateur

    /** Masse monétaire, joueurs actifs, moyenne et médiane des soldes. */
    public record Snapshot(long supply, int active, long mean, long median) {}

    public static Snapshot snapshot(RpgState state) {
        long since = System.currentTimeMillis() - EconomyConfig.get().joueurActifJours * 86_400_000L;
        List<Long> balances = new ArrayList<>();
        long supply = 0;
        int active = 0;
        for (PlayerData d : state.players.values()) {
            supply += d.money;
            balances.add(d.money);
            if (d.lastSeen >= since) active++;
        }
        balances.sort(Comparator.naturalOrder());
        long median = balances.isEmpty() ? 0 : balances.get(balances.size() / 2);
        long mean = balances.isEmpty() ? 0 : supply / balances.size();
        return new Snapshot(supply, active, mean, median);
    }

    /** Mesure et régulation immédiates (/gm economie mesurer), en plus de celle de chaque heure. */
    public static void measureNow(MinecraftServer server) {
        lastHour = System.currentTimeMillis();
        hourly(server, RpgState.get(server));
    }

    static void hourly(MinecraftServer server, RpgState state) {
        Snapshot s = snapshot(state);
        Map<String, Long> flows = Economy.drainHourFlows();
        regulate(server, state, s);
        NbtCompound m = new NbtCompound();
        m.putString("t", LocalDateTime.now().withNano(0).toString());
        m.putLong("masse", s.supply());
        m.putInt("actifs", s.active());
        m.putLong("moyenne", s.mean());
        m.putLong("mediane", s.median());
        m.putDouble("gains", state.econGainMult);
        m.putDouble("taxes", state.econTaxMult);
        NbtCompound f = new NbtCompound();
        flows.forEach(f::putLong);
        m.put("flux", f);
        state.econMetrics.add(m);
        while (state.econMetrics.size() > 48) state.econMetrics.remove(0);
        state.markDirty();
        JsonObject o = new JsonObject();
        o.addProperty("t", m.getString("t"));
        o.addProperty("masse", s.supply());
        o.addProperty("actifs", s.active());
        o.addProperty("moyenne", s.mean());
        o.addProperty("mediane", s.median());
        o.addProperty("multiplicateurGains", state.econGainMult);
        o.addProperty("multiplicateurTaxes", state.econTaxMult);
        JsonObject fo = new JsonObject();
        flows.forEach(fo::addProperty);
        o.add("flux", fo);
        Path file = Ledger.dir(server).resolve("metriques.jsonl");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, new Gson().toJson(o) + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            SurvieRpg.LOGGER.error("Métriques de l'économie : écriture impossible", e);
        }
        SurvieRpg.LOGGER.info("[économie] masse {} pièces, {} actifs, médiane {}, gains ×{}, taxes ×{}",
                s.supply(), s.active(), s.median(), String.format("%.2f", state.econGainMult), String.format("%.2f", state.econTaxMult));
    }

    /** Inflation : les gains baissent et les taxes montent, par petits pas et dans des bornes. Déflation : l'inverse. */
    private static void regulate(MinecraftServer server, RpgState state, Snapshot s) {
        EconomyConfig.Data c = EconomyConfig.get();
        if (s.active() == 0) return;
        double perActive = (double) s.supply() / s.active();
        double target = c.masseCibleParJoueurActif;
        double step;
        if (perActive > target * (1 + c.regulateurTolerance)) step = -c.regulateurPas;
        else if (perActive < target * (1 - c.regulateurTolerance)) step = c.regulateurPas;
        else return;
        double gain = clamp(state.econGainMult + step, c.regulateurMin, c.regulateurMax);
        double tax = clamp(state.econTaxMult - step, c.regulateurMin, c.regulateurMax);
        if (gain == state.econGainMult && tax == state.econTaxMult) {
            if (step < 0) Economy.alert(server, "Inflation persistante : le régulateur est à sa limite (gains ×" + String.format("%.2f", gain) + ").");
            return;
        }
        state.econGainMult = Math.round(gain * 100) / 100.0;
        state.econTaxMult = Math.round(tax * 100) / 100.0;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    // ------------------------------------------------------------------ sauvegardes des soldes

    private static Path backupDir(MinecraftServer server) {
        return Ledger.dir(server).resolve("sauvegardes");
    }

    public static void backup(MinecraftServer server, RpgState state) {
        JsonObject o = new JsonObject();
        state.players.forEach((id, d) -> {
            JsonObject p = new JsonObject();
            p.addProperty("nom", d.name);
            p.addProperty("solde", d.money);
            o.add(id.toString(), p);
        });
        Path dir = backupDir(server);
        Path file = dir.resolve("soldes-" + LocalDateTime.now().format(FILE_TIME) + ".json");
        try {
            Files.createDirectories(dir);
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(o, w);
            }
            List<Path> all = backups(server);
            int keep = Math.max(1, EconomyConfig.get().sauvegardesGardees);
            for (int i = 0; i < all.size() - keep; i++) Files.deleteIfExists(all.get(i));
        } catch (IOException e) {
            SurvieRpg.LOGGER.error("Sauvegarde des soldes impossible", e);
        }
    }

    /** Sauvegardes existantes, de la plus ancienne à la plus récente. */
    public static List<Path> backups(MinecraftServer server) {
        Path dir = backupDir(server);
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().startsWith("soldes-")).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * Restaure les soldes d'une sauvegarde (nom de fichier sans extension, ex. soldes-2026-11-20_14-30),
     * pour un joueur ou pour tous (player == null). @return nombre de comptes modifiés, ou -1 si la sauvegarde est introuvable.
     */
    public static int restore(MinecraftServer server, String name, UUID player) {
        Path file = backupDir(server).resolve(name.endsWith(".json") ? name : name + ".json");
        if (!Files.exists(file)) return -1;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            RpgState state = RpgState.get(server);
            int n = 0;
            for (String key : o.keySet()) {
                UUID id = UUID.fromString(key);
                if (player != null && !player.equals(id)) continue;
                PlayerData d = state.players.get(id);
                long money = o.getAsJsonObject(key).get("solde").getAsLong();
                if (d == null || d.money == money) continue;
                Economy.restore(server, id, money, "sauvegarde " + name);
                n++;
            }
            return n;
        } catch (Exception e) {
            SurvieRpg.LOGGER.error("Restauration impossible ({})", file, e);
            return -1;
        }
    }
}
