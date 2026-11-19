package fr.serveur.rpg.network;

import fr.serveur.rpg.SurvieRpg;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Échanges avec l'interface du joueur (touche B) :
 * - client → serveur « survierpg:action » : une action à faire ({a: "...", ...}) ;
 * - serveur → client « survierpg:data » : l'état complet à afficher, et le résultat de l'action.
 * Le serveur vérifie tout : le client ne fait qu'afficher et demander.
 */
public final class RpgNetwork {
    public static final Identifier ACTION = new Identifier(SurvieRpg.MOD_ID, "action");
    public static final Identifier DATA = new Identifier(SurvieRpg.MOD_ID, "data");

    private RpgNetwork() {}

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ACTION, (server, player, handler, buf, responseSender) -> {
            NbtCompound nbt = buf.readNbt();
            if (nbt != null) server.execute(() -> Actions.handle(player, nbt));
        });
    }

    /** Envoie l'état à jour au joueur, avec un message de résultat (peut être null). */
    public static void send(ServerPlayerEntity player, String message, boolean ok) {
        if (!ServerPlayNetworking.canSend(player, DATA)) return;
        NbtCompound snapshot = Snapshot.build(player);
        if (message != null) {
            snapshot.putString("msg", message);
            snapshot.putBoolean("ok", ok);
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeNbt(snapshot);
        ServerPlayNetworking.send(player, DATA, buf);
    }
}
