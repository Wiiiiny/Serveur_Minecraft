package fr.serveur.rpg.economy;

import fr.serveur.rpg.Msg;
import fr.serveur.rpg.data.PlayerData;
import fr.serveur.rpg.data.RpgState;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

/**
 * Banque : un compte par joueur, en Pièces d'or (la monnaie officielle).
 * Déposer retire de vraies pièces de l'inventaire, retirer en redonne.
 */
public final class Bank {
    private Bank() {}

    public static String format(long amount) {
        return amount + (amount > 1 ? " pièces" : " pièce");
    }

    public static int countCurrency(ServerPlayerEntity player) {
        int n = 0;
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (isCurrency(s)) n += s.getCount();
        }
        return n;
    }

    public static boolean isCurrency(ItemStack s) {
        return s.isOf(fr.serveur.rpg.items.ModItems.PIECE_OR) && !s.hasNbt();
    }

    /** Dépose {@code amount} pièces de l'inventaire vers le compte. */
    public static boolean deposit(ServerPlayerEntity player, int amount) {
        if (amount <= 0 || countCurrency(player) < amount) return false;
        int left = amount;
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.size() && left > 0; i++) {
            ItemStack s = inv.getStack(i);
            if (!isCurrency(s)) continue;
            int take = Math.min(left, s.getCount());
            s.decrement(take);
            left -= take;
        }
        RpgState state = RpgState.get(player.getServer());
        state.player(player).money += amount;
        state.markDirty();
        return true;
    }

    /** Retire {@code amount} pièces du compte vers l'inventaire (au sol si plein). */
    public static boolean withdraw(ServerPlayerEntity player, long amount) {
        RpgState state = RpgState.get(player.getServer());
        PlayerData data = state.player(player);
        if (amount <= 0 || data.money < amount) return false;
        data.money -= amount;
        state.markDirty();
        long left = amount;
        while (left > 0) {
            int n = (int) Math.min(64, left);
            ItemStack stack = new ItemStack(fr.serveur.rpg.items.ModItems.PIECE_OR, n);
            if (!player.getInventory().insertStack(stack)) player.dropItem(stack, false);
            left -= n;
        }
        return true;
    }

    /** Crédite un compte (joueur en ligne ou non). */
    public static void credit(MinecraftServer server, UUID id, long amount) {
        RpgState state = RpgState.get(server);
        PlayerData data = state.players.get(id);
        if (data == null) return;
        data.money += amount;
        state.markDirty();
    }

    /** Envoie un message au joueur, ou le garde pour sa prochaine connexion. */
    public static void notify(MinecraftServer server, UUID id, String message) {
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(id);
        if (online != null) {
            Msg.send(online, Text.literal(message).formatted(Formatting.GOLD));
        } else {
            RpgState state = RpgState.get(server);
            PlayerData data = state.players.get(id);
            if (data != null) {
                data.pendingMessages.add(message);
                state.markDirty();
            }
        }
    }
}
