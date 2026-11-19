package fr.serveur.rpg;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Messages avec le préfixe [RPG]. */
public final class Msg {
    private Msg() {}

    public static MutableText prefix() {
        return Text.literal("[RPG] ").formatted(Formatting.DARK_PURPLE);
    }

    public static void send(ServerPlayerEntity player, Text text) {
        player.sendMessage(prefix().append(text));
    }

    public static void ok(ServerCommandSource src, String text) {
        src.sendFeedback(() -> prefix().append(Text.literal(text).formatted(Formatting.GREEN)), true);
    }

    public static void info(ServerCommandSource src, Text text) {
        src.sendFeedback(() -> prefix().append(text), false);
    }

    public static void broadcast(MinecraftServer server, Text text) {
        server.getPlayerManager().broadcast(prefix().append(text), false);
    }
}
