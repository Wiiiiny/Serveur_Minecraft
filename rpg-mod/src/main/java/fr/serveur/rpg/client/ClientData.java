package fr.serveur.rpg.client;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Util;

/** Dernier état reçu du serveur, et le message de résultat à afficher. */
public final class ClientData {
    public static NbtCompound data;
    public static String toast;
    public static boolean toastOk;
    public static long toastUntil;

    private ClientData() {}

    static void update(NbtCompound nbt) {
        data = nbt;
        if (nbt.contains("msg")) {
            toast = nbt.getString("msg");
            toastOk = nbt.getBoolean("ok");
            toastUntil = Util.getMeasuringTimeMs() + 3500;
        }
    }
}
