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

    /** « 1 h 05 min », « 4 min 12 s », « 30 s ». */
    public static String duration(long seconds) {
        if (seconds >= 3600) return seconds / 3600 + " h " + String.format("%02d", (seconds % 3600) / 60) + " min";
        if (seconds >= 60) return seconds / 60 + " min " + String.format("%02d", seconds % 60) + " s";
        return seconds + " s";
    }

    public static void broadcast(MinecraftServer server, Text text) {
        server.getPlayerManager().broadcast(prefix().append(text), false);
    }
}
