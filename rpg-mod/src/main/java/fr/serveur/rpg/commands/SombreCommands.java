package fr.serveur.rpg.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.serveur.rpg.Msg;
import fr.serveur.rpg.data.PlayerData;
import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.sombre.CoffresCaches;
import fr.serveur.rpg.sombre.Exotiques;
import fr.serveur.rpg.sombre.Journal;
import fr.serveur.rpg.sombre.Lumiere;
import fr.serveur.rpg.sombre.Obscurcie;
import fr.serveur.rpg.sombre.Personnage;
import fr.serveur.rpg.sombre.SombreConfig;
import fr.serveur.rpg.sombre.SombreItems;
import fr.serveur.rpg.sombre.SombreState;
import net.minecraft.block.Blocks;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Commandes du monde sombre.
 * Joueurs : /lore [joueur], /personnage.
 * GM (invisibles pour les joueurs, journalisées) : /gm lumiere, /gm personnage, /gm obscurcie, /gm eclat, /gm detecteur,
 * /gm coffres, /gm exotique, /gm lore.
 */
public final class SombreCommands {
    private SombreCommands() {}

    private static final SuggestionProvider<ServerCommandSource> TYPES = (c, b) ->
            CommandSource.suggestMatching(Arrays.stream(CoffresCaches.Type.values()).map(t -> t.name().toLowerCase()), b);
    private static final SuggestionProvider<ServerCommandSource> EXOTICS = (c, b) ->
            CommandSource.suggestMatching(SombreConfig.objets().exotiques.stream().map(e -> e.id), b);
    private static final SuggestionProvider<ServerCommandSource> NAMES = (c, b) ->
            CommandSource.suggestMatching(RpgState.get(c.getSource().getServer()).players.values().stream().map(p -> p.name), b);

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(literal("lore")
                .executes(c -> showLore(c, c.getSource().getPlayerOrThrow().getGameProfile().getName()))
                .then(argument("joueur", StringArgumentType.word()).suggests(NAMES)
                        .executes(c -> showLore(c, StringArgumentType.getString(c, "joueur")))));
        d.register(literal("personnage").executes(SombreCommands::personnage));

        d.register(literal("gm").requires(s -> s.hasPermissionLevel(2))
                .then(literal("lumiere")
                        .then(literal("jour").executes(c -> {
                            Lumiere.forceDay(c.getSource().getServer(), "le destin");
                            return gm(c, "lumiere_jour", "Le jour se lève (invoqué par le GM, sans annonce de préparation).");
                        }))
                        .then(literal("nuit").executes(c -> {
                            Lumiere.forceNight(c.getSource().getServer());
                            return gm(c, "lumiere_nuit", "La nuit retombe.");
                        }))
                        .then(literal("permanente").then(argument("actif", BoolArgumentType.bool()).executes(c -> {
                            boolean on = BoolArgumentType.getBool(c, "actif");
                            SombreState s = SombreState.get(c.getSource().getServer());
                            s.nuitPermanente = on;
                            s.markDirty();
                            return gm(c, "nuit_permanente_" + on, on ? "Nuit permanente activée : seul un Éclat de Lumière appelle le jour."
                                    : "Nuit permanente désactivée : retour au cycle jour/nuit réglé dans l'onglet Monde.");
                        })))
                        .then(literal("info").executes(SombreCommands::lumiereInfo)))
                .then(literal("personnage").then(argument("joueur", EntityArgumentType.player())
                        .then(argument("n", IntegerArgumentType.integer(1)).executes(c -> {
                            ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "joueur");
                            int n = IntegerArgumentType.getInteger(c, "n");
                            RpgState st = RpgState.get(c.getSource().getServer());
                            Personnage.set(st.player(p), n);
                            st.markDirty();
                            return gm(c, "niveau " + p.getGameProfile().getName() + " " + n,
                                    p.getGameProfile().getName() + " : niveau du personnage " + st.player(p).baseLevel + ".");
                        }))))
                .then(literal("obscurcie").then(argument("joueur", EntityArgumentType.player())
                        .then(argument("n", IntegerArgumentType.integer(1)).executes(c -> {
                            ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "joueur");
                            int n = IntegerArgumentType.getInteger(c, "n");
                            RpgState st = RpgState.get(c.getSource().getServer());
                            Obscurcie.set(st.player(p), n);
                            st.markDirty();
                            return gm(c, "obscurcie " + p.getGameProfile().getName() + " " + n, p.getGameProfile().getName() + " : Obscurcie niveau " + n + ".");
                        }))))
                .then(literal("eclat").then(argument("joueur", EntityArgumentType.player())
                        .executes(c -> giveEclat(c, 1))
                        .then(argument("n", IntegerArgumentType.integer(1, 64)).executes(c -> giveEclat(c, IntegerArgumentType.getInteger(c, "n"))))))
                .then(literal("detecteur").then(argument("joueur", EntityArgumentType.player()).executes(c -> {
                    ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "joueur");
                    give(p, new ItemStack(SombreItems.DETECTEUR));
                    return gm(c, "detecteur " + p.getGameProfile().getName(), "Détecteur d'ombre donné à " + p.getGameProfile().getName() + ".");
                })))
                .then(literal("coffres")
                        .then(literal("info").executes(SombreCommands::coffresInfo))
                        .then(literal("ici").then(argument("type", StringArgumentType.word()).suggests(TYPES)
                                .executes(c -> coffreIci(c, false))
                                .then(argument("piege", BoolArgumentType.bool()).executes(c -> coffreIci(c, BoolArgumentType.getBool(c, "piege"))))))
                        .then(literal("generer").executes(c -> coffresGenerer(c, 1))
                                .then(argument("n", IntegerArgumentType.integer(1, 40)).executes(c -> coffresGenerer(c, IntegerArgumentType.getInteger(c, "n")))))
                        .then(literal("vider").executes(SombreCommands::coffresVider)))
                .then(literal("exotique")
                        .then(literal("liste").executes(SombreCommands::exoListe))
                        .then(literal("donner").then(argument("id", StringArgumentType.word()).suggests(EXOTICS)
                                .executes(c -> exoDonner(c, c.getSource().getPlayerOrThrow()))
                                .then(argument("joueur", EntityArgumentType.player()).executes(c -> exoDonner(c, EntityArgumentType.getPlayer(c, "joueur"))))))
                        .then(literal("perdu").then(argument("id", StringArgumentType.word()).suggests(EXOTICS).executes(c -> {
                            String id = StringArgumentType.getString(c, "id");
                            SombreState s = SombreState.get(c.getSource().getServer());
                            SombreState.Exo e = s.exo(id);
                            e.statut = SombreState.Statut.PERDU;
                            e.perduLe = System.currentTimeMillis();
                            s.markDirty();
                            return gm(c, "exotique_perdu " + id, id + " marqué perdu. L'exemplaire actuel reste valable tant qu'il n'est pas réintroduit.");
                        })))
                        .then(literal("reintroduire").then(argument("id", StringArgumentType.word()).suggests(EXOTICS).executes(c -> {
                            String id = StringArgumentType.getString(c, "id");
                            SombreState s = SombreState.get(c.getSource().getServer());
                            SombreState.Exo e = s.exo(id);
                            if (e.statut == SombreState.Statut.EN_JEU) return fail(c, "Cet exotique est en jeu (porteur " + e.porteur + ") : marque-le perdu d'abord.");
                            e.statut = SombreState.Statut.PERDU;
                            e.perduLe = 0;
                            s.markDirty();
                            return gm(c, "exotique_reintroduire " + id, id + " peut réapparaître dès maintenant dans un coffre exotique.");
                        }))))
                .then(literal("lore").then(argument("joueur", StringArgumentType.word()).suggests(NAMES)
                        .executes(c -> showLore(c, StringArgumentType.getString(c, "joueur")))
                        .then(literal("effacer").executes(c -> {
                            PlayerData pd = byName(c, StringArgumentType.getString(c, "joueur"));
                            if (pd == null) return fail(c, "Joueur inconnu.");
                            pd.lore = "";
                            RpgState.get(c.getSource().getServer()).markDirty();
                            return gm(c, "lore_efface " + pd.name, "Lore de " + pd.name + " effacé.");
                        })))));
    }

    // ------------------------------------------------------------------ joueurs

    private static int showLore(CommandContext<ServerCommandSource> c, String name) {
        PlayerData pd = byName(c, name);
        if (pd == null) return fail(c, "Joueur inconnu.");
        ServerCommandSource src = c.getSource();
        boolean self = src.getPlayer() != null && src.getPlayer().getGameProfile().getName().equalsIgnoreCase(pd.name);
        if (!pd.lorePublic && !self && !src.hasPermissionLevel(2)) return fail(c, pd.name + " garde son histoire pour lui.");
        if (pd.lore.isBlank()) return fail(c, pd.name + " n'a pas encore écrit son histoire.");
        src.sendFeedback(() -> Text.literal("── Histoire de " + pd.name + (pd.lorePublic ? "" : " (privée)") + " ──").formatted(Formatting.GOLD), false);
        src.sendFeedback(() -> Text.literal(pd.lore).formatted(Formatting.GRAY, Formatting.ITALIC), false);
        return 1;
    }

    private static int personnage(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
        PlayerData d = RpgState.get(p.getServer()).player(p);
        boolean max = d.baseLevel >= Personnage.maxLevel();
        Msg.send(p, Text.literal("Personnage niveau " + d.baseLevel + (max ? " (maximum)" : " — " + d.baseXp + " / " + Personnage.xpNeeded(d.baseLevel) + " XP"))
                .formatted(Formatting.AQUA));
        SombreConfig.Obscurcie oc = SombreConfig.get().obscurcie;
        Msg.send(p, Text.literal("Obscurcie niveau " + d.obsLevel + " — " + d.obsXp + " / " + Obscurcie.xpNeeded(d.obsLevel) + " XP · prochain Éclat possible au niveau "
                + Obscurcie.nextMilestone(d.obsLevel, oc.palierEclat) + ", prochain détecteur au niveau " + Obscurcie.nextMilestone(d.obsLevel, oc.palierDetecteur))
                .formatted(Obscurcie.COLOR));
        return 1;
    }

    // ------------------------------------------------------------------ GM

    private static int lumiereInfo(CommandContext<ServerCommandSource> c) {
        var server = c.getSource().getServer();
        SombreState s = SombreState.get(server);
        Lumiere.Phase ph = Lumiere.phase(server);
        String line = switch (ph) {
            case CYCLE -> "Cycle jour/nuit classique (nuit permanente désactivée).";
            case NUIT -> "Nuit permanente. Prochain Éclat possible " + (Lumiere.secondsToNextEclat(server) > 0
                    ? "dans " + Msg.duration(Lumiere.secondsToNextEclat(server)) : "maintenant") + ".";
            case PREPARATION -> "Préparation : le jour se lève dans " + Lumiere.secondsLeft(server) + " s (Éclat de " + s.eclatBy + ").";
            case JOUR -> "Jour invoqué par " + s.eclatBy + " : encore " + Msg.duration(Lumiere.secondsLeft(server)) + ".";
        };
        Msg.info(c.getSource(), Text.literal(line));
        return 1;
    }

    private static int giveEclat(CommandContext<ServerCommandSource> c, int n) throws CommandSyntaxException {
        ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "joueur");
        give(p, new ItemStack(SombreItems.ECLAT_LUMIERE, n));
        return gm(c, "eclat " + p.getGameProfile().getName() + " " + n, n + " Éclat(s) de Lumière donné(s) à " + p.getGameProfile().getName() + ".");
    }

    private static int coffresInfo(CommandContext<ServerCommandSource> c) {
        SombreState s = SombreState.get(c.getSource().getServer());
        Map<CoffresCaches.Type, Integer> count = new EnumMap<>(CoffresCaches.Type.class);
        for (SombreState.Coffre k : s.coffres) count.merge(k.type, 1, Integer::sum);
        StringBuilder sb = new StringBuilder(s.coffres.size() + " coffre(s) caché(s)");
        count.forEach((t, n) -> sb.append(" · ").append(t.nom).append(" ").append(n));
        Msg.info(c.getSource(), Text.literal(sb.toString()));
        var from = c.getSource().getPosition();
        s.coffres.stream().filter(k -> k.dim.equals(c.getSource().getWorld().getRegistryKey().getValue().toString()))
                .sorted((a, b) -> Double.compare(a.pos.getSquaredDistance(from), b.pos.getSquaredDistance(from))).limit(5)
                .forEach(k -> Msg.info(c.getSource(), Text.literal("  " + k.type.nom + (k.piege ? " (piégé)" : "") + " en " + k.pos.toShortString()
                        + " — " + Math.round(Math.sqrt(k.pos.getSquaredDistance(from))) + " blocs").formatted(Formatting.GRAY)));
        return 1;
    }

    private static int coffreIci(CommandContext<ServerCommandSource> c, boolean trap) throws CommandSyntaxException {
        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
        CoffresCaches.Type type = CoffresCaches.Type.parse(StringArgumentType.getString(c, "type"));
        BlockPos pos = p.getBlockPos().offset(p.getHorizontalFacing(), 2);
        String e = CoffresCaches.spawnAt(p.getServerWorld(), pos, type, trap);
        if (e != null) return fail(c, e);
        return gm(c, "coffre_ici " + type.name() + " " + pos.toShortString(), type.nom + (trap ? " piégé" : "") + " posé en " + pos.toShortString() + ".");
    }

    private static int coffresGenerer(CommandContext<ServerCommandSource> c, int n) {
        int placed = 0;
        for (int i = 0; i < n; i++) if (CoffresCaches.spawn(c.getSource().getServer(), true)) placed++;
        return gm(c, "coffres_generer " + placed, placed + " coffre(s) caché(s) posé(s) autour des joueurs (" + SombreState.get(c.getSource().getServer()).coffres.size() + " en tout).");
    }

    private static int coffresVider(CommandContext<ServerCommandSource> c) {
        var server = c.getSource().getServer();
        SombreState s = SombreState.get(server);
        int n = s.coffres.size();
        for (SombreState.Coffre k : s.coffres) {
            ServerWorld w = server.getWorld(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD,
                    new net.minecraft.util.Identifier(k.dim)));
            if (w != null && w.getChunkManager().isChunkLoaded(k.pos.getX() >> 4, k.pos.getZ() >> 4)) w.setBlockState(k.pos, Blocks.AIR.getDefaultState());
        }
        s.coffres.clear();
        s.markDirty();
        return gm(c, "coffres_vider", n + " coffre(s) caché(s) retiré(s) (les blocs des zones non chargées restent, sans butin).");
    }

    private static int exoListe(CommandContext<ServerCommandSource> c) {
        SombreState s = SombreState.get(c.getSource().getServer());
        for (SombreConfig.Exotique d : SombreConfig.objets().exotiques) {
            SombreState.Exo e = s.exotiques.get(d.id);
            String st = e == null || e.statut == SombreState.Statut.ABSENT ? "jamais apparu"
                    : e.statut == SombreState.Statut.EN_JEU ? "en jeu, porteur " + e.porteur : "perdu";
            Msg.info(c.getSource(), Text.literal(d.id + " — " + d.nom + " : " + st).formatted(
                    e != null && e.statut == SombreState.Statut.EN_JEU ? Formatting.YELLOW : Formatting.GRAY));
        }
        return 1;
    }

    private static int exoDonner(CommandContext<ServerCommandSource> c, ServerPlayerEntity to) {
        String id = StringArgumentType.getString(c, "id");
        if (Exotiques.def(id) == null) return fail(c, "Exotique inconnu : " + id);
        SombreState.Exo before = SombreState.get(c.getSource().getServer()).exotiques.get(id);
        String warn = before != null && before.statut == SombreState.Statut.EN_JEU
                ? " L'ancien exemplaire (porteur " + before.porteur + ") est désormais invalide et sera supprimé s'il réapparaît." : "";
        ItemStack st = Exotiques.introduce(c.getSource().getServer(), id, to.getGameProfile().getName(), "GM " + c.getSource().getName(), false);
        give(to, st);
        return gm(c, "exotique_donner " + id + " " + to.getGameProfile().getName(), st.getName().getString() + " donné à " + to.getGameProfile().getName() + "." + warn);
    }

    // ------------------------------------------------------------------ outils

    private static void give(ServerPlayerEntity p, ItemStack st) {
        if (!p.getInventory().insertStack(st)) p.dropItem(st, false);
    }

    private static PlayerData byName(CommandContext<ServerCommandSource> c, String name) {
        for (PlayerData pd : RpgState.get(c.getSource().getServer()).players.values()) if (pd.name.equalsIgnoreCase(name)) return pd;
        return null;
    }

    /** Réponse au GM seul (rien n'est diffusé aux joueurs) et ligne dans le journal des actions GM. */
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
