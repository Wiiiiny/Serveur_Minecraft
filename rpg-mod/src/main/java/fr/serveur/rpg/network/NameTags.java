package fr.serveur.rpg.network;

import fr.serveur.rpg.SurvieRpg;
import fr.serveur.rpg.data.PlayerData;
import fr.serveur.rpg.data.RpgState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Métiers affichés sous le pseudo : le serveur envoie à tous les clients la liste
 * « joueur → métier principal + métier supplémentaire » dès qu'elle change (vérifié toutes les 2 s)
 * et à chaque connexion. Le rendu est fait côté client (mixin.client.PlayerEntityRendererMixin).
 */
public final class NameTags {
    public static final Identifier TAGS = new Identifier(SurvieRpg.MOD_ID, "tags");
    private static NbtCompound lastSent;
    private static long ticks;

    private NameTags() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++ticks % 40 != 0) return;
            NbtCompound payload = build(server);
            if (payload.equals(lastSent)) return;
            lastSent = payload;
            server.getPlayerManager().getPlayerList().forEach(p -> send(p, payload));
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            NbtCompound payload = build(server);
            lastSent = payload;
            server.getPlayerManager().getPlayerList().forEach(p -> send(p, payload));
        }));
    }

    private static NbtCompound build(MinecraftServer server) {
        RpgState state = RpgState.get(server);
        NbtList list = new NbtList();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            PlayerData d = state.player(p);
            NbtCompound c = new NbtCompound();
            c.putUuid("u", p.getUuid());
            c.putString("j", d.job == null ? "" : d.job.name());
            c.putString("s", d.sideJob == null ? "" : d.sideJob.name());
            list.add(c);
        }
        NbtCompound root = new NbtCompound();
        root.put("tags", list);
        return root;
    }

    private static void send(ServerPlayerEntity player, NbtCompound payload) {
        if (!ServerPlayNetworking.canSend(player, TAGS)) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeNbt(payload);
        ServerPlayNetworking.send(player, TAGS, buf);
    }
}
