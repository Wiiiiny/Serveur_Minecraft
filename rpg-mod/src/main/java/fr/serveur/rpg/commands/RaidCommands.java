package fr.serveur.rpg.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.serveur.rpg.Msg;
import fr.serveur.rpg.data.PlayerData;
import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.economy.Bank;
import fr.serveur.rpg.economy.Economy;
import fr.serveur.rpg.items.RaidItems;
import fr.serveur.rpg.raid.ExampleRooms;
import fr.serveur.rpg.raid.Party;
import fr.serveur.rpg.raid.RaidBuilder;
import fr.serveur.rpg.raid.RaidConfig;
import fr.serveur.rpg.raid.RaidCycle;
import fr.serveur.rpg.raid.RaidState;
import fr.serveur.rpg.raid.RoomLibrary;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** /raid (joueurs) et /gm raid (GM). */
public final class RaidCommands {
    private RaidCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(literal("raid")
                .executes(RaidCommands::info)
                .then(literal("entrer").executes(c -> reply(c, RaidCycle.enter(c.getSource().getPlayerOrThrow()), null)))
                .then(literal("reparer").executes(c -> reply(c, repair(c.getSource().getPlayerOrThrow()), "Brise-statue réparé.")))
                .then(literal("compagnon").executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                    PlayerData data = RpgState.get(p.getServer()).player(p);
                    data.companionDisabled = !data.companionDisabled;
                    RpgState.get(p.getServer()).markDirty();
                    Msg.info(c.getSource(), Text.literal(data.companionDisabled ? "Compagnon désactivé." : "Compagnon activé.").formatted(Formatting.GOLD));
                    return 1;
                }))
                .then(literal("groupe")
                        .executes(c -> {
                            ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                            MutableText t = Text.literal("Ton groupe : ").formatted(Formatting.GOLD);
                            for (UUID u : Party.members(p.getUuid())) {
                                PlayerData m = RpgState.get(p.getServer()).players.get(u);
                                t.append(Text.literal((m == null ? "?" : m.name) + " ").formatted(Formatting.WHITE));
                            }
                            Msg.info(c.getSource(), t);
                            return 1;
                        })
                        .then(literal("inviter").then(argument("joueur", EntityArgumentType.player()).executes(c -> {
                            ServerPlayerEntity p = c.getSource().getPlayerOrThrow(), to = EntityArgumentType.getPlayer(c, "joueur");
                            String e = Party.invite(p, to);
                            if (e == null) Msg.send(to, Text.literal(p.getGameProfile().getName() + " t'invite dans son groupe de raid : /raid groupe accepter")
                                    .formatted(Formatting.GOLD));
                            return reply(c, e, "Invitation envoyée à " + to.getGameProfile().getName() + ".");
                        })))
                        .then(literal("accepter").executes(c -> reply(c, Party.accept(c.getSource().getPlayerOrThrow()), "Tu as rejoint le groupe.")))
                        .then(literal("quitter").executes(c -> {
                            Party.leave(c.getSource().getPlayerOrThrow().getUuid());
                            return reply(c, null, "Tu as quitté ton groupe.");
                        }))));
    }

    /** Sous-commande /gm raid (ajoutée dans GmCommands). */
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> gm() {
        return literal("raid")
                .then(literal("info").executes(RaidCommands::info))
                .then(literal("hub").executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                    RaidState s = RaidState.get(p.getServer());
                    s.hubDim = p.getWorld().getRegistryKey().getValue().toString();
                    s.hub = p.getBlockPos();
                    s.hubYaw = p.getYaw();
                    s.markDirty();
                    Msg.ok(c.getSource(), "Point de retour du raid (hub) défini ici.");
                    return 1;
                }))
                .then(literal("portail").executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                    RaidState s = RaidState.get(p.getServer());
                    s.portal = p.getBlockPos();
                    if (s.hub == null) {
                        s.hubDim = p.getWorld().getRegistryKey().getValue().toString();
                        s.hub = p.getBlockPos();
                        s.hubYaw = p.getYaw();
                    }
                    s.markDirty();
                    Msg.ok(c.getSource(), "Portail du raid défini ici : les joueurs à moins de 6 blocs peuvent entrer (/raid entrer ou menu B).");
                    return 1;
                }))
                .then(literal("regenerer").executes(c -> reply(c, RaidCycle.startCycle(c.getSource().getServer(), "GM"), "Régénération lancée.")))
                .then(literal("niveau").then(argument("niveau", IntegerArgumentType.integer(1, 10)).executes(c -> {
                    RaidCycle.forceThreat(IntegerArgumentType.getInteger(c, "niveau") - 1);
                    Msg.ok(c.getSource(), "Niveau de menace imposé pour la prochaine génération.");
                    return 1;
                })))
                .then(literal("spawn").then(argument("type", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((c, b) -> net.minecraft.command.CommandSource.suggestMatching(java.util.List.of("statue", "voleur", "gardien"), b))
                        .executes(c -> {
                            ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                            var w = p.getServerWorld();
                            var look = p.getRotationVec(1f);
                            var at = net.minecraft.util.math.BlockPos.ofFloored(p.getX() + look.x * 4, p.getY(), p.getZ() + look.z * 4);
                            switch (com.mojang.brigadier.arguments.StringArgumentType.getString(c, "type")) {
                                case "statue" -> fr.serveur.rpg.raid.statue.Statues.spawn(w, at);
                                case "voleur" -> fr.serveur.rpg.raid.thief.Thieves.spawn(w, at);
                                case "gardien" -> fr.serveur.rpg.raid.RaidMobs.spawnBoss(w, at);
                                default -> {
                                    return reply(c, "Type inconnu : statue, voleur ou gardien.", null);
                                }
                            }
                            return reply(c, null, "Apparu devant toi.");
                        })))
                .then(literal("salles").executes(c -> {
                    Msg.ok(c.getSource(), RoomLibrary.load() + ". Dossier : " + RaidConfig.roomsDir());
                    return 1;
                }))
                .then(literal("exemples").executes(c -> {
                    try {
                        int n = ExampleRooms.write();
                        Msg.ok(c.getSource(), n + " salles d'exemple écrites. " + RoomLibrary.load());
                    } catch (Exception e) {
                        Msg.info(c.getSource(), Text.literal("Écriture impossible : " + e.getMessage()).formatted(Formatting.RED));
                    }
                    return 1;
                }));
    }

    private static int info(CommandContext<ServerCommandSource> c) {
        var server = c.getSource().getServer();
        RaidState s = RaidState.get(server);
        String status = switch (s.status) {
            case OUVERT -> "ouverte, niveau " + s.threat().nom + ", régénération dans " + Msg.duration(Math.max(0, (s.nextReset - System.currentTimeMillis()) / 1000));
            case GENERATION -> "en génération (" + RaidBuilder.progress() + " %)";
            case FERME -> "fermée";
        };
        Msg.info(c.getSource(), Text.literal("Zone de raid : " + status + ". Entrée : " + Bank.format(RaidCycle.entryFee(server))
                + (s.portal == null ? " — portail non placé (/gm raid portail)" : "") + ".").formatted(Formatting.LIGHT_PURPLE));
        return 1;
    }

    /** Réparation du Brise-statue contre des pièces (pas d'enclume). */
    public static String repair(ServerPlayerEntity p) {
        ItemStack held = p.getMainHandStack();
        if (!held.isOf(RaidItems.BRISE_STATUE)) return "Tiens ton Brise-statue en main.";
        if (held.getDamage() == 0) return "Ton Brise-statue est intact.";
        long price = (long) held.getDamage() * RaidConfig.get().reparationBriseStatue;
        String e = Economy.spend(p.getServer(), p.getUuid(), Economy.Source.BOUTIQUE, price, "réparation du Brise-statue");
        if (e != null) return e;
        held.setDamage(0);
        return null;
    }

    private static int reply(CommandContext<ServerCommandSource> c, String error, String ok) throws CommandSyntaxException {
        if (error != null) {
            Msg.info(c.getSource(), Text.literal(error).formatted(Formatting.RED));
            return 0;
        }
        if (ok != null) Msg.info(c.getSource(), Text.literal(ok).formatted(Formatting.GREEN));
        return 1;
    }
}
