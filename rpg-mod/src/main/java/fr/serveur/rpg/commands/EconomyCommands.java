package fr.serveur.rpg.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.serveur.rpg.Msg;
import fr.serveur.rpg.data.MarketItem;
import fr.serveur.rpg.data.PlayerData;
import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.economy.Bank;
import fr.serveur.rpg.economy.ObscureStone;
import fr.serveur.rpg.gui.MarketMenus;
import net.minecraft.command.CommandSource;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Map;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** /banque, /hdv, /boutique */
public final class EconomyCommands {
    public static final int MAX_LISTINGS_PER_PLAYER = 27;

    /** Pseudos connus (y compris hors ligne). */
    public static final SuggestionProvider<ServerCommandSource> KNOWN_PLAYERS = (c, b) ->
            CommandSource.suggestMatching(RpgState.get(c.getSource().getServer()).players.values().stream().map(d -> d.name), b);

    private EconomyCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(literal("banque")
                .executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                    PlayerData data = RpgState.get(p.getServer()).player(p);
                    Msg.info(c.getSource(), Text.literal("Solde : ").formatted(Formatting.GRAY)
                            .append(Text.literal(Bank.format(data.money)).formatted(Formatting.GOLD))
                            .append(Text.literal(" — sur toi : " + Bank.format(Bank.countCurrency(p))).formatted(Formatting.GRAY)));
                    return 1;
                })
                .then(literal("deposer")
                        .then(literal("tout").executes(c -> deposit(c, -1)))
                        .then(argument("montant", IntegerArgumentType.integer(1)).executes(c -> deposit(c, IntegerArgumentType.getInteger(c, "montant")))))
                .then(literal("retirer").then(argument("montant", LongArgumentType.longArg(1)).executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                    long amount = LongArgumentType.getLong(c, "montant");
                    if (!Bank.withdraw(p, amount)) throw err("Solde insuffisant.");
                    Msg.info(c.getSource(), Text.literal("Retrait de " + Bank.format(amount) + ".").formatted(Formatting.GREEN));
                    return 1;
                })))
                .then(literal("virer").then(argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                        .then(argument("montant", LongArgumentType.longArg(1)).executes(c -> {
                            ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                            RpgState state = RpgState.get(p.getServer());
                            Map.Entry<UUID, PlayerData> target = resolve(c, state);
                            if (target.getKey().equals(p.getUuid())) throw err("Tu ne peux pas te virer de l'argent à toi-même.");
                            long amount = LongArgumentType.getLong(c, "montant");
                            PlayerData me = state.player(p);
                            if (me.money < amount) throw err("Solde insuffisant (" + Bank.format(me.money) + ").");
                            me.money -= amount;
                            target.getValue().money += amount;
                            state.markDirty();
                            Msg.info(c.getSource(), Text.literal("Virement de " + Bank.format(amount) + " à " + target.getValue().name + ".").formatted(Formatting.GREEN));
                            Bank.notify(p.getServer(), target.getKey(), p.getGameProfile().getName() + " t'a viré " + Bank.format(amount) + ".");
                            return 1;
                        })))));

        d.register(literal("hdv")
                .executes(c -> {
                    MarketMenus.openAuctions(c.getSource().getPlayerOrThrow(), 0);
                    return 1;
                })
                .then(literal("vendre").then(argument("prix", LongArgumentType.longArg(1)).executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                    ItemStack held = p.getMainHandStack();
                    if (held.isEmpty()) throw err("Tiens en main l'objet à vendre.");
                    if (ObscureStone.isPickaxe(held)) throw err("La Pioche des ténèbres ne peut pas être vendue.");
                    RpgState state = RpgState.get(p.getServer());
                    long mine = state.auctions.stream().filter(a -> p.getUuid().equals(a.seller)).count();
                    if (mine >= MAX_LISTINGS_PER_PLAYER) throw err("Tu as déjà " + MAX_LISTINGS_PER_PLAYER + " objets en vente.");
                    MarketItem m = new MarketItem();
                    m.id = state.nextMarketId++;
                    m.seller = p.getUuid();
                    m.sellerName = p.getGameProfile().getName();
                    m.stack = held.copy();
                    m.price = LongArgumentType.getLong(c, "prix");
                    p.getInventory().removeOne(held);
                    state.auctions.add(m);
                    state.markDirty();
                    Msg.info(c.getSource(), Text.literal("Mis en vente : " + m.stack.getCount() + "x ").formatted(Formatting.GREEN)
                            .append(m.stack.getName()).append(" pour " + Bank.format(m.price) + ". (/hdv pour le retirer)"));
                    return 1;
                }))));

        d.register(literal("boutique").executes(c -> {
            MarketMenus.openShop(c.getSource().getPlayerOrThrow(), 0);
            return 1;
        }));
    }

    private static int deposit(CommandContext<ServerCommandSource> c, int amount) throws CommandSyntaxException {
        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
        int n = amount < 0 ? Bank.countCurrency(p) : amount;
        if (n <= 0 || !Bank.deposit(p, n)) throw err("Tu n'as pas assez de poudre de glowstone sur toi.");
        Msg.info(c.getSource(), Text.literal("Dépôt de " + Bank.format(n) + ". Nouveau solde : "
                + Bank.format(RpgState.get(p.getServer()).player(p).money) + ".").formatted(Formatting.GREEN));
        return 1;
    }

    public static Map.Entry<UUID, PlayerData> resolve(CommandContext<ServerCommandSource> c, RpgState state) throws CommandSyntaxException {
        String name = StringArgumentType.getString(c, "joueur");
        Map.Entry<UUID, PlayerData> e = state.findByName(name);
        if (e == null) throw err("Joueur inconnu : " + name + " (il doit s'être connecté au moins une fois).");
        return e;
    }

    public static CommandSyntaxException err(String message) {
        return new SimpleCommandExceptionType(Text.literal(message)).create();
    }
}
