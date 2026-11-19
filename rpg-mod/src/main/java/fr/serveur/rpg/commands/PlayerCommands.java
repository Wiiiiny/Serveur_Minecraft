package fr.serveur.rpg.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.serveur.rpg.Msg;
import fr.serveur.rpg.data.PlayerData;
import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.jobs.Job;
import fr.serveur.rpg.jobs.JobConfig;
import fr.serveur.rpg.jobs.Progression;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** /metier, /prestige, /quetes */
public final class PlayerCommands {
    private PlayerCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(literal("metier")
                .executes(c -> showInfo(c.getSource(), c.getSource().getPlayerOrThrow()))
                .then(literal("choisir").then(argument("metier", StringArgumentType.word())
                        .suggests((c, b) -> CommandSource.suggestMatching(Job.ids(), b))
                        .executes(PlayerCommands::choose))));

        d.register(literal("prestige")
                .executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                    PlayerData.JobProgress jp = RpgState.get(p.getServer()).player(p).current();
                    if (jp == null || jp.level < Job.MAX_LEVEL) {
                        Msg.info(c.getSource(), Text.literal("Le prestige est disponible au niveau " + Job.MAX_LEVEL + ".").formatted(Formatting.RED));
                        return 0;
                    }
                    Msg.info(c.getSource(), Text.literal("Le prestige te remet au niveau 1 de ton métier, avec un bonus d'argent sur les quêtes. ")
                            .append(Text.literal("Tape /prestige confirmer pour valider.").formatted(Formatting.GOLD)));
                    return 1;
                })
                .then(literal("confirmer").executes(c -> {
                    if (Progression.prestige(c.getSource().getPlayerOrThrow())) return 1;
                    Msg.info(c.getSource(), Text.literal("Le prestige est disponible au niveau " + Job.MAX_LEVEL + ".").formatted(Formatting.RED));
                    return 0;
                })));

        d.register(literal("quetes").executes(c -> showQuests(c.getSource(), c.getSource().getPlayerOrThrow())));

        // Après une mort de jour.
        d.register(literal("attente")
                .then(literal("spectateur").executes(c -> {
                    ServerPlayerEntity p = requireDead(c.getSource());
                    fr.serveur.rpg.death.DeathSystem.goSpectator(p);
                    return 1;
                }))
                .then(literal("salle").executes(c -> {
                    ServerPlayerEntity p = requireDead(c.getSource());
                    if (!fr.serveur.rpg.death.DeathSystem.goWaitingRoom(p)) {
                        Msg.info(c.getSource(), Text.literal("Le GM n'a pas encore défini de salle d'attente.").formatted(Formatting.RED));
                        return 0;
                    }
                    return 1;
                })));
        d.register(literal("larme").executes(c -> fr.serveur.rpg.death.DeathSystem.useTear(c.getSource().getPlayerOrThrow()) ? 1 : 0));
    }

    private static int choose(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
        Job job = Job.parse(StringArgumentType.getString(c, "metier"));
        if (job == null) {
            Msg.info(c.getSource(), Text.literal("Métier inconnu. Choix : " + String.join(", ", Job.ids())).formatted(Formatting.RED));
            return 0;
        }
        RpgState state = RpgState.get(p.getServer());
        PlayerData data = state.player(p);
        if (data.job != null) {
            Msg.info(c.getSource(), Text.literal("Tu es déjà " + data.job.displayName
                    + ". Pour changer de métier, demande l'autorisation au GM.").formatted(Formatting.RED));
            return 0;
        }
        data.job = job;
        data.current();
        state.markDirty();
        Msg.info(c.getSource(), Text.literal("Tu es maintenant ").append(Text.literal(job.displayName).formatted(job.color))
                .append(" ! Demande ton outil officiel (" + job.toolName.toLowerCase() + ") au GM."));
        return 1;
    }

    public static int showInfo(ServerCommandSource src, ServerPlayerEntity target) {
        PlayerData data = RpgState.get(target.getServer()).player(target);
        MutableText t = Text.literal("— " + data.name + " —\n").formatted(Formatting.GOLD);
        if (data.job == null) {
            t.append(Text.literal("Aucun métier. Tape /metier choisir <" + String.join("|", Job.ids()) + ">").formatted(Formatting.GRAY));
        } else {
            PlayerData.JobProgress p = data.current();
            String xp = p.level >= Job.MAX_LEVEL ? "MAX" : p.xp + " / " + JobConfig.xpNeeded(p.level);
            t.append(Text.literal("Métier : ").formatted(Formatting.GRAY)).append(Text.literal(data.job.displayName + "\n").formatted(data.job.color))
                    .append(Text.literal("Niveau : ").formatted(Formatting.GRAY)).append(Text.literal(p.level + " / " + Job.MAX_LEVEL + "\n").formatted(Formatting.WHITE))
                    .append(Text.literal("Progression : ").formatted(Formatting.GRAY)).append(Text.literal(xp + " XP " + bar(p) + "\n").formatted(Formatting.WHITE))
                    .append(Text.literal("Prestige : ").formatted(Formatting.GRAY)).append(Text.literal(String.valueOf(p.prestige)).formatted(Formatting.GOLD));
        }
        long open = data.quests.stream().filter(q -> !q.done).count();
        t.append(Text.literal("\nQuêtes en cours : " + open + " (/quetes)").formatted(Formatting.GRAY));
        Msg.info(src, t);
        return 1;
    }

    private static ServerPlayerEntity requireDead(ServerCommandSource src) throws CommandSyntaxException {
        ServerPlayerEntity p = src.getPlayerOrThrow();
        if (!RpgState.get(p.getServer()).player(p).awaitingRevive) {
            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                    Text.literal("Cette commande ne sert qu'après une mort de jour.")).create();
        }
        return p;
    }

    private static String bar(PlayerData.JobProgress p) {
        int filled = p.level >= Job.MAX_LEVEL ? 20 : (int) (20.0 * p.xp / JobConfig.xpNeeded(p.level));
        return "[" + "|".repeat(filled) + ".".repeat(20 - filled) + "]";
    }

    public static int showQuests(ServerCommandSource src, ServerPlayerEntity target) {
        PlayerData data = RpgState.get(target.getServer()).player(target);
        if (data.quests.isEmpty()) {
            Msg.info(src, Text.literal("Aucune quête. Le GM t'en attribuera.").formatted(Formatting.GRAY));
            return 1;
        }
        MutableText t = Text.literal("— Quêtes de " + data.name + " —").formatted(Formatting.GOLD);
        for (PlayerData.Quest q : data.quests) {
            t.append(Text.literal("\n#" + q.id + " ").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal(q.done ? "✔ " : "• ").formatted(q.done ? Formatting.GREEN : Formatting.YELLOW))
                    .append(Text.literal(q.text).formatted(q.done ? Formatting.GRAY : Formatting.WHITE));
        }
        Msg.info(src, t);
        return 1;
    }
}
