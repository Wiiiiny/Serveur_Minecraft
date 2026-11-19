package fr.serveur.rpg.client;

import fr.serveur.rpg.network.RpgNetwork;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import org.lwjgl.glfw.GLFW;

/** Côté client : touche B (modifiable dans Options > Commandes) et réception des données. */
public class SurvieRpgClient implements ClientModInitializer {
    public static KeyBinding OPEN_MENU;

    @Override
    public void onInitializeClient() {
        OPEN_MENU = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.survierpg.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, "category.survierpg"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_MENU.wasPressed()) {
                if (client.player != null && client.currentScreen == null) {
                    client.setScreen(new RpgScreen());
                    send("open", null);
                }
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(RpgNetwork.DATA, (client, handler, buf, responseSender) -> {
            NbtCompound nbt = buf.readNbt();
            if (nbt != null) client.execute(() -> ClientData.update(nbt));
        });

        // Captures automatiques de l'interface, pour le développement uniquement.
        if (Boolean.getBoolean("survierpg.devshots")) DevShots.register();
    }

    /** Envoie une action au serveur. */
    public static void send(String action, NbtCompound params) {
        if (!ClientPlayNetworking.canSend(RpgNetwork.ACTION)) return;
        NbtCompound n = params == null ? new NbtCompound() : params.copy();
        n.putString("a", action);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeNbt(n);
        ClientPlayNetworking.send(RpgNetwork.ACTION, buf);
    }
}
