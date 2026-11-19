package fr.serveur.rpg.events;

import fr.serveur.rpg.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Événements organisés par le GM, avec barre de temps et classement final.
 * - combat : 3 points par joueur tué, 1 par créature ;
 * - recolte : 1 point par bloc récolté ;
 * - ressource : 1 point par Pierre obscure récoltée ;
 * - special : points donnés à la main par le GM (/gm event points).
 */
public final class GmEvents {
    public enum Type {
        COMBAT("Combat", BossBar.Color.RED), RECOLTE("Récolte", BossBar.Color.GREEN),
        RESSOURCE("Ressource", BossBar.Color.PURPLE), SPECIAL("Spécial", BossBar.Color.YELLOW);

        public final String label;
        public final BossBar.Color color;

        Type(String label, BossBar.Color color) {
            this.label = label;
            this.color = color;
        }

        public static Type parse(String s) {
            for (Type t : values()) if (t.name().equalsIgnoreCase(s)) return t;
            return null;
        }
    }

    private static Type type;
    private static String title;
    private static long endTick, startTick, now, lastEndTick;
    private static ServerBossBar bar;
    private static final Map<UUID, Integer> SCORES = new HashMap<>();
    private static final Map<UUID, String> NAMES = new HashMap<>();

    private GmEvents() {}

    public static boolean running() {
        return type != null;
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            now++;
            if (type == null) {
                // Événements planifiés : un événement aléatoire se lance tout seul à intervalle régulier.
                var cfg = fr.serveur.rpg.auto.AutoConfig.get();
                if (cfg.evenementsAuto && now - lastEndTick >= cfg.evenementIntervalleMinutes * 1200L
                        && server.getCurrentPlayerCount() >= Math.max(1, cfg.evenementJoueursMin)) {
                    startRandom(server);
                }
                return;
            }
            long total = Math.max(1, endTick - startTick);
            bar.setPercent(Math.max(0f, Math.min(1f, (endTick - now) / (float) total)));
            if (now % 20 == 0) {
                long s = Math.max(0, (endTick - now) / 20);
                bar.setName(Text.literal("Événement " + type.label + " : " + title + " — " + (s / 60) + ":" + String.format("%02d", s % 60)));
            }
            if (now >= endTick) stop(server);
        });
        ServerPlayConnectionEvents.JOIN.register((h, s, server) -> {
            if (bar != null) bar.addPlayer(h.player);
        });

        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, killer, killed) -> {
            if (type == Type.COMBAT && killer instanceof ServerPlayerEntity p) add(p, killed instanceof PlayerEntity ? 3 : 1);
        });
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
            if (!(player instanceof ServerPlayerEntity p)) return;
            if (type == Type.RECOLTE) add(p, 1);
            if (type == Type.RESSOURCE && fr.serveur.rpg.economy.ObscureStone.isObscureStone(state)) add(p, 1);
        });
    }

    public static void add(ServerPlayerEntity p, int points) {
        SCORES.merge(p.getUuid(), points, Integer::sum);
        NAMES.put(p.getUuid(), p.getGameProfile().getName());
        p.sendMessage(Text.literal("+" + points + " point(s) d'événement (total " + SCORES.get(p.getUuid()) + ")").formatted(Formatting.AQUA), true);
    }

    public static void start(MinecraftServer server, Type t, int minutes, String name) {
        if (type != null) stop(server);
        type = t;
        title = name;
        startTick = now;
        endTick = now + minutes * 60L * 20L;
        SCORES.clear();
        NAMES.clear();
        bar = new ServerBossBar(Text.literal("Événement " + t.label + " : " + name), t.color, BossBar.Style.NOTCHED_10);
        server.getPlayerManager().getPlayerList().forEach(bar::addPlayer);
        Msg.broadcast(server, Text.literal("ÉVÉNEMENT " + t.label.toUpperCase() + " : " + name + " (" + minutes + " min) !").formatted(Formatting.AQUA, Formatting.BOLD));
    }

    private static final String[][] AUTO_TITLES = {
            {"COMBAT", "Chasse nocturne"}, {"COMBAT", "Arène sanglante"},
            {"RECOLTE", "Grande récolte"}, {"RECOLTE", "Les bras du royaume"},
            {"RESSOURCE", "Ruée vers la Pierre obscure"}, {"RESSOURCE", "Fièvre de l'obscur"}};

    public static void startRandom(MinecraftServer server) {
        String[] pick = AUTO_TITLES[(int) (Math.random() * AUTO_TITLES.length)];
        start(server, Type.valueOf(pick[0]), Math.max(1, fr.serveur.rpg.auto.AutoConfig.get().evenementDureeMinutes), pick[1]);
    }

    /** Secondes avant le prochain événement automatique (-1 si désactivé ou en cours). */
    public static long secondsToNextAuto() {
        var cfg = fr.serveur.rpg.auto.AutoConfig.get();
        if (type != null || !cfg.evenementsAuto) return -1;
        return Math.max(0, (lastEndTick + cfg.evenementIntervalleMinutes * 1200L - now) / 20);
    }

    public static String currentTitle() {
        return type == null ? null : title;
    }

    public static String currentType() {
        return type == null ? null : type.label;
    }

    public static long secondsLeft() {
        return type == null ? 0 : Math.max(0, (endTick - now) / 20);
    }

    public static void stop(MinecraftServer server) {
        if (type == null) return;
        lastEndTick = now;
        if (bar != null) bar.clearPlayers();
        // Récompenses automatiques du podium, versées en banque.
        List<Integer> rewards = fr.serveur.rpg.auto.AutoConfig.get().evenementRecompenses;
        List<Map.Entry<UUID, Integer>> podium = SCORES.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed()).toList();
        for (int i = 0; i < Math.min(podium.size(), rewards.size()); i++) {
            int coins = rewards.get(i);
            if (coins <= 0) continue;
            fr.serveur.rpg.economy.Bank.credit(server, podium.get(i).getKey(), coins);
            fr.serveur.rpg.economy.Bank.notify(server, podium.get(i).getKey(),
                    "Événement « " + title + " » : " + (i + 1) + "e place, +" + fr.serveur.rpg.economy.Bank.format(coins) + " !");
        }
        MutableText t = Text.literal("Fin de l'événement « " + title + " » — classement :").formatted(Formatting.AQUA);
        List<Map.Entry<UUID, Integer>> ranking = SCORES.entrySet().stream().sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed()).toList();
        if (ranking.isEmpty()) t.append(Text.literal("\n(aucun participant)").formatted(Formatting.GRAY));
        for (int i = 0; i < Math.min(10, ranking.size()); i++) {
            Map.Entry<UUID, Integer> e = ranking.get(i);
            t.append(Text.literal("\n" + (i + 1) + ". " + NAMES.get(e.getKey()) + " — " + e.getValue() + " pts")
                    .formatted(i == 0 ? Formatting.GOLD : Formatting.WHITE));
        }
        Msg.broadcast(server, t);
        type = null;
        bar = null;
    }

    public static Map<String, Integer> scores() {
        Map<String, Integer> m = new LinkedHashMap<>();
        SCORES.entrySet().stream().sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .forEach(e -> m.put(NAMES.get(e.getKey()), e.getValue()));
        return m;
    }
}
