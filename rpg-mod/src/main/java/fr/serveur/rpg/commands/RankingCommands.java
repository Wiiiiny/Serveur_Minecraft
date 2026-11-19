package fr.serveur.rpg.commands;

import com.mojang.brigadier.CommandDispatcher;
import fr.serveur.rpg.Msg;
import fr.serveur.rpg.data.PlayerData;
import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.economy.Bank;
import fr.serveur.rpg.jobs.JobConfig;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import static net.minecraft.server.command.CommandManager.literal;

/** /classement metiers|richesse|combats|morts */
public final class RankingCommands {
    private RankingCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(literal("classement")
                .executes(c -> {
                    Msg.info(c.getSource(), Text.literal("/classement metiers | richesse | combats | morts").formatted(Formatting.GRAY));
                    return 1;
                })
                .then(literal("metiers").executes(c -> show(c.getSource(), "Métiers",
                        Comparator.comparingInt((PlayerData p) -> p.current() == null ? -1 : p.current().prestige)
                                .thenComparingInt(p -> p.current() == null ? 0 : p.current().level)
                                .thenComparingInt(p -> p.current() == null ? 0 : p.current().xp),
                        p -> p.current() == null ? "sans métier"
                                : p.job.displayName + " niv. " + p.current().level + (p.current().prestige > 0 ? " ★" + p.current().prestige : ""))))
                .then(literal("richesse").executes(c -> show(c.getSource(), "Richesse",
                        Comparator.comparingLong((PlayerData p) -> p.money), p -> Bank.format(p.money))))
                .then(literal("combats").executes(c -> show(c.getSource(), "Combats",
                        Comparator.comparingInt((PlayerData p) -> p.pvpKills).thenComparingInt(p -> p.mobKills),
                        p -> p.pvpKills + " joueurs, " + p.mobKills + " créatures")))
                .then(literal("morts").executes(c -> show(c.getSource(), "Morts",
                        Comparator.comparingInt((PlayerData p) -> p.deaths), p -> p.deaths + " morts"))));
    }

    private static int show(ServerCommandSource src, String title, Comparator<PlayerData> order, Function<PlayerData, String> label) {
        List<PlayerData> list = RpgState.get(src.getServer()).players.values().stream().sorted(order.reversed()).limit(10).toList();
        MutableText t = Text.literal("— Classement : " + title + " —").formatted(Formatting.GOLD);
        for (int i = 0; i < list.size(); i++) {
            PlayerData p = list.get(i);
            t.append(Text.literal("\n" + (i + 1) + ". ").formatted(Formatting.GRAY))
                    .append(Text.literal(p.name).formatted(i == 0 ? Formatting.GOLD : Formatting.WHITE))
                    .append(Text.literal(" — " + label.apply(p)).formatted(Formatting.GRAY));
        }
        if (list.isEmpty()) t.append(Text.literal("\n(personne pour l'instant)").formatted(Formatting.GRAY));
        Msg.info(src, t);
        return 1;
    }
}
