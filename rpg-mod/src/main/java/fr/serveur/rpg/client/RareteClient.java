package fr.serveur.rpg.client;

import fr.serveur.rpg.sombre.Rarete;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;

import java.util.HashMap;
import java.util.Map;

/**
 * Raretés côté client : table reçue du serveur à la connexion (survierpg:raretes), contour coloré autour des objets
 * (inventaires, barre d'objets, menu B) et ligne de rareté dans l'infobulle. Exotique : contour noir et étincelles jaunes.
 */
public final class RareteClient {
    private static Map<Item, Rarete> table = Map.of();
    private static final int SPARK = 0xFFFFD84A, SPARK_DIM = 0x90FFB000;

    private RareteClient() {}

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
            Rarete r = of(stack);
            if (r == null || lines.isEmpty()) return;
            if (!stack.hasCustomName()) lines.set(0, lines.get(0).copy().formatted(r.text));
            lines.add(Math.min(1, lines.size()), Text.literal("◆ " + r.nom).formatted(r.text));
        });
    }

    public static void update(NbtCompound n) {
        Map<String, String> raw = new HashMap<>();
        for (String k : n.getKeys()) raw.put(k, n.getString(k));
        table = Rarete.table(raw);
    }

    public static void clear() {
        table = Map.of();
    }

    public static Rarete of(ItemStack stack) {
        return Rarete.of(stack, table);
    }

    /** Contour d'un emplacement d'objet 16 × 16 dont le coin est en (x, y). */
    public static void outline(DrawContext c, ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return;
        Rarete r = of(stack);
        if (r == null) return;
        c.getMatrices().push();
        c.getMatrices().translate(0, 0, 180);
        int col = r.color;
        c.fill(x, y, x + 16, y + 1, col);
        c.fill(x, y + 15, x + 16, y + 16, col);
        c.fill(x, y + 1, x + 1, y + 15, col);
        c.fill(x + 15, y + 1, x + 16, y + 15, col);
        if (r == Rarete.EXOTIQUE) {
            // Deux étincelles jaunes qui font le tour du contour, et quelques points qui scintillent.
            long t = Util.getMeasuringTimeMs();
            for (int k = 0; k < 2; k++) {
                int p = (int) ((t / 45 + k * 30) % 60);
                int[] a = perimeter(p), b = perimeter((p + 59) % 60);
                c.fill(x + b[0], y + b[1], x + b[0] + 1, y + b[1] + 1, SPARK_DIM);
                c.fill(x + a[0], y + a[1], x + a[0] + 1, y + a[1] + 1, SPARK);
            }
            int twinkle = (int) ((t / 120) % 4);
            int[][] dots = {{3, 3}, {12, 4}, {4, 12}, {11, 11}};
            c.fill(x + dots[twinkle][0], y + dots[twinkle][1], x + dots[twinkle][0] + 1, y + dots[twinkle][1] + 1, SPARK_DIM);
        }
        c.getMatrices().pop();
    }

    /** Point n° p (0-59) sur le bord d'un carré de 16 pixels, dans le sens des aiguilles d'une montre. */
    private static int[] perimeter(int p) {
        if (p < 15) return new int[]{p, 0};
        if (p < 30) return new int[]{15, p - 15};
        if (p < 45) return new int[]{15 - (p - 30), 15};
        return new int[]{0, 15 - (p - 45)};
    }
}
