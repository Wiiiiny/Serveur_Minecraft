package fr.serveur.rpg.client;

import net.minecraft.client.gui.DrawContext;

/** Palette « obsidienne » et éléments de dessin de l'interface Survie RPG. */
public final class Ui {
    // Fonds
    public static final int CARD = 0xFF1C1527, CARD_HI = 0xFF271D37, WELL = 0xFF0C0911;
    // Traits
    public static final int LINE = 0xFF3A2D52, LINE_SOFT = 0xFF2A2039;
    // Or et violet
    public static final int GOLD = 0xFFE2BA5A, GOLD_DIM = 0xFF84692E, VIOLET = 0xFFA06AFF, VIOLET_DIM = 0xFF4A2F7E;
    // Texte
    public static final int TEXT = 0xFFEEE8F8, MUTED = 0xFF9E95B4, FAINT = 0xFF655C78;
    // États
    public static final int RED = 0xFFEA6B62, GREEN = 0xFF79DA96, ORANGE = 0xFFF0A24B;

    private Ui() {}

    public static void border(DrawContext c, int x, int y, int w, int h, int color) {
        c.fill(x, y, x + w, y + 1, color);
        c.fill(x, y + h - 1, x + w, y + h, color);
        c.fill(x, y, x + 1, y + h, color);
        c.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** Fenêtre principale : ombre, fond obsidienne dégradé, double liseré or/violet, coins ornés. */
    public static void frame(DrawContext c, int x, int y, int w, int h) {
        c.fill(x + 4, y + 4, x + w + 4, y + h + 4, 0x66000000);
        c.fillGradient(x, y, x + w, y + h, 0xF71A1124, 0xF70A0710);
        border(c, x, y, w, h, GOLD_DIM);
        border(c, x + 2, y + 2, w - 4, h - 4, 0xFF2B1F40);
        int[][] corners = {{x, y}, {x + w - 1, y}, {x, y + h - 1}, {x + w - 1, y + h - 1}};
        for (int[] p : corners) gem(c, p[0], p[1], GOLD);
    }

    /** Petit losange (ornement). */
    public static void gem(DrawContext c, int cx, int cy, int color) {
        c.fill(cx - 1, cy - 2, cx + 2, cy + 3, color);
        c.fill(cx - 2, cy - 1, cx + 3, cy + 2, color);
        c.fill(cx, cy - 1, cx + 1, cy + 2, 0xFFFFF1C2);
    }

    public static void card(DrawContext c, int x, int y, int w, int h, boolean highlight) {
        c.fill(x, y, x + w, y + h, highlight ? CARD_HI : CARD);
        border(c, x, y, w, h, highlight ? GOLD_DIM : LINE);
    }

    public static void cardTinted(DrawContext c, int x, int y, int w, int h, int borderColor) {
        c.fill(x, y, x + w, y + h, CARD);
        border(c, x, y, w, h, borderColor);
    }

    /** Barre de progression violette (dégradé) dans un creux sombre. */
    public static void bar(DrawContext c, int x, int y, int w, int h, float progress, int top, int bottom) {
        c.fill(x, y, x + w, y + h, WELL);
        border(c, x, y, w, h, LINE);
        int fw = (int) ((w - 2) * Math.max(0f, Math.min(1f, progress)));
        if (fw > 0) {
            c.fillGradient(x + 1, y + 1, x + 1 + fw, y + h - 1, top, bottom);
            c.fill(x + 1, y + 1, x + 1 + fw, y + 2, 0x40FFFFFF);
        }
    }

    /** Séparateur horizontal or qui s'estompe. */
    public static void rule(DrawContext c, int x, int y, int w) {
        c.fill(x, y, x + w, y + 1, LINE_SOFT);
        c.fill(x, y, x + Math.min(w, 40), y + 1, GOLD_DIM);
    }

    public static String number(long n) {
        return String.format("%,d", n).replace(',', ' ').replace(' ', ' ');
    }

    public static String duration(long seconds) {
        if (seconds < 0) return "—";
        long h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        if (h > 0) return h + " h " + String.format("%02d", m);
        if (m > 0) return m + " min " + String.format("%02d", s);
        return s + " s";
    }
}
