package fr.serveur.rpg.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.serveur.rpg.Msg;
import fr.serveur.rpg.economy.Bank;
import fr.serveur.rpg.faction.Faction;
import fr.serveur.rpg.faction.FactionState;
import fr.serveur.rpg.faction.Factions;
import fr.serveur.rpg.faction.Territories;
import fr.serveur.rpg.faction.Wars;
import fr.serveur.rpg.sombre.Journal;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.Function;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** /faction (ou /f) pour les joueurs, /gm faction pour le GM. Tout se fait aussi dans le menu B › Faction. */
public final class FactionCommands {
    private FactionCommands() {}

    private static final SuggestionProvider<ServerCommandSource> FACTIONS = (c, b) ->
            CommandSource.suggestMatching(FactionState.get(c.getSource().getServer()).factions.values().stream().map(f -> f.name), b);

    private static int run(CommandContext<ServerCommandSource> c, Function<ServerPlayerEntity, String> action, String ok) throws CommandSyntaxException {
        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
        String e = action.apply(p);
        if (e != null) {
            c.getSource().sendError(Text.literal(e));
            return 0;
        }
        if (ok != null) Msg.ok(c.getSource(), ok);
        return 1;
    }

    private static Faction byName(CommandContext<ServerCommandSource> c, String arg) {
        return FactionState.get(c.getSource().getServer()).byName(StringArgumentType.getString(c, arg));
    }

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        LiteralArgumentBuilder<ServerCommandSource> root = literal("faction")
                .executes(c -> info(c, Factions.of(c.getSource().getPlayerOrThrow())))
                .then(literal("creer").then(argument("nom", StringArgumentType.greedyString())
                        .executes(c -> run(c, p -> Factions.create(p, StringArgumentType.getString(c, "nom")), null))))
                .then(literal("inviter").then(argument("joueur", StringArgumentType.word())
                        .executes(c -> run(c, p -> Factions.invite(p, StringArgumentType.getString(c, "joueur")), "Invitation envoyée."))))
                .then(literal("accepter").then(argument("faction", StringArgumentType.greedyString()).suggests(FACTIONS)
                        .executes(c -> run(c, p -> {
                            Faction f = byName(c, "faction");
                            return f == null ? "Faction inconnue." : Factions.join(p, f.id);
                        }, null))))
                .then(literal("quitter").executes(c -> run(c, Factions::leave, "Tu as quitté ta faction.")))
                .then(literal("info").executes(c -> info(c, Factions.of(c.getSource().getPlayerOrThrow())))
                        .then(argument("faction", StringArgumentType.greedyString()).suggests(FACTIONS).executes(c -> info(c, byName(c, "faction")))))
                .then(literal("liste").executes(FactionCommands::list))
                .then(literal("base").executes(c -> run(c, Territories::teleportBase, null))
                        .then(literal("deplacer").executes(c -> run(c, Territories::moveBase, "Base déplacée ici."))))
                .then(literal("coffre").executes(c -> run(c, p -> {
                    Faction f = Factions.of(p);
                    return f == null ? "Tu n'es dans aucune faction." : Territories.openChest(p, f);
                }, null)))
                .then(literal("deposer").then(argument("montant", LongArgumentType.longArg(1))
                        .executes(c -> run(c, p -> Factions.deposit(p, LongArgumentType.getLong(c, "montant")), "Merci pour ta contribution."))))
                .then(literal("retirer").then(argument("montant", LongArgumentType.longArg(1))
                        .executes(c -> run(c, p -> Factions.withdraw(p, LongArgumentType.getLong(c, "montant")), "Retrait fait."))))
                .then(literal("guerre")
                        .then(literal("declarer").then(argument("faction", StringArgumentType.greedyString()).suggests(FACTIONS)
                                .executes(c -> run(c, p -> {
                                    Faction f = byName(c, "faction");
                                    return f == null ? "Faction inconnue." : Wars.declare(p, f.id);
                                }, null))))
                        .then(literal("capituler").executes(c -> run(c, Wars::offerCapitulation, null)))
                        .then(literal("accepter").executes(c -> run(c, p -> Wars.answerCapitulation(p, true), null)))
                        .then(literal("refuser").executes(c -> run(c, p -> Wars.answerCapitulation(p, false), "Capitulation refusée.")))
                        .then(literal("nuit").then(argument("accepter", BoolArgumentType.bool())
                                .executes(c -> run(c, p -> Wars.setNight(p, BoolArgumentType.getBool(c, "accepter")), null)))));
        var node = d.register(root);
        d.register(literal("f").redirect(node));

        d.register(literal("gm").requires(s -> s.hasPermissionLevel(2)).then(literal("faction")
                .then(literal("liste").executes(FactionCommands::list))
                .then(literal("supprimer").then(argument("faction", StringArgumentType.greedyString()).suggests(FACTIONS).executes(c -> {
                    Faction f = byName(c, "faction");
                    if (f == null) return fail(c, "Faction inconnue.");
                    Factions.dissolve(c.getSource().getServer(), f, "dissoute par le GM");
                    return gm(c, "faction_supprimer " + f.name, "Faction « " + f.name + " » dissoute.");
                })))
                .then(literal("niveau").then(argument("faction", StringArgumentType.string()).suggests(FACTIONS)
                        .then(argument("n", IntegerArgumentType.integer(1)).executes(c -> {
                            Faction f = byName(c, "faction");
                            if (f == null) return fail(c, "Faction inconnue (nom entre guillemets s'il contient des espaces).");
                            f.level = IntegerArgumentType.getInteger(c, "n");
                            f.xp = 0;
                            FactionState.get(c.getSource().getServer()).markDirty();
                            return gm(c, "faction_niveau " + f.name + " " + f.level, "« " + f.name + " » : niveau " + f.level + ".");
                        }))))
                .then(literal("tresor").then(argument("faction", StringArgumentType.string()).suggests(FACTIONS)
                        .then(argument("montant", LongArgumentType.longArg()).executes(c -> {
                            Faction f = byName(c, "faction");
                            if (f == null) return fail(c, "Faction inconnue (nom entre guillemets s'il contient des espaces).");
                            f.bank = Math.max(0, f.bank + LongArgumentType.getLong(c, "montant"));
                            FactionState.get(c.getSource().getServer()).markDirty();
                            return gm(c, "faction_tresor " + f.name + " " + LongArgumentType.getLong(c, "montant"), "Trésor de « " + f.name + " » : " + Bank.format(f.bank) + ".");
                        }))))
                .then(literal("guerre").then(literal("terminer").then(argument("faction", StringArgumentType.greedyString()).suggests(FACTIONS).executes(c -> {
                    Faction f = byName(c, "faction");
                    FactionState.War w = f == null ? null : FactionState.get(c.getSource().getServer()).warOf(f.id);
                    if (w == null) return fail(c, "Aucune guerre en cours pour cette faction.");
                    Wars.gmEnd(c.getSource().getServer(), w);
                    return gm(c, "faction_guerre_terminer " + f.name, "Guerre terminée sans vainqueur.");
                }))))
                .then(literal("coeur").then(argument("faction", StringArgumentType.greedyString()).suggests(FACTIONS).executes(c -> {
                    Faction f = byName(c, "faction");
                    if (f == null) return fail(c, "Faction inconnue.");
                    f.heartHp = fr.serveur.rpg.faction.FactionConfig.get().coeurPointsDeVie;
                    FactionState.get(c.getSource().getServer()).markDirty();
                    return gm(c, "faction_coeur " + f.name, "Cœur de « " + f.name + " » remis à neuf.");
                })))));
    }

    private static int info(CommandContext<ServerCommandSource> c, Faction f) {
        if (f == null) return fail(c, "Aucune faction (crée-la : /faction creer <nom>, ou menu B › Faction).");
        var server = c.getSource().getServer();
        FactionState.War w = FactionState.get(server).warOf(f.id);
        Msg.info(c.getSource(), Text.literal("⚑ " + f.name + " — niveau " + f.level + " (" + f.xp + " / " + Factions.xpNeeded(f.level) + " XP)").formatted(Formatting.DARK_AQUA));
        Msg.info(c.getSource(), Text.literal(f.members.size() + " membres · " + f.territories.size() + " territoire(s), rayon " + f.radius()
                + " · trésor " + Bank.format(f.bank) + " · guerres " + f.warsWon + " gagnée(s), " + f.warsLost + " perdue(s)").formatted(Formatting.GRAY));
        StringBuilder sb = new StringBuilder();
        for (Faction.Member m : f.members.values()) sb.append(sb.isEmpty() ? "" : ", ").append(m.name).append(" (").append(m.rank.nom).append(")");
        Msg.info(c.getSource(), Text.literal(sb.toString()).formatted(Formatting.GRAY));
        if (w != null) {
            Faction o = FactionState.get(server).factions.get(w.other(f.id));
            Msg.info(c.getSource(), Text.literal("En guerre contre « " + (o == null ? "?" : o.name) + " » : " + Wars.status(server, w)).formatted(Formatting.RED));
        }
        return 1;
    }

    private static int list(CommandContext<ServerCommandSource> c) {
        var s = FactionState.get(c.getSource().getServer());
        if (s.factions.isEmpty()) return fail(c, "Aucune faction pour l'instant.");
        for (Faction f : s.factions.values()) {
            Msg.info(c.getSource(), Text.literal("⚑ " + f.name + " — niveau " + f.level + ", " + f.members.size() + " membres, "
                    + f.territories.size() + " territoire(s)").formatted(Formatting.GRAY));
        }
        return 1;
    }

    private static int gm(CommandContext<ServerCommandSource> c, String action, String message) {
        Journal.write(c.getSource().getServer(), "gm", action, "gm", c.getSource().getName());
        Msg.ok(c.getSource(), message);
        return 1;
    }

    private static int fail(CommandContext<ServerCommandSource> c, String message) {
        c.getSource().sendError(Text.literal(message));
        return 0;
    }
}
