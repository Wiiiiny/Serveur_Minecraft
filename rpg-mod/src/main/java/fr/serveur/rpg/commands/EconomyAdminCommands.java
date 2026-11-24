package fr.serveur.rpg.commands;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.serveur.rpg.Msg;
import fr.serveur.rpg.data.PlayerData;
import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.economy.Economy;
import fr.serveur.rpg.economy.EconomyConfig;
import fr.serveur.rpg.economy.EconomyTicker;
import fr.serveur.rpg.economy.Ledger;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * /gm economie ... : audit, comptes gelés, annulation de transactions, sauvegardes des soldes, régulateur.
 * Commandes de secours : l'économie tourne seule, le GM n'intervient que sur alerte.
 */
public final class EconomyAdminCommands {
    private EconomyAdminCommands() {}

    public static LiteralArgumentBuilder<ServerCommandSource> build() {
        return literal("economie")
                .then(literal("audit").executes(EconomyAdminCommands::audit))
                .then(literal("mesurer").executes(c -> {
                    EconomyTicker.measureNow(c.getSource().getServer());
                    Msg.ok(c.getSource(), "Métriques mesurées et régulateur appliqué.");
                    return audit(c);
                }))
                .then(literal("geler").then(player().executes(c -> freeze(c, true))))
                .then(literal("degeler").then(player().executes(c -> freeze(c, false))))
                .then(literal("transactions").then(player().executes(c -> history(c, 10))
                        .then(argument("nombre", IntegerArgumentType.integer(1, 50)).executes(c -> history(c, IntegerArgumentType.getInteger(c, "nombre"))))))
                .then(literal("annuler").then(argument("id", LongArgumentType.longArg(1)).executes(EconomyAdminCommands::undo)))
                .then(literal("annuler-joueur").then(player().then(argument("minutes", IntegerArgumentType.integer(1, 60 * 24 * 7))
                        .executes(EconomyAdminCommands::undoPlayer))))
                .then(literal("sauvegardes").executes(EconomyAdminCommands::listBackups))
                .then(literal("sauvegarder").executes(c -> {
                    EconomyTicker.backup(c.getSource().getServer(), RpgState.get(c.getSource().getServer()));
                    Msg.ok(c.getSource(), "Soldes sauvegardés.");
                    return 1;
                }))
                .then(literal("restaurer").then(argument("sauvegarde", StringArgumentType.word())
                        .suggests((c, b) -> CommandSource.suggestMatching(EconomyTicker.backups(c.getSource().getServer()).stream()
                                .map(p -> p.getFileName().toString().replace(".json", "")), b))
                        .executes(c -> restore(c, null))
                        .then(player().executes(c -> restore(c, StringArgumentType.getString(c, "joueur"))))))
                .then(literal("multiplicateurs").then(argument("gains", DoubleArgumentType.doubleArg(0.1, 3))
                        .then(argument("taxes", DoubleArgumentType.doubleArg(0.1, 3)).executes(c -> {
                            RpgState s = RpgState.get(c.getSource().getServer());
                            s.econGainMult = DoubleArgumentType.getDouble(c, "gains");
                            s.econTaxMult = DoubleArgumentType.getDouble(c, "taxes");
                            s.markDirty();
                            Msg.ok(c.getSource(), "Multiplicateurs : gains ×" + s.econGainMult + ", taxes ×" + s.econTaxMult
                                    + " (le régulateur reprend la main à la prochaine heure).");
                            return 1;
                        }))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<ServerCommandSource, String> player() {
        return argument("joueur", StringArgumentType.word()).suggests(EconomyCommands.KNOWN_PLAYERS);
    }

    private static int audit(CommandContext<ServerCommandSource> c) {
        RpgState s = RpgState.get(c.getSource().getServer());
        EconomyTicker.Snapshot snap = EconomyTicker.snapshot(s);
        EconomyConfig.Data cfg = EconomyConfig.get();
        long perActive = snap.active() == 0 ? 0 : snap.supply() / snap.active();
        MutableText t = Text.literal("— Économie —").formatted(Formatting.GOLD)
                .append(Text.literal("\nMasse monétaire : " + snap.supply() + " pièces · " + snap.active() + " joueurs actifs · "
                        + perActive + " par actif (cible " + cfg.masseCibleParJoueurActif + ")").formatted(Formatting.WHITE))
                .append(Text.literal("\nSoldes : moyenne " + snap.mean() + ", médiane " + snap.median()).formatted(Formatting.WHITE))
                .append(Text.literal("\nRégulateur : gains ×" + String.format("%.2f", s.econGainMult) + ", taxes ×"
                        + String.format("%.2f", s.econTaxMult)).formatted(Formatting.AQUA));
        if (!s.econMetrics.isEmpty()) {
            var last = s.econMetrics.getCompound(s.econMetrics.size() - 1);
            MutableText flows = Text.literal("\nFlux de l'heure mesurée (" + last.getString("t").replace('T', ' ') + ") :").formatted(Formatting.GRAY);
            var f = last.getCompound("flux");
            for (String k : f.getKeys()) flows.append(Text.literal(" " + k + " " + (f.getLong(k) > 0 ? "+" : "") + f.getLong(k)));
            t.append(flows);
        }
        List<String> frozen = s.players.values().stream().filter(d -> d.frozen).map(d -> d.name).toList();
        t.append(Text.literal("\nComptes gelés : " + (frozen.isEmpty() ? "aucun" : String.join(", ", frozen))).formatted(frozen.isEmpty() ? Formatting.GREEN : Formatting.RED));
        if (!s.econAlerts.isEmpty()) {
            t.append(Text.literal("\nDernières alertes :").formatted(Formatting.RED));
            s.econAlerts.subList(Math.max(0, s.econAlerts.size() - 5), s.econAlerts.size())
                    .forEach(a -> t.append(Text.literal("\n- " + a).formatted(Formatting.GRAY)));
        }
        Msg.info(c.getSource(), t);
        return 1;
    }

    private static int freeze(CommandContext<ServerCommandSource> c, boolean frozen) throws CommandSyntaxException {
        RpgState s = RpgState.get(c.getSource().getServer());
        Map.Entry<UUID, PlayerData> e = EconomyCommands.resolve(c, s);
        e.getValue().frozen = frozen;
        s.markDirty();
        Msg.ok(c.getSource(), "Compte de " + e.getValue().name + (frozen ? " gelé." : " dégelé."));
        return 1;
    }

    private static int history(CommandContext<ServerCommandSource> c, int count) throws CommandSyntaxException {
        RpgState s = RpgState.get(c.getSource().getServer());
        Map.Entry<UUID, PlayerData> e = EconomyCommands.resolve(c, s);
        List<Ledger.Entry> mine = Ledger.read(c.getSource().getServer(), 7).stream().filter(x -> x.player().equals(e.getKey())).toList();
        MutableText t = Text.literal("— " + e.getValue().name + " : " + Math.min(count, mine.size()) + " dernières transactions —").formatted(Formatting.GOLD);
        for (Ledger.Entry x : mine.subList(Math.max(0, mine.size() - count), mine.size())) {
            t.append(Text.literal("\n#" + x.id() + " " + x.time().toLocalTime().withNano(0) + " ").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal((x.amount() > 0 ? "+" : "") + x.amount() + " ").formatted(x.amount() >= 0 ? Formatting.GREEN : Formatting.RED))
                    .append(Text.literal(x.source() + (x.detail().isEmpty() ? "" : " (" + x.detail() + ")") + " → " + x.after()).formatted(Formatting.GRAY));
        }
        Msg.info(c.getSource(), t);
        return 1;
    }

    private static int undo(CommandContext<ServerCommandSource> c) {
        long id = LongArgumentType.getLong(c, "id");
        Ledger.Entry e = Ledger.read(c.getSource().getServer(), 30).stream().filter(x -> x.id() == id).findFirst().orElse(null);
        if (e == null) {
            Msg.info(c.getSource(), Text.literal("Transaction #" + id + " introuvable (30 derniers jours).").formatted(Formatting.RED));
            return 0;
        }
        Economy.reverse(c.getSource().getServer(), e, c.getSource().getName());
        Msg.ok(c.getSource(), "Transaction #" + id + " annulée (" + e.name() + ", " + e.amount() + " pièces, " + e.source() + ").");
        return 1;
    }

    private static int undoPlayer(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        RpgState s = RpgState.get(c.getSource().getServer());
        Map.Entry<UUID, PlayerData> who = EconomyCommands.resolve(c, s);
        LocalDateTime since = LocalDateTime.now().minusMinutes(IntegerArgumentType.getInteger(c, "minutes"));
        List<Ledger.Entry> list = Ledger.read(c.getSource().getServer(), 8).stream()
                .filter(x -> x.player().equals(who.getKey()) && x.time().isAfter(since) && !x.source().equals(Economy.Source.ANNULATION.key))
                .toList();
        long total = 0;
        for (int i = list.size() - 1; i >= 0; i--) {
            Economy.reverse(c.getSource().getServer(), list.get(i), c.getSource().getName());
            total += list.get(i).amount();
        }
        Msg.ok(c.getSource(), list.size() + " transaction(s) de " + who.getValue().name + " annulée(s) (" + (total > 0 ? "−" : "+") + Math.abs(total)
                + " pièces). Les échanges avec d'autres joueurs ne sont annulés que de son côté.");
        return 1;
    }

    private static int listBackups(CommandContext<ServerCommandSource> c) {
        List<Path> all = EconomyTicker.backups(c.getSource().getServer());
        MutableText t = Text.literal("— Sauvegardes des soldes (" + all.size() + ") —").formatted(Formatting.GOLD);
        all.subList(Math.max(0, all.size() - 10), all.size()).forEach(p -> t.append(Text.literal("\n" + p.getFileName().toString().replace(".json", ""))));
        t.append(Text.literal("\n/gm economie restaurer <sauvegarde> [joueur]").formatted(Formatting.GRAY));
        Msg.info(c.getSource(), t);
        return 1;
    }

    private static int restore(CommandContext<ServerCommandSource> c, String playerName) throws CommandSyntaxException {
        RpgState s = RpgState.get(c.getSource().getServer());
        UUID id = null;
        if (playerName != null) id = EconomyCommands.resolve(c, s).getKey();
        int n = EconomyTicker.restore(c.getSource().getServer(), StringArgumentType.getString(c, "sauvegarde"), id);
        if (n < 0) {
            Msg.info(c.getSource(), Text.literal("Sauvegarde introuvable : /gm economie sauvegardes").formatted(Formatting.RED));
            return 0;
        }
        Msg.ok(c.getSource(), n + " solde(s) restauré(s).");
        return 1;
    }
}
