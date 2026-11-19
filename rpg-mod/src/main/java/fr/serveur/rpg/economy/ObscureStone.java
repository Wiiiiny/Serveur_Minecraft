package fr.serveur.rpg.economy;

import fr.serveur.rpg.Msg;
import fr.serveur.rpg.data.PlayerData;
import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.tools.OfficialTools;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Pierre obscure (minerai violet) et la Pioche des ténèbres :
 * pioche en Netherite incassable, donnée à la première connexion, jamais perdue à la mort,
 * et qui ne récolte que la Pierre obscure.
 */
public final class ObscureStone {
    private ObscureStone() {}

    public static boolean isObscureStone(BlockState state) {
        return state.isOf(fr.serveur.rpg.blocks.ModBlocks.PIERRE_OBSCURE);
    }

    public static ItemStack createPickaxe() {
        return new ItemStack(fr.serveur.rpg.items.ModItems.PIOCHE_TENEBRES);
    }

    public static boolean isPickaxe(ItemStack stack) {
        if (stack.isOf(fr.serveur.rpg.items.ModItems.PIOCHE_TENEBRES)) return true;
        NbtCompound tag = stack.getSubNbt(OfficialTools.NBT_KEY); // anciennes pioches (Netherite + NBT)
        return tag != null && "pierre".equals(tag.getString("type"));
    }

    public static void give(ServerPlayerEntity player) {
        ItemStack pick = createPickaxe();
        if (!player.getInventory().insertStack(pick)) player.dropItem(pick, false);
    }

    public static void register() {
        // Première connexion : on donne la pioche. Et on affiche les messages en attente.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            ServerPlayerEntity player = handler.player;
            RpgState state = RpgState.get(server);
            PlayerData data = state.player(player);
            if (!data.starterGiven) {
                give(player);
                data.starterGiven = true;
                state.markDirty();
                Msg.send(player, Text.literal("Bienvenue ! Voici ta Pioche des ténèbres : elle récolte la Pierre obscure (minerai violet), "
                        + "4 pierres = 1 Pièce d'or à l'établi. Appuie sur B pour ouvrir ton menu et choisir ton métier.").formatted(Formatting.GOLD));
            }
            if (!data.pendingMessages.isEmpty()) {
                data.pendingMessages.forEach(m -> Msg.send(player, Text.literal(m).formatted(Formatting.GOLD)));
                data.pendingMessages.clear();
                state.markDirty();
            }
        }));
        // La conservation à la mort est gérée par DeathSystem (objets « liés à l'âme »).
    }

    public static void returnKeptItems(ServerPlayerEntity player) {
        RpgState state = RpgState.get(player.getServer());
        PlayerData data = state.player(player);
        if (data.keptItems.isEmpty()) return;
        for (NbtCompound n : data.keptItems) {
            ItemStack s = ItemStack.fromNbt(n);
            if (!player.getInventory().insertStack(s)) player.dropItem(s, false);
        }
        data.keptItems.clear();
        state.markDirty();
    }
}
