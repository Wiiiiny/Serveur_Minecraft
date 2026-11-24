package fr.serveur.rpg.client;

import fr.serveur.rpg.items.ModItems;
import fr.serveur.rpg.jobs.Job;
import fr.serveur.rpg.jobs.SideJob;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import fr.serveur.rpg.faction.Faction;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Language;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Interface Survie RPG (touche B) : profil, quêtes, forge, banque, marché, boutique, classements,
 * et tableau de bord du GM pour les opérateurs. Dessinée à la main (style obsidienne et or).
 * Elle n'affiche que ce que le serveur envoie et ne fait que lui demander des actions.
 */
public class RpgScreen extends Screen {
    enum Tab {
        PROFIL("Profil"), PERSO("Personnage"), FACTION("Faction"), METIER2("2e métier"), QUETES("Quêtes"), FORGE("Forge"), BANQUE("Banque"), MARCHE("Marché"),
        BOUTIQUE("Boutique"), RAID("Raid"), CLASSEMENT("Classements"), GM("Maître du jeu");
        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    enum Style { NORMAL, PRIMARY, DANGER }

    private record Hot(int x, int y, int w, int h, Runnable action) {}

    // État conservé entre deux ouvertures
    private static Tab tab = Tab.PROFIL;
    private static String gmSub = "joueurs", rankCat = "metiers", selectedPlayer = "";
    private static int page, gmPage, zonePage, shopPage;
    private static String amountText = "", nameText = "", transferText = "", priceText = "", gmPriceText = "";

    private final List<Hot> hots = new ArrayList<>();
    private ItemStack hoverStack = ItemStack.EMPTY;
    private List<Text> hoverText;
    private int mx, my, x0, y0, w, h, cx, cy, cw, ch, refresh;
    /** Échelle d'affichage (< 1 si l'écran est trop petit pour l'interface). */
    private float scale = 1f;
    private Job pendingJob;
    private SideJob pendingSide;
    /** Champs de saisie du tableau de bord GM, par clé (réglage, article, joueur...). */
    private final Map<String, TextFieldWidget> valueFields = new HashMap<>();
    private final Map<String, String> syncedValues = new HashMap<>();
    private final Set<String> seenFields = new HashSet<>();
    private boolean confirmPrestige;
    /** Lore personnel : brouillon conservé entre deux ouvertures, chargé une fois depuis le serveur. */
    private static String loreDraft;
    private static Boolean lorePublicDraft;
    /** Vrai quand le brouillon diffère de ce qui est enregistré (on ne l'écrase pas en rouvrant l'onglet). */
    private static boolean loreDirty;
    /** Dernière histoire reçue du serveur (ou envoyée), pour ne recharger la zone que si elle a changé côté serveur. */
    private static String loreServer;
    private EditBoxWidget loreBox;

    public RpgScreen() {
        super(Text.literal("Survie RPG"));
    }

    /** Outil de développement (DevShots) : sous-page de l'onglet Faction. */
    static void devFaction(String sub) {
        facSub = sub;
        bnBase = -1;
    }

    /** Outil de développement (DevShots) : choisit l'onglet à ouvrir. */
    static void devSelect(Tab t, String sub) {
        tab = t;
        if (sub != null) gmSub = sub;
    }

    // ================================================================ cycle de vie

    @Override
    protected void init() {
        // Interface dessinée à taille fixe (440 x 272), réduite si l'écran est trop petit.
        scale = Math.min(1f, Math.min((width - 8) / 440f, (height - 8) / 272f));
        w = 440;
        h = 272;
        x0 = (int) ((width / scale - w) / 2);
        y0 = (int) ((height / scale - h) / 2);
        cx = x0 + 106;
        cy = y0 + 32;
        cw = w - 114;
        ch = h - 40;
        if (tab == Tab.GM && !isGm()) tab = Tab.PROFIL;
        valueFields.clear();
        syncedValues.clear();
        loreBox = null;
        switch (tab) {
            case BANQUE -> {
                field(cx + 8, cy + 86, 64, amountText, true, s -> amountText = s, "Montant");
                field(cx + 8, cy + 142, 104, nameText, false, s -> nameText = s, "Pseudo");
                field(cx + 118, cy + 142, 56, transferText, true, s -> transferText = s, "Montant");
            }
            case MARCHE -> field(cx + cw - 116, cy + ch - 18, 50, priceText, true, s -> priceText = s, "Prix");
            case PERSO -> {
                loreBox = new LoreBox(textRenderer, cx, cy + LORE_Y, cw, ch - LORE_Y,
                        Text.literal("Écris ici l'histoire de ton personnage : origines, quête, secrets…"), Text.literal("Lore"));
                loreBox.setMaxLength(LORE_MAX);
                if (ClientData.data != null) loreServer = ClientData.data.getCompound("me").getString("lore");
                if (!loreDirty && loreServer != null) loreDraft = loreServer;
                loreBox.setText(loreDraft == null ? "" : loreDraft);
                loreBox.setChangeListener(s -> {
                    loreDraft = s;
                    loreDirty = true;
                });
                addDrawableChild(loreBox);
            }
            case GM -> {
                if ("economie".equals(gmSub)) field(cx + cw - 116, cy + ch - 18, 50, gmPriceText, true, s -> gmPriceText = s, "Prix");
            }
            default -> { }
        }
    }

    private void field(int x, int y, int fw, String value, boolean digits, Consumer<String> onChange, String placeholder) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, fw, 16, Text.literal(placeholder));
        f.setMaxLength(digits ? 9 : 16);
        if (digits) f.setTextPredicate(s -> s.matches("\\d*"));
        f.setText(value);
        f.setPlaceholder(Text.literal(placeholder).formatted(Formatting.DARK_GRAY));
        f.setChangedListener(onChange);
        addDrawableChild(f);
    }

    private void switchTab(Tab t) {
        tab = t;
        page = 0;
        pendingJob = null;
        pendingSide = null;
        confirmPrestige = false;
        clearAndInit();
    }

    @Override
    public void tick() {
        for (Element e : children()) if (e instanceof TextFieldWidget f) f.tick();
        if (loreBox != null) loreBox.tick();
        if (++refresh % 40 == 0) SurvieRpgClient.send("refresh", null);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((getFocused() instanceof TextFieldWidget f && f.isFocused()) || (getFocused() instanceof EditBoxWidget e && e.isFocused())) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                close();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (SurvieRpgClient.OPEN_MENU.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(mouseX / scale, mouseY / scale, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        return super.mouseDragged(mouseX / scale, mouseY / scale, button, dx / scale, dy / scale);
    }

    @Override
    public boolean mouseClicked(double rawX, double rawY, int button) {
        double mouseX = rawX / scale, mouseY = rawY / scale;
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        for (Hot hot : List.copyOf(hots)) {
            if (mouseX >= hot.x && mouseX < hot.x + hot.w && mouseY >= hot.y && mouseY < hot.y + hot.h) {
                if (client != null) client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1f));
                hot.action.run();
                return true;
            }
        }
        return false;
    }

    // ================================================================ rendu

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        hots.clear();
        seenFields.clear();
        hoverStack = ItemStack.EMPTY;
        hoverText = null;
        renderBackground(c);
        c.getMatrices().push();
        c.getMatrices().scale(scale, scale, 1f);
        mx = (int) (mouseX / scale);
        my = (int) (mouseY / scale);
        Ui.frame(c, x0, y0, w, h);
        NbtCompound d = ClientData.data;
        header(c, d);
        sidebar(c, d);
        if (d == null) {
            centered(c, "Chargement…", cx + cw / 2, cy + ch / 2, Ui.MUTED);
        } else {
            switch (tab) {
                case PROFIL -> profil(c, d);
                case PERSO -> perso(c, d);
                case FACTION -> faction(c, d);
                case METIER2 -> metier2(c, d);
                case QUETES -> quetes(c, d);
                case FORGE -> forge(c, d);
                case BANQUE -> banque(c, d);
                case MARCHE -> marche(c, d);
                case BOUTIQUE -> boutique(c, d);
                case RAID -> raid(c, d);
                case CLASSEMENT -> classement(c, d);
                case GM -> gm(c, d);
            }
        }
        // Les champs qui n'ont pas été dessinés ce tour-ci (autre page, autre sous-onglet) sont cachés.
        valueFields.forEach((key, f) -> {
            if (!seenFields.contains(key)) {
                f.visible = false;
                f.setFocused(false);
            }
        });
        super.render(c, mx, my, delta);
        if (!hoverStack.isEmpty()) c.drawItemTooltip(textRenderer, hoverStack, mx, my);
        else if (hoverText != null) c.drawTooltip(textRenderer, hoverText, mx, my);
        c.getMatrices().pop();
    }

    private void header(DrawContext c, NbtCompound d) {
        c.fill(x0 + 3, y0 + 3, x0 + w - 3, y0 + 25, 0x38000000);
        Ui.gem(c, x0 + 12, y0 + 13, Ui.VIOLET);
        c.drawText(textRenderer, "SURVIE RPG", x0 + 20, y0 + 9, Ui.GOLD, true);
        if (d != null) {
            NbtCompound me = d.getCompound("me");
            int tx = x0 + 22 + textRenderer.getWidth("SURVIE RPG");
            c.drawText(textRenderer, "· " + me.getString("name"), tx, y0 + 9, Ui.MUTED, false);
            String money = Ui.number(me.getLong("money"));
            int mw = textRenderer.getWidth(money);
            c.drawItem(new ItemStack(ModItems.PIECE_OR), x0 + w - 26 - mw, y0 + 5);
            c.drawText(textRenderer, money, x0 + w - 8 - mw, y0 + 9, Ui.GOLD, true);
            boolean night = d.getCompound("world").getBoolean("night");
            String moment = night ? "☾ Nuit" : "☀ Jour";
            c.drawText(textRenderer, moment, x0 + w - 34 - mw - textRenderer.getWidth(moment), y0 + 9, night ? Ui.VIOLET : Ui.ORANGE, false);
        }
        if (ClientData.toast != null && Util.getMeasuringTimeMs() < ClientData.toastUntil) {
            String s = textRenderer.trimToWidth(ClientData.toast, w - 270);
            int tx = x0 + w / 2 - textRenderer.getWidth(s) / 2 + 20;
            c.drawText(textRenderer, s, tx, y0 + 9, ClientData.toastOk ? Ui.GREEN : Ui.RED, true);
        }
        Ui.rule(c, x0 + 6, y0 + 26, w - 12);
    }

    private void sidebar(DrawContext c, NbtCompound d) {
        int sx = x0 + 8, sy = y0 + 32;
        c.fill(sx - 2, sy - 2, sx + 92, y0 + h - 8, 0x30000000);
        for (Tab t : Tab.values()) {
            if (t == Tab.GM && !isGm()) continue;
            boolean active = t == tab;
            boolean hover = in(sx, sy, 90, 15);
            if (active) {
                c.fillGradient(sx, sy, sx + 90, sy + 15, 0xFF3D2765, 0xFF221539);
                c.fill(sx, sy, sx + 2, sy + 15, Ui.GOLD);
            } else if (hover) {
                c.fill(sx, sy, sx + 90, sy + 15, 0xFF231A31);
            }
            int color = t == Tab.GM ? (active ? Ui.GOLD : Ui.ORANGE) : active ? Ui.GOLD : hover ? Ui.TEXT : Ui.MUTED;
            c.drawText(textRenderer, t.label, sx + 8, sy + 4, color, active);
            if (!active) hots.add(new Hot(sx, sy, 90, 15, () -> switchTab(t)));
            sy += 16;
        }
        if (d == null) return;
        NbtCompound world = d.getCompound("world");
        int by = y0 + h - 44;
        if (world.contains("eventTitle")) {
            Ui.cardTinted(c, sx, by, 90, 34, Ui.VIOLET_DIM);
            c.drawText(textRenderer, "ÉVÉNEMENT", sx + 5, by + 4, Ui.VIOLET, false);
            c.drawText(textRenderer, textRenderer.trimToWidth(world.getString("eventTitle"), 80), sx + 5, by + 14, Ui.TEXT, false);
            c.drawText(textRenderer, Ui.duration(world.getLong("eventLeft")), sx + 5, by + 24, Ui.MUTED, false);
        } else if (world.getLong("nextEvent") >= 0) {
            c.drawText(textRenderer, "Prochain événement", sx + 4, by + 12, Ui.FAINT, false);
            c.drawText(textRenderer, "dans " + Ui.duration(world.getLong("nextEvent")), sx + 4, by + 23, Ui.MUTED, false);
        }
    }

    // ================================================================ onglets joueur

    private void profil(DrawContext c, NbtCompound d) {
        NbtCompound me = d.getCompound("me");
        Job job = Job.parse(me.getString("job"));
        int level = me.getInt("level"), max = d.getInt("maxLevel"), price = d.getInt("jobPrice");
        int y = cy;
        if (me.getBoolean("awaitingRevive")) {
            Ui.cardTinted(c, cx, y, cw, 44, Ui.RED);
            c.drawText(textRenderer, "☠ Mort pendant le jour", cx + 8, y + 5, Ui.RED, true);
            c.drawText(textRenderer, "Tu reviendras à la tombée de la nuit.", cx + 8, y + 15, Ui.MUTED, false);
            int bw = (cw - 24) / 3, tears = me.getInt("tears");
            button(c, cx + 6, y + 26, bw, 14, "Spectateur", true, Style.NORMAL, () -> act("death.spectator"));
            button(c, cx + 12 + bw, y + 26, bw, 14, "Salle d'attente", true, Style.NORMAL, () -> act("death.room"));
            button(c, cx + 18 + 2 * bw, y + 26, bw, 14, "Larme de vie (" + tears + ")", tears > 0, Style.PRIMARY, () -> act("death.tear"));
            y += 50;
        }

        Ui.card(c, cx, y, cw, 56, true);
        if (job != null) {
            item(c, toolIcon(job), cx + 8, y + 8);
            scaled(c, Text.literal(job.displayName), cx + 30, y + 8, 1.5f, jobColor(job));
            textRight(c, "Niveau " + level + " / " + max, cx + cw - 8, y + 8, Ui.TEXT);
            int prestige = me.getInt("prestige");
            if (prestige > 0) textRight(c, "★ Prestige " + prestige, cx + cw - 8, y + 19, Ui.GOLD);
            boolean maxed = level >= max;
            float progress = maxed ? 1f : me.getInt("xp") / (float) Math.max(1, me.getInt("xpNeeded"));
            Ui.bar(c, cx + 8, y + 31, cw - 16, 9, progress, 0xFFC39BFF, 0xFF6936C9);
            c.drawText(textRenderer, maxed ? "Niveau maximum — le prestige est disponible" : me.getInt("xp") + " / " + me.getInt("xpNeeded") + " XP",
                    cx + 8, y + 44, Ui.MUTED, false);
        } else {
            scaled(c, Text.literal("Sans métier"), cx + 10, y + 8, 1.5f, Ui.TEXT);
            c.drawText(textRenderer, "Choisis ton métier ci-dessous : c'est gratuit,", cx + 10, y + 27, Ui.MUTED, false);
            c.drawText(textRenderer, "et ton outil en bois t'est donné aussitôt.", cx + 10, y + 38, Ui.MUTED, false);
        }
        y += 62;

        String[][] stats = {
                {Ui.number(me.getLong("money")), "en banque"}, {Ui.number(me.getInt("coinsOnHand")), "sur toi"},
                {String.valueOf(me.getInt("pvpKills")), "victimes"}, {String.valueOf(me.getInt("mobKills")), "monstres"},
                {String.valueOf(me.getInt("deaths")), "morts"}};
        int sw = (cw - 16) / 5;
        for (int i = 0; i < stats.length; i++) {
            int sx = cx + i * (sw + 4);
            Ui.card(c, sx, y, sw, 27, false);
            centered(c, stats[i][0], sx + sw / 2, y + 5, i < 2 ? Ui.GOLD : Ui.TEXT);
            centered(c, textRenderer.trimToWidth(stats[i][1], sw - 4), sx + sw / 2, y + 16, Ui.FAINT);
        }
        y += 34;

        boolean first = job == null;
        c.drawText(textRenderer, first ? "Choisir un métier — gratuit" : "Changer de métier — " + price + " pièces", cx, y, Ui.GOLD, false);
        if (job != null && level >= max) {
            button(c, cx + cw - 96, y - 4, 96, 14, confirmPrestige ? "Confirmer ★" : "Passer prestige ★", true, Style.PRIMARY, () -> {
                if (confirmPrestige) {
                    act("prestige");
                    confirmPrestige = false;
                } else confirmPrestige = true;
            });
        }
        y += 13;
        int jw = (cw - 12) / 4;
        Job[] jobs = Job.values();
        for (int i = 0; i < jobs.length; i++) {
            Job j = jobs[i];
            jobButton(c, cx + i * (jw + 4), y, jw, 24, j, j == job, () -> {
                if (first) act("job.choose", "job", j.name());
                else pendingJob = j;
            });
        }
        y += 30;
        if (pendingJob != null) {
            c.drawText(textRenderer, "Devenir " + pendingJob.displayName + " pour " + price + " pièces ?", cx, y + 3, Ui.TEXT, false);
            Job target = pendingJob;
            button(c, cx + cw - 148, y, 72, 15, "Confirmer", true, Style.PRIMARY, () -> {
                act("job.choose", "job", target.name());
                pendingJob = null;
            });
            button(c, cx + cw - 72, y, 72, 15, "Annuler", true, Style.NORMAL, () -> pendingJob = null);
            return;
        }
        // Résumé : quêtes du jour et forge
        NbtList daily = d.getList("daily", NbtElement.COMPOUND_TYPE);
        int done = 0;
        for (int i = 0; i < daily.size(); i++) if (daily.getCompound(i).getBoolean("done")) done++;
        Ui.card(c, cx, y, cw, 22, false);
        c.drawText(textRenderer, "Quêtes du jour : " + done + " / " + daily.size() + " terminées", cx + 6, y + 7,
                done == daily.size() && !daily.isEmpty() ? Ui.GREEN : Ui.TEXT, false);
        button(c, cx + cw - 60, y + 4, 56, 14, "Voir", true, Style.NORMAL, () -> switchTab(Tab.QUETES));
        y += 24;
        SideJob side = SideJob.parse(me.getString("side"));
        Ui.card(c, cx, y, cw, 22, false);
        if (side != null) {
            c.drawItem(new ItemStack(side.icon()), cx + 4, y + 3);
            c.drawText(textRenderer, "2e métier : ", cx + 24, y + 7, Ui.TEXT, false);
            c.drawText(textRenderer, side.displayName + " niv. " + me.getInt("sideLevel"), cx + 24 + textRenderer.getWidth("2e métier : "), y + 7, sideColor(side), false);
        } else {
            c.drawText(textRenderer, "2e métier : aucun — le premier choix est gratuit", cx + 6, y + 7, Ui.MUTED, false);
        }
        button(c, cx + cw - 60, y + 4, 56, 14, side == null ? "Choisir" : "Voir", true, side == null ? Style.PRIMARY : Style.NORMAL,
                () -> switchTab(Tab.METIER2));
        NbtList forge = d.getList("forge", NbtElement.COMPOUND_TYPE);
        int ready = 0;
        for (int i = 0; i < forge.size(); i++) if (forge.getCompound(i).getBoolean("ok")) ready++;
        if (ready > 1) {
            y += 24;
            Ui.card(c, cx, y, cw, 22, false);
            c.drawText(textRenderer, "Tu peux forger " + ready + " outils officiels.", cx + 6, y + 7, Ui.GOLD, false);
            button(c, cx + cw - 60, y + 4, 56, 14, "Forge", true, Style.PRIMARY, () -> switchTab(Tab.FORGE));
        }
    }

    // ---------------------------------------------------------------- Personnage : niveau, Obscurcie, lumière, lore

    private static final int LORE_Y = 140, LORE_MAX = 1500;

    private void perso(DrawContext c, NbtCompound d) {
        NbtCompound me = d.getCompound("me");
        int y = cy;
        // Niveau du personnage
        int level = me.getInt("baseLevel"), max = me.getInt("baseMax");
        boolean maxed = level >= max;
        Ui.card(c, cx, y, cw, 40, true);
        scaled(c, Text.literal("Niveau " + level), cx + 8, y + 5, 1.4f, 0xFF7FE3F0);
        textRight(c, maxed ? "Niveau maximum" : me.getInt("baseXp") + " / " + me.getInt("baseXpNeeded") + " XP", cx + cw - 8, y + 6, Ui.TEXT);
        Ui.bar(c, cx + 8, y + 21, cw - 16, 7, maxed ? 1f : me.getInt("baseXp") / (float) Math.max(1, me.getInt("baseXpNeeded")), 0xFF9FF0F8, 0xFF2A8FA0);
        c.drawText(textRenderer, textRenderer.trimToWidth("Sans classe · mourir coûte " + Math.round(me.getDouble("deathLoss"))
                + " % de l'XP du niveau", cw - 16), cx + 8, y + 30, Ui.FAINT, false);
        if (in(cx, y, cw, 40)) hoverText = List.of(Text.literal("Niveau du personnage").formatted(Formatting.AQUA, Formatting.BOLD),
                Text.literal("Monstres, quêtes, coffres cachés, découvertes,").formatted(Formatting.GRAY),
                Text.literal("niveaux de métier et une part de l'XP des métiers.").formatted(Formatting.GRAY),
                Text.literal("Coffres ouverts : " + me.getInt("chestsOpened")).formatted(Formatting.DARK_GRAY));
        y += 44;
        // Obscurcie
        int obs = me.getInt("obsLevel");
        Ui.card(c, cx, y, cw, 44, false);
        c.drawItem(new ItemStack(fr.serveur.rpg.sombre.SombreItems.DETECTEUR), cx + 6, y + 5);
        scaled(c, Text.literal("Obscurcie " + obs), cx + 26, y + 6, 1.2f, 0xFFB98CFF);
        textRight(c, me.getInt("obsXp") + " / " + me.getInt("obsXpNeeded") + " XP · sans limite", cx + cw - 8, y + 7, Ui.MUTED);
        Ui.bar(c, cx + 8, y + 23, cw - 16, 7, me.getInt("obsXp") / (float) Math.max(1, me.getInt("obsXpNeeded")), 0xFFC9A6FF, 0xFF5B2CA8);
        c.drawText(textRenderer, textRenderer.trimToWidth("Niv. " + me.getInt("obsNextEclat") + " : " + me.getInt("eclatChance")
                + " % d'Éclat · niv. " + me.getInt("obsNextDetector") + " : détecteur de coffres", cw - 16), cx + 8, y + 33, Ui.FAINT, false);
        if (in(cx, y, cw, 44)) hoverText = List.of(Text.literal("Obscurcie").formatted(Formatting.DARK_PURPLE, Formatting.BOLD),
                Text.literal("Ressources, combats de nuit, quêtes, exploration").formatted(Formatting.GRAY),
                Text.literal("nocturne, Éclats, détecteurs et coffres cachés.").formatted(Formatting.GRAY));
        y += 48;
        // Lumière
        NbtCompound lum = d.getCompound("world").getCompound("lumiere");
        String phase = lum.getString("phase");
        long left = lum.getLong("left"), next = lum.getLong("next");
        int eclats = me.getInt("eclats");
        Ui.card(c, cx, y, cw, 26, false);
        String status;
        int color;
        switch (phase) {
            case "JOUR" -> { status = "☀ Jour · encore " + Ui.duration(left); color = Ui.ORANGE; }
            case "PREPARATION" -> { status = "☀ Le jour se lève dans " + left + " s"; color = Ui.GOLD; }
            case "NUIT" -> { status = next > 0 ? "☾ Nuit · Éclat possible dans " + Ui.duration(next) : "☾ Nuit permanente · Éclat possible"; color = Ui.VIOLET; }
            default -> { status = "Cycle jour/nuit normal"; color = Ui.MUTED; }
        }
        c.drawItem(new ItemStack(fr.serveur.rpg.sombre.SombreItems.ECLAT_LUMIERE), cx + 5, y + 5);
        c.drawText(textRenderer, textRenderer.trimToWidth(status, cw - 140), cx + 25, y + 9, color, false);
        if ("JOUR".equals(phase) && in(cx, y, cw - 112, 26)) hoverText = List.of(Text.literal("Jour invoqué par " + lum.getString("by")).formatted(Formatting.GOLD));
        boolean canUse = eclats > 0 && "NUIT".equals(phase) && next == 0;
        button(c, cx + cw - 110, y + 6, 104, 14, "Briser un Éclat (" + eclats + ")", canUse, Style.PRIMARY, () -> act("eclat.use"));
        y += 30;
        // Lore
        boolean pub = lorePublicDraft != null ? lorePublicDraft : me.getBoolean("lorePublic");
        // Données arrivées après l'ouverture de l'onglet : on affiche l'histoire enregistrée.
        if (!me.getString("lore").equals(loreServer)) {
            loreServer = me.getString("lore");
            if (!loreDirty && loreBox != null && !loreBox.isFocused()) {
                loreDraft = loreServer;
                loreBox.setText(loreDraft);
                loreDirty = false;
            }
        }
        c.drawText(textRenderer, "Mon histoire", cx, y + 3, Ui.GOLD, true);
        int len = loreDraft == null ? 0 : loreDraft.length();
        c.drawText(textRenderer, len + " / " + LORE_MAX, cx + 72, y + 3, Ui.FAINT, false);
        button(c, cx + cw - 156, y, 72, 14, pub ? "Publique" : "Privée", true, Style.NORMAL,
                () -> lorePublicDraft = !(lorePublicDraft != null ? lorePublicDraft : me.getBoolean("lorePublic")));
        if (in(cx + cw - 156, y, 72, 14)) hoverText = List.of(Text.literal(pub ? "Les autres joueurs peuvent la lire (/lore " + me.getString("name") + ")."
                : "Toi seul (et les GM) peux la lire.").formatted(Formatting.GRAY));
        button(c, cx + cw - 80, y, 80, 14, "Enregistrer", true, Style.PRIMARY, () -> {
            boolean p = lorePublicDraft != null ? lorePublicDraft : me.getBoolean("lorePublic");
            act("lore.save", "text", loreDraft == null ? "" : loreDraft, "public", p ? 1 : 0);
            lorePublicDraft = null;
            loreDirty = false;
            loreServer = loreDraft == null ? "" : loreDraft;
        });
    }

    /**
     * Zone de texte de l'histoire : la découpe (scissor) d'EditBoxWidget ignore la mise à l'échelle du menu, ce qui rognait
     * le texte ; on la recalcule avec la matrice courante. Le compteur de caractères est dessiné dans l'en-tête.
     */
    private static final class LoreBox extends EditBoxWidget {
        LoreBox(net.minecraft.client.font.TextRenderer tr, int x, int y, int w, int h, Text placeholder, Text message) {
            super(tr, x, y, w, h, placeholder, message);
        }

        @Override
        public void renderButton(DrawContext c, int mouseX, int mouseY, float delta) {
            if (!visible) return;
            drawBox(c);
            org.joml.Matrix4f m = c.getMatrices().peek().getPositionMatrix();
            org.joml.Vector3f a = m.transformPosition(new org.joml.Vector3f(getX() + 1, getY() + 1, 0));
            org.joml.Vector3f b = m.transformPosition(new org.joml.Vector3f(getX() + width - 1, getY() + height - 1, 0));
            c.enableScissor((int) Math.floor(a.x), (int) Math.floor(a.y), (int) Math.ceil(b.x), (int) Math.ceil(b.y));
            c.getMatrices().push();
            c.getMatrices().translate(0, -getScrollY(), 0);
            renderContents(c, mouseX, mouseY, delta);
            c.getMatrices().pop();
            c.disableScissor();
        }
    }

    private void metier2(DrawContext c, NbtCompound d) {
        NbtCompound me = d.getCompound("me");
        SideJob side = SideJob.parse(me.getString("side"));
        int level = me.getInt("sideLevel"), max = d.getInt("maxLevel"), price = d.getInt("sidePrice");
        boolean free = me.getBoolean("sideFree");
        NbtCompound rules = d.getCompound("sideRules");
        int y = cy;
        Ui.card(c, cx, y, cw, 56, true);
        if (side != null) {
            item(c, new ItemStack(side.icon()), cx + 8, y + 8);
            scaled(c, Text.literal(side.displayName), cx + 30, y + 8, 1.5f, sideColor(side));
            textRight(c, "Niveau " + level + " / " + max, cx + cw - 8, y + 8, Ui.TEXT);
            if (me.contains("beaconLeft")) {
                long left = me.getLong("beaconLeft");
                textRight(c, left > 0 ? "Balise : " + Ui.duration(left) : "Balise prête", cx + cw - 8, y + 19, left > 0 ? Ui.MUTED : Ui.GREEN);
            }
            boolean maxed = level >= max;
            float progress = maxed ? 1f : me.getInt("sideXp") / (float) Math.max(1, me.getInt("sideXpNeeded"));
            Ui.bar(c, cx + 8, y + 31, cw - 16, 9, progress, 0xFF9FE8EC, 0xFF2F8C96);
            c.drawText(textRenderer, maxed ? "Niveau maximum" : me.getInt("sideXp") + " / " + me.getInt("sideXpNeeded") + " XP",
                    cx + 8, y + 44, Ui.MUTED, false);
            if (in(cx, y, cw, 56)) hoverText = rulesTooltip(side, rules);
        } else {
            scaled(c, Text.literal("Aucun métier supplémentaire"), cx + 10, y + 8, 1.3f, Ui.TEXT);
            c.drawText(textRenderer, "En plus de ton métier principal, choisis une spécialité.", cx + 10, y + 27, Ui.MUTED, false);
            c.drawText(textRenderer, "Le premier choix est gratuit.", cx + 10, y + 38, Ui.MUTED, false);
        }
        y += 62;
        c.drawText(textRenderer, free ? "Choisir — gratuit" : "Changer — " + price + " pièces", cx, y, Ui.GOLD, false);
        textRight(c, "survole un métier pour ses règles", cx + cw, y, Ui.FAINT);
        y += 13;
        int bw = (cw - 4) / 2, bh = 30;
        SideJob[] all = SideJob.values();
        for (int i = 0; i < all.length; i++) {
            SideJob j = all[i];
            int bx = cx + (i % 2) * (bw + 4), by = y + (i / 2) * (bh + 3);
            boolean current = j == side, hover = !current && in(bx, by, bw, bh);
            c.fill(bx, by, bx + bw, by + bh, current ? 0xFF30224A : hover ? 0xFF2A2039 : Ui.CARD);
            Ui.border(c, bx, by, bw, bh, current ? Ui.GOLD : hover ? Ui.GOLD_DIM : Ui.LINE);
            c.drawItem(new ItemStack(j.icon()), bx + 6, by + 7);
            c.drawText(textRenderer, j.displayName, bx + 27, by + 5, sideColor(j), false);
            if (current) textRight(c, "actuel", bx + bw - 5, by + 5, Ui.GOLD);
            c.drawText(textRenderer, textRenderer.trimToWidth(j.tagline, bw - 31), bx + 27, by + 17, Ui.FAINT, false);
            if (in(bx, by, bw, bh)) hoverText = rulesTooltip(j, rules);
            if (!current) hots.add(new Hot(bx, by, bw, bh, () -> {
                if (free) act("side.choose", "job", j.name());
                else pendingSide = j;
            }));
        }
        y += 4 * (bh + 3) + 2;
        if (pendingSide != null) {
            c.drawText(textRenderer, "Devenir " + pendingSide.displayName + " pour " + price + " pièces ?", cx, y + 3, Ui.TEXT, false);
            SideJob target = pendingSide;
            button(c, cx + cw - 148, y, 72, 15, "Confirmer", true, Style.PRIMARY, () -> {
                act("side.choose", "job", target.name());
                pendingSide = null;
            });
            button(c, cx + cw - 72, y, 72, 15, "Annuler", true, Style.NORMAL, () -> pendingSide = null);
        } else if (side == SideJob.VOYAGEUR && !d.getBoolean("border")) {
            c.drawText(textRenderer, "Le GM n'a pas encore défini la zone jouable : ta balise attend.", cx, y + 3, Ui.ORANGE, false);
        }
    }

    /** Règles d'un métier supplémentaire (envoyées par le serveur, calculées depuis sa config). */
    private static List<Text> rulesTooltip(SideJob j, NbtCompound rules) {
        List<Text> lines = new ArrayList<>();
        lines.add(Text.literal(j.displayName).formatted(j.color, Formatting.BOLD));
        NbtList l = rules.getList(j.name(), NbtElement.STRING_TYPE);
        for (int i = 0; i < l.size(); i++) {
            Text t = Text.Serializer.fromJson(l.getString(i));
            if (t != null) lines.add(t.copy().formatted(Formatting.GRAY));
        }
        return lines;
    }

    private void quetes(DrawContext c, NbtCompound d) {
        int y = title(c, "Quêtes du jour", cy);
        textRight(c, "Nouvelles dans " + Ui.duration(d.getLong("dailyResetIn")), cx + cw, cy, Ui.FAINT);
        NbtList daily = d.getList("daily", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < daily.size(); i++) {
            NbtCompound q = daily.getCompound(i);
            boolean done = q.getBoolean("done");
            Ui.cardTinted(c, cx, y, cw, 30, done ? 0xFF3E7A52 : Ui.LINE);
            item(c, ItemStack.fromNbt(q.getCompound("icon")), cx + 6, y + 7);
            MutableText label = Text.translatable("quest.survierpg." + q.getString("kind"), q.getInt("count"), Text.translatable(q.getString("nameKey")));
            if (q.getBoolean("variants")) label.append(" ").append(Text.translatable("quest.survierpg.variants"));
            trimmed(c, label, cx + 28, y + 5, cw - 110, done ? Ui.GREEN : Ui.TEXT);
            int bw = cw - 150;
            Ui.bar(c, cx + 28, y + 18, bw, 6, q.getInt("progress") / (float) Math.max(1, q.getInt("count")), 0xFFC39BFF, 0xFF6936C9);
            c.drawText(textRenderer, Math.min(q.getInt("progress"), q.getInt("count")) + " / " + q.getInt("count"), cx + 32 + bw, y + 17, Ui.MUTED, false);
            if (done) {
                textRight(c, "✔ Terminée", cx + cw - 6, y + 11, Ui.GREEN);
            } else {
                textRight(c, "+" + q.getInt("coins") + " pièces", cx + cw - 6, y + 5, Ui.GOLD);
                if (q.getInt("xp") > 0) textRight(c, "+" + q.getInt("xp") + " XP", cx + cw - 6, y + 17, Ui.VIOLET);
            }
            y += 34;
        }
        y = title(c, "Quêtes du GM", y + 4);
        NbtList quests = d.getList("quests", NbtElement.COMPOUND_TYPE);
        if (quests.isEmpty()) c.drawText(textRenderer, "Aucune quête du GM pour l'instant.", cx + 4, y, Ui.FAINT, false);
        for (int i = 0; i < quests.size() && y < cy + ch - 10; i++) {
            NbtCompound q = quests.getCompound(i);
            boolean done = q.getBoolean("done");
            c.drawText(textRenderer, done ? "✔" : "◆", cx + 4, y, done ? Ui.GREEN : Ui.GOLD, false);
            c.drawText(textRenderer, textRenderer.trimToWidth("#" + q.getInt("id") + "  " + q.getString("text"), cw - 20), cx + 16, y, done ? Ui.FAINT : Ui.TEXT, false);
            y += 12;
        }
    }

    private void forge(DrawContext c, NbtCompound d) {
        NbtCompound me = d.getCompound("me");
        Job job = Job.parse(me.getString("job"));
        if (job == null) {
            centered(c, "Choisis d'abord un métier dans l'onglet Profil.", cx + cw / 2, cy + ch / 2, Ui.MUTED);
            return;
        }
        int y = title(c, "Forge — outils de " + job.displayName, cy);
        c.drawText(textRenderer, "Matériaux pris dans ton inventaire, pièces prises sur ton compte.", cx, y - 2, Ui.FAINT, false);
        y += 10;
        NbtList tiers = d.getList("forge", NbtElement.COMPOUND_TYPE);
        long money = me.getLong("money");
        for (int i = 0; i < tiers.size(); i++) {
            NbtCompound t = tiers.getCompound(i);
            boolean ok = t.getBoolean("ok");
            Ui.card(c, cx, y, cw, 34, ok);
            ItemStack tool = ItemStack.fromNbt(t.getCompound("item"));
            item(c, tool, cx + 6, y + 9);
            String tierId = ModItems.Tier.valueOf(t.getString("tier")).id;
            c.drawText(textRenderer, textRenderer.trimToWidth(job.toolName + " en " + tierId, 112), cx + 26, y + 6, Ui.TEXT, false);
            String lvl = "Niv. " + t.getInt("level");
            c.drawText(textRenderer, lvl, cx + 26, y + 20, me.getInt("level") >= t.getInt("level") ? Ui.GREEN : Ui.RED, false);
            int coins = t.getInt("coins");
            c.drawText(textRenderer, coins == 0 ? "· gratuit" : "· " + coins + " pièces", cx + 30 + textRenderer.getWidth(lvl), y + 20,
                    money >= coins ? Ui.GOLD : Ui.RED, false);
            NbtList mats = t.getList("mats", NbtElement.COMPOUND_TYPE);
            int mx0 = cx + 142;
            for (int m = 0; m < mats.size(); m++) {
                NbtCompound mat = mats.getCompound(m);
                item(c, ItemStack.fromNbt(mat.getCompound("icon")), mx0, y + 9);
                int have = mat.getInt("have"), need = mat.getInt("need");
                c.drawText(textRenderer, Math.min(have, 99) + "/" + need, mx0 + 17, y + 13, have >= need ? Ui.GREEN : Ui.RED, false);
                mx0 += 42;
            }
            String tierName = t.getString("tier");
            button(c, cx + cw - 56, y + 9, 50, 16, "Forger", ok, Style.PRIMARY, () -> act("forge", "tier", tierName));
            y += 37;
        }
    }

    private void banque(DrawContext c, NbtCompound d) {
        NbtCompound me = d.getCompound("me");
        int y = title(c, "Banque", cy);
        Ui.card(c, cx, y, cw, 46, true);
        c.getMatrices().push();
        c.getMatrices().translate(cx + 8, y + 7, 0);
        c.getMatrices().scale(2f, 2f, 1f);
        c.drawItem(new ItemStack(ModItems.PIECE_OR), 0, 0);
        c.getMatrices().pop();
        scaled(c, Text.literal(Ui.number(me.getLong("money")) + " pièces"), cx + 46, y + 9, 1.6f, Ui.GOLD);
        int onHand = me.getInt("coinsOnHand");
        c.drawText(textRenderer, "Sur toi : " + onHand + " pièce" + (onHand > 1 ? "s" : ""), cx + 46, y + 30, Ui.MUTED, false);
        button(c, cx + cw - 104, y + 15, 96, 16, "Tout déposer", onHand > 0, Style.PRIMARY, () -> act("bank.deposit", "amount", -1));

        c.drawText(textRenderer, "Déposer ou retirer", cx, cy + 74, Ui.TEXT, false);
        long amount = parse(amountText);
        button(c, cx + 78, cy + 86, 58, 16, "Déposer", amount > 0, Style.NORMAL, () -> act("bank.deposit", "amount", (int) amount));
        button(c, cx + 140, cy + 86, 58, 16, "Retirer", amount > 0, Style.NORMAL, () -> act("bank.withdraw", "amount", amount));
        button(c, cx + 202, cy + 86, 58, 16, "Retirer 10", true, Style.NORMAL, () -> act("bank.withdraw", "amount", 10L));
        button(c, cx + 264, cy + 86, Math.min(58, cw - 264), 16, "Retirer 64", true, Style.NORMAL, () -> act("bank.withdraw", "amount", 64L));

        title(c, "Virement à un joueur", cy + 116);
        long tAmount = parse(transferText);
        button(c, cx + 180, cy + 142, 70, 16, "Envoyer", !nameText.isBlank() && tAmount > 0, Style.PRIMARY,
                () -> act("bank.transfer", "name", nameText, "amount", tAmount));
        if (me.getBoolean("frozen")) {
            c.drawText(textRenderer, "❄ Ton compte est gelé : contacte un GM.", cx, cy + 166, Ui.RED, false);
        } else {
            c.drawText(textRenderer, me.getLong("transferLeft") < 0 ? "Virements illimités, même vers un joueur déconnecté."
                    : "Virements : encore " + Ui.number(me.getLong("transferLeft")) + " pièces aujourd'hui.", cx, cy + 166, Ui.FAINT, false);
        }
        c.drawText(textRenderer, "Paie des métiers aujourd'hui : " + me.getString("capMetier") + " pièces (au-delà, paie réduite).", cx, cy + 177, Ui.FAINT, false);
        c.drawText(textRenderer, "Marché, boutique, forge et métiers se paient depuis ton compte.", cx, cy + 188, Ui.FAINT, false);
    }

    private void marche(DrawContext c, NbtCompound d) {
        NbtList list = d.getList("auctions", NbtElement.COMPOUND_TYPE);
        int y = title(c, "Hôtel des ventes", cy);
        textRight(c, list.size() + " annonce" + (list.size() > 1 ? "s" : ""), cx + cw, cy, Ui.FAINT);
        int rows = Math.max(1, (cy + ch - 46 - y) / 22);
        int pages = Math.max(1, (list.size() + rows - 1) / rows);
        page = Math.max(0, Math.min(page, pages - 1));
        long money = d.getCompound("me").getLong("money");
        if (list.isEmpty()) centered(c, "Aucune annonce pour l'instant.", cx + cw / 2, y + 30, Ui.FAINT);
        for (int i = page * rows; i < Math.min(list.size(), (page + 1) * rows); i++) {
            NbtCompound m = list.getCompound(i);
            ItemStack stack = ItemStack.fromNbt(m.getCompound("stack"));
            boolean own = m.getBoolean("own");
            long price = m.getLong("price");
            Ui.card(c, cx, y, cw, 20, false);
            item(c, stack, cx + 3, y + 2);
            c.drawText(textRenderer, textRenderer.trimToWidth(stack.getName().getString() + (stack.getCount() > 1 ? " ×" + stack.getCount() : ""), cw - 200),
                    cx + 23, y + 6, Ui.TEXT, false);
            c.drawText(textRenderer, textRenderer.trimToWidth(own ? "(toi)" : m.getString("seller"), 64), cx + cw - 172, y + 6, Ui.FAINT, false);
            textRight(c, Ui.number(price), cx + cw - 72, y + 6, Ui.GOLD);
            coin(c, cx + cw - 70, y + 6);
            int id = m.getInt("id");
            if (own) button(c, cx + cw - 58, y + 3, 54, 14, "Retirer", true, Style.DANGER, () -> act("hdv.click", "id", id));
            else button(c, cx + cw - 58, y + 3, 54, 14, "Acheter", money >= price, Style.PRIMARY, () -> act("hdv.click", "id", id));
            y += 22;
        }
        pager(c, cy + ch - 40, page, pages, p -> page = p);
        // vendre l'objet en main
        int by = cy + ch - 19;
        Ui.rule(c, cx, by - 4, cw);
        ItemStack held = client != null && client.player != null ? client.player.getMainHandStack() : ItemStack.EMPTY;
        if (!held.isEmpty()) item(c, held, cx + 2, by);
        c.drawText(textRenderer, held.isEmpty() ? "Prends un objet en main pour le vendre"
                        : "Vendre l'objet en main (taxe " + Math.round(d.getDouble("hdvTax") * 100) + " %) pour",
                cx + 22, by + 4, held.isEmpty() ? Ui.FAINT : Ui.TEXT, false);
        long price = parse(priceText);
        coin(c, cx + cw - 64, by + 4);
        button(c, cx + cw - 56, by, 54, 16, "Vendre", !held.isEmpty() && price > 0, Style.PRIMARY, () -> act("hdv.sell", "price", price));
    }

    private void boutique(DrawContext c, NbtCompound d) {
        NbtList list = d.getList("shop", NbtElement.COMPOUND_TYPE);
        int y = title(c, "Boutique du royaume", cy);
        NbtCompound meShop = d.getCompound("me");
        textRight(c, "Découverts " + meShop.getInt("discKnown") + " / " + meShop.getInt("discTotal") + " · prix selon l'offre", cx + cw, cy, Ui.FAINT);
        long money = d.getCompound("me").getLong("money");
        c.drawText(textRenderer, textRenderer.trimToWidth("Reventes au serveur aujourd'hui : " + d.getCompound("me").getString("capRachat")
                + " pièces (au-delà, prix réduit)", cw), cx, y, Ui.FAINT, false);
        y += 12;
        int rows = Math.max(1, (cy + ch - 26 - y) / 22);
        int pages = Math.max(1, (list.size() + rows - 1) / rows);
        page = Math.max(0, Math.min(page, pages - 1));
        if (list.isEmpty()) centered(c, "La boutique est vide.", cx + cw / 2, y + 30, Ui.FAINT);
        for (int i = page * rows; i < Math.min(list.size(), (page + 1) * rows); i++) {
            NbtCompound m = list.getCompound(i);
            if (m.contains("known") && !m.getBoolean("known")) {
                // Objet jamais ramassé : masqué.
                Ui.card(c, cx, y, cw, 20, false);
                c.fill(cx + 3, y + 2, cx + 19, y + 18, 0xFF120D18);
                Ui.border(c, cx + 3, y + 2, 16, 16, Ui.LINE);
                centered(c, "?", cx + 11, y + 6, Ui.FAINT);
                c.drawText(textRenderer, "Objet inconnu — ramasse-le une fois pour l'acheter", cx + 23, y + 6, Ui.FAINT, false);
                textRight(c, "???", cx + cw - 8, y + 6, Ui.FAINT);
                if (in(cx, y, cw, 20)) hoverText = List.of(Text.literal("Objet inconnu").formatted(Formatting.GRAY),
                        Text.literal("Il apparaîtra ici dès que tu l'auras ramassé.").formatted(Formatting.DARK_GRAY));
                y += 22;
                continue;
            }
            ItemStack stack = ItemStack.fromNbt(m.getCompound("stack"));
            long price = m.getLong("price"), base = m.getLong("base"), sell = m.getLong("sellPrice");
            boolean sold = m.getBoolean("sold"), buyback = m.getBoolean("buyback");
            Ui.card(c, cx, y, cw, 20, false);
            item(c, stack, cx + 3, y + 2);
            c.drawText(textRenderer, textRenderer.trimToWidth(stack.getName().getString() + (stack.getCount() > 1 ? " ×" + stack.getCount() : ""), cw - 210),
                    cx + 23, y + 6, Ui.TEXT, false);
            int id = m.getInt("id");
            if (sold) {
                // Tendance : ▲ plus cher que d'habitude (stock bas), ▼ moins cher.
                if (price != base) c.drawText(textRenderer, price > base ? "▲" : "▼", cx + cw - 104, y + 6, price > base ? Ui.RED : Ui.GREEN, false);
                textRight(c, Ui.number(price), cx + cw - 72, y + 6, Ui.GOLD);
                coin(c, cx + cw - 70, y + 6);
                button(c, cx + cw - 58, y + 3, 54, 14, "Acheter", money >= price, Style.PRIMARY, () -> act("shop.buy", "id", id));
            }
            if (buyback) button(c, cx + cw - (sold ? 178 : 98), y + 3, 70, 14, "Vendre " + sell, true, Style.NORMAL, () -> act("shop.sell", "id", id));
            y += 22;
        }
        pager(c, cy + ch - 18, page, pages, p -> page = p);
    }

    private void raid(DrawContext c, NbtCompound d) {
        NbtCompound r = d.getCompound("raid");
        int y = title(c, "Zone Nether de raid", cy);
        String status = r.getString("status");
        Ui.card(c, cx, y, cw, 50, true);
        if (status.equals("OUVERT")) {
            scaled(c, Text.literal("Ouverte — " + r.getString("threat")), cx + 8, y + 6, 1.3f, Ui.VIOLET);
            c.drawText(textRenderer, textRenderer.trimToWidth("Régénération dans " + Ui.duration(r.getLong("left")) + " · " + r.getInt("statues") + " statues",
                    cw - 124), cx + 8, y + 22, Ui.MUTED, false);
            c.drawText(textRenderer, textRenderer.trimToWidth("Extraction : " + r.getInt("keys") + " / " + r.getInt("keysNeeded") + " clés sur le socle",
                    cw - 124), cx + 8, y + 34, Ui.FAINT, false);
        } else {
            scaled(c, Text.literal(status.equals("GENERATION") ? "Génération… " + r.getInt("progress") + " %" : "Fermée"), cx + 8, y + 6, 1.3f, Ui.ORANGE);
            c.drawText(textRenderer, "La zone se reconstruit toutes les quelques heures.", cx + 8, y + 24, Ui.MUTED, false);
        }
        long fee = r.getLong("fee");
        boolean in = r.getBoolean("inRaid"), near = r.getBoolean("nearPortal") || isGm();
        if (!in) {
            button(c, cx + cw - 104, y + 16, 96, 18, "Entrer (" + fee + ")", status.equals("OUVERT") && near, Style.PRIMARY, () -> act("raid.enter"));
            if (!near) textRight(c, r.getBoolean("portalSet") ? "Va au portail du hub" : "Portail pas encore placé", cx + cw - 8, y + 38, Ui.FAINT);
        } else {
            textRight(c, "Tu es dans le raid", cx + cw - 8, y + 20, Ui.GREEN);
        }
        y += 56;
        c.drawText(textRenderer, textRenderer.trimToWidth("Sortir : balise de retour (10 s immobile) ou extraction (2 clés de gardien).", cw), cx, y, Ui.FAINT, false);
        c.drawText(textRenderer, textRenderer.trimToWidth("Mort : coffre de corps 10 min. Butin non extrait : perdu.", cw), cx, y + 10, Ui.FAINT, false);
        y += 26;
        // Groupe
        y = title(c, "Groupe (" + r.getList("party", NbtElement.STRING_TYPE).size() + " / 4)", y);
        NbtList party = r.getList("party", NbtElement.STRING_TYPE);
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < party.size(); i++) names.append(i > 0 ? ", " : "").append(party.getString(i));
        c.drawText(textRenderer, textRenderer.trimToWidth(names.toString(), cw - 182), cx, y + 4, Ui.TEXT, false);
        String invite = valueField("raid.invite", cx + cw - 176, y, 64, "", "[A-Za-z0-9_]*").getText();
        button(c, cx + cw - 108, y + 1, 48, 14, "Inviter", !invite.isBlank(), Style.NORMAL, () -> {
            act("raid.invite", "name", invite);
            unfocus();
        });
        button(c, cx + cw - 58, y + 1, 58, 14, party.size() > 1 ? "Quitter" : "Accepter", true, Style.NORMAL,
                () -> act(party.size() > 1 ? "raid.leave" : "raid.accept"));
        y += 22;
        // Voleurs et Brise-statue
        y = title(c, "Chapardeurs et statues", y);
        int a = r.getInt("affinity");
        String tier = a >= 100 ? "compagnon fidèle" : a >= 50 ? "il te suit" : a >= 20 ? "il ne te vole plus" : "hostile";
        c.drawText(textRenderer, "Affinité avec les voleurs : " + a + " (" + tier + ")", cx, y + 4, Ui.TEXT, false);
        if (a >= 100) {
            long cd = r.getLong("companionCooldown");
            button(c, cx + cw - 96, y + 1, 96, 14, cd > 0 ? "Revient dans " + Ui.duration(cd) : r.getBoolean("companionOff") ? "Activer" : "Désactiver",
                    cd == 0, Style.NORMAL, () -> act("raid.companion"));
        }
        y += 18;
        if (r.contains("breakerDamage")) {
            c.drawText(textRenderer, "Brise-statue : " + (12 - r.getInt("breakerDamage")) + " / 12 statues restantes", cx, y + 4, Ui.TEXT, false);
            button(c, cx + cw - 96, y + 1, 96, 14, "Réparer (" + r.getLong("repairPrice") + ")", r.getInt("breakerDamage") > 0, Style.PRIMARY, () -> act("raid.repair"));
        } else {
            c.drawText(textRenderer, "Prends ton Brise-statue en main pour le réparer ici.", cx, y + 4, Ui.FAINT, false);
        }
        if (isGm()) {
            int by = cy + ch - 16;
            Ui.rule(c, cx, by - 4, cw);
            int bw = (cw - 12) / 4;
            button(c, cx, by, bw, 14, "Régénérer", true, Style.DANGER, () -> act("gm.raid.regen"));
            button(c, cx + bw + 4, by, bw, 14, "Hub ici", true, Style.NORMAL, () -> act("gm.raid.hub"));
            button(c, cx + 2 * (bw + 4), by, bw, 14, "Portail ici", true, Style.NORMAL, () -> act("gm.raid.portal"));
            button(c, cx + 3 * (bw + 4), by, bw, 14, "Relire salles", true, Style.NORMAL, () -> act("gm.raid.rooms"));
        }
    }

    private void classement(DrawContext c, NbtCompound d) {
        int y = title(c, "Classements", cy);
        String[][] cats = {{"metiers", "Métiers"}, {"richesse", "Richesse"}, {"combats", "Combats"}, {"morts", "Morts"}};
        int bw = (cw - 12) / 4;
        for (int i = 0; i < cats.length; i++) {
            String key = cats[i][0];
            button(c, cx + i * (bw + 4), y, bw, 16, cats[i][1], true, key.equals(rankCat) ? Style.PRIMARY : Style.NORMAL, () -> rankCat = key);
        }
        y += 24;
        String myName = d.getCompound("me").getString("name");
        NbtList list = d.getCompound("rank").getList(rankCat, NbtElement.COMPOUND_TYPE);
        int[] medals = {0xFFE9C25B, 0xFFC9D1DB, 0xFFCD8B4E};
        for (int i = 0; i < list.size(); i++) {
            NbtCompound r = list.getCompound(i);
            boolean mine = r.getString("name").equals(myName);
            c.fill(cx, y, cx + cw, y + 15, mine ? 0x40A06AFF : i % 2 == 0 ? 0x18FFFFFF : 0);
            c.drawText(textRenderer, String.valueOf(i + 1), cx + 6, y + 4, i < 3 ? medals[i] : Ui.FAINT, i < 3);
            c.drawText(textRenderer, r.getString("name"), cx + 24, y + 4, mine ? Ui.GOLD : Ui.TEXT, false);
            textRight(c, r.getString("value"), cx + cw - 6, y + 4, Ui.MUTED);
            y += 16;
        }
        if (list.isEmpty()) centered(c, "Personne pour l'instant.", cx + cw / 2, y + 20, Ui.FAINT);
    }

    // ================================================================ onglet Faction

    private static String facSub = "accueil", facRank = "power";
    private static int facPage, facShopPage, facMapScale = 2;
    private static boolean confirmLeave;
    private static int confirmWar;
    /** Éditeur de bannière : brouillon (fond, motifs, couleurs) et élément sélectionné (−1 = fond). */
    private static int bnBase = -1, bnSel = -1;
    private static final List<String> bnPat = new ArrayList<>();
    private static final List<Integer> bnCol = new ArrayList<>();
    private static final int[] DYE = new int[16];

    static {
        for (net.minecraft.util.DyeColor dc : net.minecraft.util.DyeColor.values()) DYE[dc.getId()] = 0xFF000000 | dc.getFireworkColor();
    }

    private void faction(DrawContext c, NbtCompound d) {
        NbtCompound fac = d.getCompound("fac");
        boolean has = fac.contains("mine");
        String[][] subs = has
                ? new String[][]{{"accueil", "Accueil"}, {"membres", "Membres"}, {"carte", "Carte"}, {"tresor", "Trésor"}, {"guerre", "Guerre"},
                {"banniere", "Bannière"}, {"podium", "Podium"}}
                : new String[][]{{"accueil", "Accueil"}, {"carte", "Carte"}, {"podium", "Podium"}};
        boolean known = false;
        for (String[] s : subs) known |= s[0].equals(facSub);
        if (!known) facSub = "accueil";
        int x = cx, textW = 0;
        for (String[] s : subs) textW += textRenderer.getWidth(s[1]);
        int pad = Math.max(4, (cw - textW - 3 * (subs.length - 1)) / subs.length);
        for (String[] s : subs) {
            int bw = textRenderer.getWidth(s[1]) + pad;
            String key = s[0];
            button(c, x, cy, bw, 15, s[1], true, key.equals(facSub) ? Style.PRIMARY : Style.NORMAL, () -> {
                facSub = key;
                facPage = 0;
                bnBase = -1;
                confirmLeave = false;
                confirmWar = 0;
                clearAndInit();
            });
            x += bw + 3;
        }
        int y = cy + 20;
        switch (facSub) {
            case "membres" -> facMembres(c, fac, y);
            case "carte" -> facCarte(c, fac, y);
            case "tresor" -> facTresor(c, fac, y);
            case "guerre" -> facGuerre(c, fac, y);
            case "banniere" -> facBanniere(c, fac, y);
            case "podium" -> facPodium(c, fac, y);
            default -> {
                if (has) facAccueil(c, fac, y);
                else facSansFaction(c, fac, y);
            }
        }
    }

    private void bannerIcon(DrawContext c, NbtCompound stackNbt, int x, int y, float scale) {
        ItemStack st = ItemStack.fromNbt(stackNbt);
        if (st.isEmpty()) return;
        c.getMatrices().push();
        c.getMatrices().translate(x, y, 0);
        c.getMatrices().scale(scale, scale, 1f);
        c.drawItem(st, 0, 0);
        c.getMatrices().pop();
    }

    private void facSansFaction(DrawContext c, NbtCompound fac, int y) {
        Ui.card(c, cx, y, cw, 40, true);
        c.drawText(textRenderer, "Fonder une faction — gratuit", cx + 8, y + 6, Ui.GOLD, true);
        c.drawText(textRenderer, "Tu en deviens le chef ; invite qui tu veux.", cx + 8, y + 18, Ui.MUTED, false);
        String name = valueField("fac.name", cx + cw - 172, y + 20, 104, "", "[\\p{L}0-9 '_-]*").getText();
        button(c, cx + cw - 64, y + 20, 58, 15, "Fonder", name.trim().length() >= 3, Style.PRIMARY, () -> {
            act("fac.create", "name", name);
            unfocus();
        });
        y += 46;
        NbtList inv = fac.getList("invites", NbtElement.COMPOUND_TYPE);
        if (!inv.isEmpty()) {
            y = title(c, "Invitations", y);
            for (int i = 0; i < inv.size(); i++) {
                NbtCompound f = inv.getCompound(i);
                bannerIcon(c, f.getCompound("banner"), cx + 2, y, 1f);
                c.drawText(textRenderer, f.getString("name") + "  ·  niv. " + f.getInt("level") + " · " + f.getInt("members") + " membres", cx + 22, y + 4, Ui.TEXT, false);
                int id = f.getInt("id");
                button(c, cx + cw - 70, y + 1, 70, 14, "Rejoindre", true, Style.PRIMARY, () -> act("fac.join", "id", id));
                y += 18;
            }
            y += 4;
        }
        NbtList all = fac.getList("all", NbtElement.COMPOUND_TYPE);
        y = title(c, "Factions du monde (" + all.size() + ")", y);
        int rows = Math.max(1, (cy + ch - 18 - y) / 18);
        int pages = Math.max(1, (all.size() + rows - 1) / rows);
        facPage = Math.max(0, Math.min(facPage, pages - 1));
        for (int i = facPage * rows; i < Math.min(all.size(), (facPage + 1) * rows); i++) {
            NbtCompound f = all.getCompound(i);
            bannerIcon(c, f.getCompound("banner"), cx + 2, y, 1f);
            c.drawText(textRenderer, f.getString("name"), cx + 22, y + 4, Ui.TEXT, false);
            textRight(c, "niv. " + f.getInt("level") + " · " + f.getInt("members") + " membre(s) · " + f.getInt("territories") + " terr."
                    + (f.getBoolean("atWar") ? " · en guerre" : ""), cx + cw - 4, y + 4, f.getBoolean("atWar") ? Ui.RED : Ui.MUTED);
            y += 18;
        }
        if (all.isEmpty()) centered(c, "Aucune faction pour l'instant : fonde la première !", cx + cw / 2, y + 10, Ui.FAINT);
        pager(c, cy + ch - 15, facPage, pages, p -> facPage = p);
    }

    private void facAccueil(DrawContext c, NbtCompound fac, int y) {
        NbtCompound m = fac.getCompound("mine");
        Ui.card(c, cx, y, cw, 48, true);
        bannerIcon(c, m.getCompound("banner"), cx + 6, y + 6, 2.2f);
        scaled(c, Text.literal(m.getString("name")), cx + 46, y + 6, 1.4f, Ui.GOLD);
        textRight(c, "Niveau " + m.getInt("level") + " · rayon " + m.getInt("radius"), cx + cw - 8, y + 7, Ui.TEXT);
        Ui.bar(c, cx + 46, y + 22, cw - 54, 7, m.getLong("xp") / (float) Math.max(1, m.getLong("xpNeeded")), 0xFF9FE8EC, 0xFF2F8C96);
        int nb = m.getInt("members");
        c.drawText(textRenderer, textRenderer.trimToWidth(m.getLong("xp") + " / " + m.getLong("xpNeeded") + " XP · " + nb + (nb > 1 ? " membres · " : " membre · ")
                + m.getInt("territories") + " terr. · trésor " + Ui.number(m.getLong("bank")) + " · guerres " + m.getInt("warsWon") + "/" + m.getInt("warsLost"),
                cw - 54), cx + 46, y + 34, Ui.MUTED, false);
        y += 52;
        // Missions du jour
        NbtList mi = m.getList("missions", NbtElement.COMPOUND_TYPE);
        c.drawText(textRenderer, "Missions du jour", cx, y, Ui.GOLD, false);
        y += 11;
        for (int i = 0; i < mi.size(); i++) {
            NbtCompound q = mi.getCompound(i);
            boolean done = q.getBoolean("done");
            c.drawText(textRenderer, textRenderer.trimToWidth((done ? "✔ " : "• ") + q.getString("text"), cw - 150), cx + 4, y, done ? Ui.GREEN : Ui.TEXT, false);
            textRight(c, (done ? "" : q.getInt("progress") + " / " + q.getInt("total") + " · ") + "+" + q.getInt("xp") + " XP, +" + q.getLong("pieces"),
                    cx + cw - 4, y, done ? Ui.GREEN : Ui.MUTED);
            y += 11;
        }
        y += 4;
        // Base principale
        Ui.card(c, cx, y, cw, 22, false);
        boolean base = m.contains("heartX");
        c.drawText(textRenderer, textRenderer.trimToWidth(base ? "Base " + m.getInt("heartX") + " / " + m.getInt("heartZ") + " · Cœur " + m.getInt("heartHp") + "/" + fac.getInt("heartMax")
                : "Pas de base : achète un Étendard (Trésor) et plante-le.", base ? cw - 120 : cw - 8), cx + 6, y + 7, base ? Ui.TEXT : Ui.ORANGE, false);
        if (base) {
            button(c, cx + cw - 112, y + 4, 52, 14, "Y aller", true, Style.NORMAL, () -> act("fac.base.tp"));
            button(c, cx + cw - 56, y + 4, 52, 14, "Coffre", true, Style.NORMAL, () -> act("fac.chest"));
        }
        y += 26;
        // Historique
        NbtList hs = m.getList("history", NbtElement.STRING_TYPE);
        c.drawText(textRenderer, "Historique", cx, y, Ui.GOLD, false);
        button(c, cx + cw - 96, y - 3, 96, 13, confirmLeave ? "Confirmer le départ" : "Quitter la faction", true, Style.DANGER, () -> {
            if (confirmLeave) {
                act("fac.leave");
                confirmLeave = false;
            } else confirmLeave = true;
        });
        y += 12;
        for (int i = 0; i < hs.size() && y < cy + ch - 9; i++) {
            String line = hs.getString(i);
            int dash = line.indexOf(" — ");
            String text = dash > 0 ? line.substring(dash + 3) : line;
            c.drawText(textRenderer, textRenderer.trimToWidth(text, cw - 4), cx + 2, y, Ui.FAINT, false);
            if (in(cx, y, cw, 10)) hoverText = List.of(Text.literal(line).formatted(Formatting.GRAY));
            y += 10;
        }
    }

    private void facMembres(DrawContext c, NbtCompound fac, int y) {
        NbtCompound m = fac.getCompound("mine");
        boolean chief = m.getBoolean("chief");
        c.drawText(textRenderer, "Inviter un joueur", cx, y + 4, Ui.TEXT, false);
        String who = valueField("fac.invite", cx + 100, y, 100, "", "[A-Za-z0-9_]*").getText();
        button(c, cx + 204, y + 1, 58, 14, "Inviter", !who.isBlank(), Style.PRIMARY, () -> {
            act("fac.invite", "name", who);
            unfocus();
        });
        y += 20;
        NbtList votes = m.getList("votes", NbtElement.COMPOUND_TYPE);
        if (!votes.isEmpty()) {
            c.drawText(textRenderer, "Votes en cours (adoptés à plus de 50 % des membres)", cx, y, Ui.GOLD, false);
            y += 11;
            for (int i = 0; i < votes.size(); i++) {
                NbtCompound v = votes.getCompound(i);
                int id = v.getInt("id"), my = v.getInt("my");
                Ui.card(c, cx, y, cw, 17, false);
                c.drawText(textRenderer, textRenderer.trimToWidth(v.getString("label"), cw - 170), cx + 4, y + 5, Ui.TEXT, false);
                textRight(c, v.getInt("yes") + " oui · " + v.getInt("no") + " non · " + Ui.duration(v.getLong("left")), cx + cw - 82, y + 5, Ui.MUTED);
                button(c, cx + cw - 78, y + 2, 36, 13, "Oui", true, my == 1 ? Style.PRIMARY : Style.NORMAL, () -> act("fac.vote", "id", id, "yes", true));
                button(c, cx + cw - 40, y + 2, 36, 13, "Non", true, my == -1 ? Style.DANGER : Style.NORMAL, () -> act("fac.vote", "id", id, "yes", false));
                y += 19;
            }
            y += 3;
        }
        NbtList ms = m.getList("memberList", NbtElement.COMPOUND_TYPE);
        c.drawText(textRenderer, "Membres (" + ms.size() + ")", cx, y, Ui.GOLD, false);
        textRight(c, chief ? "▲▼ rang · Exclure : direct" : "Élire = vote pour le chef · Exclure = vote", cx + cw, y, Ui.FAINT);
        y += 12;
        int rows = Math.max(1, (cy + ch - 18 - y) / 16);
        int pages = Math.max(1, (ms.size() + rows - 1) / rows);
        facPage = Math.max(0, Math.min(facPage, pages - 1));
        String myName = ClientData.data == null ? "" : ClientData.data.getCompound("me").getString("name");
        for (int i = facPage * rows; i < Math.min(ms.size(), (facPage + 1) * rows); i++) {
            NbtCompound mb = ms.getCompound(i);
            String uuid = mb.getString("uuid");
            Faction.Rank rank = Faction.Rank.parse(mb.getString("rank"));
            boolean self = mb.getString("name").equals(myName);
            c.fill(cx, y, cx + cw, y + 15, self ? 0x40A06AFF : i % 2 == 0 ? 0x18FFFFFF : 0);
            c.fill(cx + 4, y + 5, cx + 8, y + 9, mb.getBoolean("online") ? Ui.GREEN : 0xFF555555);
            c.drawText(textRenderer, mb.getString("name"), cx + 12, y + 4, rank == Faction.Rank.CHEF ? Ui.GOLD : Ui.TEXT, false);
            c.drawText(textRenderer, rank.nom, cx + 110, y + 4, rank == Faction.Rank.CHEF ? Ui.GOLD : rank == Faction.Rank.OFFICIER ? Ui.VIOLET : Ui.MUTED, false);
            long seen = mb.getLong("seenDays");
            if (in(cx, y, 170, 15)) hoverText = List.of(Text.literal(mb.getString("name")).formatted(Formatting.WHITE),
                    Text.literal(mb.getBoolean("online") ? "Connecté" : seen < 0 ? "Jamais vu" : "Vu il y a " + seen + " jour(s)").formatted(Formatting.GRAY),
                    Text.literal("Dons au trésor : " + mb.getLong("contributed") + " pièces").formatted(Formatting.GOLD));
            int bx = cx + cw - 2;
            if (rank != Faction.Rank.CHEF) {
                bx -= 50;
                button(c, bx, y + 1, 48, 13, chief ? "Exclure" : "Exclure ?", !self, Style.DANGER, () -> {
                    if (chief) act("fac.kick", "uuid", uuid);
                    else act("fac.vote.start", "type", "EXCLUSION", "uuid", uuid, "territory", 0);
                });
                bx -= 38;
                button(c, bx, y + 1, 36, 13, "Élire", true, Style.NORMAL, () -> act("fac.vote.start", "type", "CHEF", "uuid", uuid, "territory", 0));
                if (chief) {
                    bx -= 18;
                    button(c, bx, y + 1, 16, 13, "▼", rank != Faction.Rank.RECRUE, Style.NORMAL, () -> act("fac.rank", "uuid", uuid, "delta", -1));
                    bx -= 18;
                    button(c, bx, y + 1, 16, 13, "▲", rank != Faction.Rank.OFFICIER, Style.NORMAL, () -> act("fac.rank", "uuid", uuid, "delta", 1));
                }
            }
            y += 16;
        }
        pager(c, cy + ch - 15, facPage, pages, p -> facPage = p);
    }

    private void facCarte(DrawContext c, NbtCompound fac, int y) {
        int mapH = fac.contains("mine") ? 140 : cy + ch - y - 16;
        int mx0 = cx, my0 = y, mw = cw;
        c.fill(mx0, my0, mx0 + mw, my0 + mapH, 0xFF0C0A12);
        Ui.border(c, mx0, my0, mw, mapH, Ui.LINE);
        int px = fac.getInt("px"), pz = fac.getInt("pz");
        int scale = facMapScale;
        int ccx = mx0 + mw / 2, ccy = my0 + mapH / 2;
        // Quadrillage tous les 100 blocs.
        for (int gx = Math.floorDiv(px - mw / 2 * scale, 100) * 100; gx <= px + mw / 2 * scale; gx += 100) {
            int sx = ccx + (gx - px) / scale;
            if (sx > mx0 && sx < mx0 + mw) c.fill(sx, my0 + 1, sx + 1, my0 + mapH - 1, 0x14FFFFFF);
        }
        for (int gz = Math.floorDiv(pz - mapH / 2 * scale, 100) * 100; gz <= pz + mapH / 2 * scale; gz += 100) {
            int sy = ccy + (gz - pz) / scale;
            if (sy > my0 && sy < my0 + mapH) c.fill(mx0 + 1, sy, mx0 + mw - 1, sy + 1, 0x14FFFFFF);
        }
        NbtList map = fac.getList("map", NbtElement.COMPOUND_TYPE);
        int myFaction = fac.contains("mine") ? fac.getCompound("mine").getInt("id") : -1;
        List<Text> tip = null;
        for (int i = 0; i < map.size(); i++) {
            NbtCompound t = map.getCompound(i);
            int tx = ccx + (t.getInt("x") - px) / scale, ty = ccy + (t.getInt("z") - pz) / scale, rp = Math.max(2, t.getInt("r") / scale);
            int col = brighten(DYE[Math.max(0, Math.min(15, t.getInt("color")))]);
            if (t.getInt("fid") == myFaction) col = 0xFF7CF07C;
            for (int dy = -rp; dy <= rp; dy++) {
                int row = ty + dy;
                if (row <= my0 || row >= my0 + mapH - 1) continue;
                int half = (int) Math.sqrt((double) rp * rp - (double) dy * dy);
                int x1 = Math.max(mx0 + 1, tx - half), x2 = Math.min(mx0 + mw - 1, tx + half + 1);
                if (x2 <= x1) continue;
                c.fill(x1, row, x2, row + 1, (col & 0x00FFFFFF) | 0x40000000);
                if (tx - half >= mx0 + 1) c.fill(tx - half, row, tx - half + 1, row + 1, col);
                if (tx + half < mx0 + mw - 1) c.fill(tx + half, row, tx + half + 1, row + 1, col);
            }
            if (ty - rp > my0 && ty - rp < my0 + mapH) c.fill(Math.max(mx0 + 1, tx - 2), ty - rp, Math.min(mx0 + mw - 1, tx + 3), ty - rp + 1, col);
            if (ty + rp > my0 && ty + rp < my0 + mapH) c.fill(Math.max(mx0 + 1, tx - 2), ty + rp, Math.min(mx0 + mw - 1, tx + 3), ty + rp + 1, col);
            if (rp >= 9 && tx - 8 > mx0 && tx + 8 < mx0 + mw && ty - 8 > my0 && ty + 8 < my0 + mapH) bannerIcon(c, t.getCompound("banner"), tx - 8, ty - 8, 1f);
            else if (tx > mx0 && tx < mx0 + mw && ty > my0 && ty < my0 + mapH) c.fill(tx - 1, ty - 1, tx + 2, ty + 2, col);
            long ddx = mx - tx, ddy = my - ty;
            if (in(mx0, my0, mw, mapH) && ddx * ddx + ddy * ddy <= (long) rp * rp) {
                tip = List.of(Text.literal(t.getString("name")).formatted(t.getInt("fid") == myFaction ? Formatting.GREEN : Formatting.AQUA, Formatting.BOLD),
                        Text.literal((t.getBoolean("base") ? "Base principale · " : "Territoire · ") + "niv. " + t.getInt("level") + " · rayon " + t.getInt("r")).formatted(Formatting.GRAY),
                        Text.literal(t.getInt("x") + " / " + t.getInt("z")).formatted(Formatting.DARK_GRAY));
            }
        }
        // Le joueur au centre, avec sa direction.
        c.fill(ccx - 2, ccy - 2, ccx + 3, ccy + 3, 0xFFFFFFFF);
        double yaw = Math.toRadians(fac.getFloat("yaw"));
        for (int k = 3; k <= 7; k++) c.fill(ccx + (int) Math.round(-Math.sin(yaw) * k), ccy + (int) Math.round(Math.cos(yaw) * k),
                ccx + (int) Math.round(-Math.sin(yaw) * k) + 1, ccy + (int) Math.round(Math.cos(yaw) * k) + 1, 0xFFFFFFFF);
        c.drawText(textRenderer, "N", ccx - 2, my0 + 3, Ui.FAINT, false);
        c.drawText(textRenderer, "1 px = " + scale + " blocs", mx0 + 4, my0 + mapH - 10, Ui.FAINT, false);
        button(c, mx0 + mw - 34, my0 + 3, 14, 13, "+", scale > 1, Style.NORMAL, () -> facMapScale = Math.max(1, facMapScale / 2));
        button(c, mx0 + mw - 18, my0 + 3, 14, 13, "−", scale < 16, Style.NORMAL, () -> facMapScale = Math.min(16, facMapScale * 2));
        if (tip != null) hoverText = tip;
        y += mapH + 4;
        String here = fac.contains("hereName") ? "Tu es dans le territoire de « " + fac.getString("hereName") + " »." : "Tu es en terres sauvages.";
        c.drawText(textRenderer, textRenderer.trimToWidth(here, cw), cx, y, Ui.MUTED, false);
        if (!fac.contains("mine")) return;
        NbtCompound m = fac.getCompound("mine");
        y += 12;
        NbtList ts = m.getList("terr", NbtElement.COMPOUND_TYPE);
        if (ts.isEmpty()) {
            c.drawText(textRenderer, textRenderer.trimToWidth("Revendique avec un Étendard (Trésor), à " + fac.getInt("distanceMin")
                    + " blocs au moins des autres factions.", cw), cx, y, Ui.FAINT, false);
            return;
        }
        int rows = Math.max(1, (cy + ch - y) / 15);
        for (int i = 0; i < Math.min(ts.size(), rows); i++) {
            NbtCompound t = ts.getCompound(i);
            int tid = t.getInt("id");
            c.drawText(textRenderer, (t.getBoolean("base") ? "★ Base " : "⚑ Territoire ") + t.getInt("x") + " / " + t.getInt("z") + " (" + t.getString("dim") + ")",
                    cx + 2, y + 3, t.getBoolean("base") ? Ui.GOLD : Ui.TEXT, false);
            boolean inside = fac.getInt("hereTerritory") == tid;
            if (m.getBoolean("chief") && inside && !t.getBoolean("base")) {
                button(c, cx + cw - 154, y, 70, 13, "Base ici", true, Style.NORMAL, () -> act("fac.base.move"));
            }
            if (m.getBoolean("leader")) {
                button(c, cx + cw - 80, y, 80, 13, "Abandonner ?", true, Style.DANGER, () -> act("fac.vote.start", "type", "ABANDON", "uuid", "", "territory", tid));
            }
            y += 15;
        }
    }

    /** Couleur éclaircie (mélangée à 45 % de blanc) pour rester visible sur le fond sombre de la carte. */
    private static int brighten(int argb) {
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        r = r + (255 - r) * 45 / 100;
        g = g + (255 - g) * 45 / 100;
        b = b + (255 - b) * 45 / 100;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void facTresor(DrawContext c, NbtCompound fac, int y) {
        NbtCompound m = fac.getCompound("mine");
        Ui.card(c, cx, y, cw, 24, true);
        coin(c, cx + 6, y + 8);
        c.drawText(textRenderer, "Trésor : " + Ui.number(m.getLong("bank")), cx + 18, y + 8, Ui.GOLD, true);
        long amount = parse(valueField("fac.amount", cx + cw - 196, y + 4, 56, "", "\\d*").getText());
        button(c, cx + cw - 136, y + 5, 44, 14, "Donner", amount > 0, Style.PRIMARY, () -> {
            act("fac.deposit", "amount", amount);
            unfocus();
        });
        button(c, cx + cw - 90, y + 5, 44, 14, "Retirer", amount > 0 && m.getBoolean("chief"), Style.NORMAL, () -> {
            act("fac.withdraw", "amount", amount);
            unfocus();
        });
        button(c, cx + cw - 44, y + 5, 40, 14, "Coffre", true, Style.NORMAL, () -> act("fac.chest"));
        if (in(cx + cw - 90, y + 5, 44, 14) && !m.getBoolean("chief")) hoverText = List.of(Text.literal("Seul le chef retire de l'argent.").formatted(Formatting.GRAY));
        y += 28;
        y = title(c, "Boutique de faction (payée par le trésor)", y);
        NbtList shop = m.getList("shop", NbtElement.COMPOUND_TYPE);
        int rows = Math.max(1, (cy + ch - 18 - y) / 19);
        int pages = Math.max(1, (shop.size() + rows - 1) / rows);
        facShopPage = Math.max(0, Math.min(facShopPage, pages - 1));
        int level = m.getInt("level");
        long bank = m.getLong("bank");
        for (int i = facShopPage * rows; i < Math.min(shop.size(), (facShopPage + 1) * rows); i++) {
            NbtCompound a = shop.getCompound(i);
            int idx = a.getInt("index"), need = a.getInt("level");
            long price = a.getLong("price");
            Ui.card(c, cx, y, cw, 17, false);
            item(c, ItemStack.fromNbt(a.getCompound("stack")), cx + 2, y + 1);
            c.drawText(textRenderer, textRenderer.trimToWidth(a.getString("label"), level >= need ? cw - 160 : cw - 200), cx + 22, y + 5, level >= need ? Ui.TEXT : Ui.FAINT, false);
            if (level < need) textRight(c, "niveau " + need, cx + cw - 110, y + 5, Ui.ORANGE);
            textRight(c, Ui.number(price), cx + cw - 66, y + 5, Ui.GOLD);
            coin(c, cx + cw - 64, y + 5);
            button(c, cx + cw - 52, y + 2, 50, 13, "Acheter", level >= need && bank >= price, Style.PRIMARY, () -> act("fac.buy", "index", idx));
            y += 19;
        }
        pager(c, cy + ch - 15, facShopPage, pages, p -> facShopPage = p);
    }

    private void facGuerre(DrawContext c, NbtCompound fac, int y) {
        NbtCompound m = fac.getCompound("mine");
        boolean chief = m.getBoolean("chief");
        int heartMax = fac.getInt("heartMax");
        if (m.contains("war")) {
            NbtCompound w = m.getCompound("war");
            Ui.cardTinted(c, cx, y, cw, 70, Ui.RED);
            bannerIcon(c, w.getCompound("otherBanner"), cx + 6, y + 6, 2f);
            scaled(c, Text.literal("En guerre contre " + w.getString("otherName")), cx + 44, y + 6, 1.2f, Ui.RED);
            c.drawText(textRenderer, textRenderer.trimToWidth((w.getBoolean("attacker") ? "Déclarée par vous · " : "Déclarée par eux · ") + w.getString("status"), cw - 50),
                    cx + 44, y + 20, w.getBoolean("open") ? Ui.ORANGE : Ui.MUTED, false);
            c.drawText(textRenderer, "Notre Cœur", cx + 44, y + 33, Ui.TEXT, false);
            Ui.bar(c, cx + 104, y + 33, 70, 7, m.getInt("heartHp") / (float) Math.max(1, heartMax), 0xFF8CF08C, 0xFF2E8B3A);
            c.drawText(textRenderer, "Le leur", cx + 184, y + 33, Ui.TEXT, false);
            Ui.bar(c, cx + 226, y + 33, 70, 7, w.getInt("heartOther") / (float) Math.max(1, heartMax), 0xFFF08C8C, 0xFF8B2E2E);
            c.drawText(textRenderer, "Combats de nuit : nous " + (w.getBoolean("nightMine") ? "oui" : "non") + ", eux " + (w.getBoolean("nightTheirs") ? "oui" : "non")
                    + " (il faut les deux)", cx + 44, y + 46, Ui.MUTED, false);
            if (w.getInt("offer") == 1) c.drawText(textRenderer, "Vous avez proposé de capituler.", cx + 44, y + 57, Ui.ORANGE, false);
            if (w.getInt("offer") == 2) c.drawText(textRenderer, "Ils proposent de capituler !", cx + 44, y + 57, Ui.GREEN, false);
            y += 76;
            if (chief) {
                boolean night = w.getBoolean("nightMine");
                button(c, cx, y, 118, 15, night ? "Refuser la nuit" : "Accepter la nuit", true, Style.NORMAL, () -> act("fac.war.night", "on", !night));
                if (w.getInt("offer") == 2) {
                    button(c, cx + 122, y, 100, 15, "Accepter", true, Style.PRIMARY, () -> act("fac.war.answer", "yes", true));
                    button(c, cx + 226, y, 100, 15, "Refuser", true, Style.NORMAL, () -> act("fac.war.answer", "yes", false));
                } else {
                    button(c, cx + 122, y, 130, 15, "Proposer de capituler", w.getInt("offer") != 1, Style.DANGER, () -> act("fac.war.offer"));
                }
            } else {
                c.drawText(textRenderer, "Seul le chef déclare, accepte la nuit et capitule.", cx, y + 4, Ui.FAINT, false);
            }
            y += 22;
            c.drawText(textRenderer, textRenderer.trimToWidth("Victoire : briser leur Cœur (frapper, aux heures d'attaque) ou leur capitulation.", cw), cx, y, Ui.FAINT, false);
            y += 14;
        } else {
            long peace = 0;
            for (int i = 0; i < fac.getList("all", NbtElement.COMPOUND_TYPE).size(); i++) {
                NbtCompound f = fac.getList("all", NbtElement.COMPOUND_TYPE).getCompound(i);
                if (f.getBoolean("mine")) peace = f.getLong("peaceLeft");
            }
            c.drawText(textRenderer, peace > 0 ? "Paix obligatoire encore " + Ui.duration(peace) + "." : "Aucune guerre. Seul le chef peut en déclarer une.",
                    cx, y, peace > 0 ? Ui.GREEN : Ui.MUTED, false);
            y += 12;
            NbtList all = fac.getList("all", NbtElement.COMPOUND_TYPE);
            int shown = 0;
            for (int i = 0; i < all.size() && shown < 6; i++) {
                NbtCompound f = all.getCompound(i);
                if (f.getBoolean("mine")) continue;
                shown++;
                int id = f.getInt("id");
                bannerIcon(c, f.getCompound("banner"), cx + 2, y, 1f);
                boolean calm = f.getLong("peaceLeft") > 0, busy = f.getBoolean("atWar");
                c.drawText(textRenderer, textRenderer.trimToWidth(f.getString("name") + " · niv. " + f.getInt("level") + " · " + f.getInt("members") + " membre(s)"
                        + (calm ? " · en paix" : busy ? " · déjà en guerre" : ""), cw - 96), cx + 22, y + 4, calm ? Ui.GREEN : Ui.TEXT, false);
                boolean can = chief && peace == 0 && !busy && !calm;
                button(c, cx + cw - 70, y + 1, 70, 14, confirmWar == id ? "Confirmer ?" : "Déclarer", can, Style.DANGER, () -> {
                    if (confirmWar == id) {
                        act("fac.war.declare", "id", id);
                        confirmWar = 0;
                    } else confirmWar = id;
                });
                y += 18;
            }
            if (shown == 0) c.drawText(textRenderer, "Aucune autre faction pour l'instant.", cx, y, Ui.FAINT, false);
            y += 6;
        }
        NbtList wh = m.getList("warHistory", NbtElement.COMPOUND_TYPE);
        if (!wh.isEmpty() && y < cy + ch - 24) {
            c.drawText(textRenderer, "Guerres passées", cx, y, Ui.GOLD, false);
            y += 11;
            for (int i = 0; i < wh.size() && y < cy + ch - 9; i++) {
                NbtCompound h = wh.getCompound(i);
                int r = h.getInt("result");
                c.drawText(textRenderer, textRenderer.trimToWidth((r > 0 ? "Victoire" : r < 0 ? "Défaite" : "Fin") + " contre " + h.getString("other") + " — " + h.getString("reason"), cw),
                        cx + 2, y, r > 0 ? Ui.GREEN : r < 0 ? Ui.RED : Ui.MUTED, false);
                y += 10;
            }
        }
    }

    private void facBanniere(DrawContext c, NbtCompound fac, int y) {
        NbtCompound m = fac.getCompound("mine");
        List<String> codes = fr.serveur.rpg.faction.Banners.patterns();
        if (bnBase < 0) {
            bnBase = m.getInt("base");
            bnPat.clear();
            bnCol.clear();
            NbtList ls = m.getList("layers", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < ls.size(); i++) {
                bnPat.add(ls.getCompound(i).getString("p"));
                bnCol.add(ls.getCompound(i).getInt("c"));
            }
            bnSel = -1;
        }
        List<Faction.Layer> layers = new ArrayList<>();
        for (int i = 0; i < bnPat.size(); i++) layers.add(new Faction.Layer(bnPat.get(i), bnCol.get(i)));
        // Aperçu
        Ui.card(c, cx, y, 64, 116, true);
        c.getMatrices().push();
        c.getMatrices().translate(cx + 8, y + 10, 0);
        c.getMatrices().scale(3f, 3f, 1f);
        c.drawItem(fr.serveur.rpg.faction.Banners.stack(bnBase, layers), 0, 0);
        c.getMatrices().pop();
        int lx = cx + 70;
        // Palette (applique la couleur au fond ou au motif choisi)
        c.drawText(textRenderer, bnSel < 0 ? "Couleur du fond" : "Couleur du motif " + (bnSel + 1), lx, y, Ui.GOLD, false);
        for (int i = 0; i < 16; i++) {
            int sx = lx + i * 15, sy = y + 11;
            int col = i;
            boolean on = bnSel < 0 ? bnBase == i : bnSel < bnCol.size() && bnCol.get(bnSel) == i;
            c.fill(sx, sy, sx + 13, sy + 13, DYE[i]);
            Ui.border(c, sx - 1, sy - 1, 15, 15, on ? Ui.GOLD : Ui.LINE);
            hots.add(new Hot(sx, sy, 13, 13, () -> {
                if (bnSel < 0) bnBase = col;
                else if (bnSel < bnCol.size()) bnCol.set(bnSel, col);
            }));
        }
        int ly = y + 30;
        boolean fond = bnSel < 0;
        c.fill(lx, ly, lx + cw - 70, ly + 14, fond ? 0xFF30224A : 0x18FFFFFF);
        c.drawText(textRenderer, "Fond", lx + 4, ly + 3, fond ? Ui.GOLD : Ui.TEXT, false);
        hots.add(new Hot(lx, ly, cw - 70, 14, () -> bnSel = -1));
        ly += 15;
        for (int i = 0; i < bnPat.size(); i++) {
            int li = i;
            boolean sel = bnSel == i;
            c.fill(lx, ly, lx + cw - 70, ly + 14, sel ? 0xFF30224A : i % 2 == 0 ? 0x18FFFFFF : 0);
            c.fill(lx + 3, ly + 3, lx + 11, ly + 11, DYE[Math.max(0, Math.min(15, bnCol.get(i)))]);
            int pi = Math.max(0, codes.indexOf(bnPat.get(i)));
            String label = patternName(bnPat.get(i));
            c.drawText(textRenderer, textRenderer.trimToWidth((i + 1) + ". " + label, cw - 150), lx + 15, ly + 3, sel ? Ui.GOLD : Ui.TEXT, false);
            hots.add(new Hot(lx, ly, cw - 140, 14, () -> bnSel = li));
            button(c, lx + cw - 136, ly + 1, 14, 12, "◀", true, Style.NORMAL, () -> bnPat.set(li, codes.get(Math.floorMod(pi - 1, codes.size()))));
            button(c, lx + cw - 120, ly + 1, 14, 12, "▶", true, Style.NORMAL, () -> bnPat.set(li, codes.get(Math.floorMod(pi + 1, codes.size()))));
            button(c, lx + cw - 104, ly + 1, 14, 12, "↑", li > 0, Style.NORMAL, () -> {
                java.util.Collections.swap(bnPat, li, li - 1);
                java.util.Collections.swap(bnCol, li, li - 1);
            });
            button(c, lx + cw - 88, ly + 1, 14, 12, "✕", true, Style.DANGER, () -> {
                bnPat.remove(li);
                bnCol.remove(li);
                bnSel = -1;
            });
            ly += 15;
        }
        int by = y + 126;
        boolean leader = m.getBoolean("leader");
        button(c, cx, by, 64, 15, "+ Motif", bnPat.size() < fr.serveur.rpg.faction.Banners.MAX_LAYERS, Style.NORMAL, () -> {
            bnPat.add(codes.get(0));
            bnCol.add(bnBase == 15 ? 0 : 15);
            bnSel = bnPat.size() - 1;
        });
        button(c, cx + 68, by, 110, 15, "Copier celle en main", leader, Style.NORMAL, () -> {
            act("fac.banner.copy");
            bnBase = -1;
        });
        button(c, cx + cw - 80, by, 80, 15, "Enregistrer", leader, Style.PRIMARY, () -> {
            NbtList ls = new NbtList();
            for (int i = 0; i < bnPat.size(); i++) {
                NbtCompound l = new NbtCompound();
                l.putString("p", bnPat.get(i));
                l.putInt("c", bnCol.get(i));
                ls.add(l);
            }
            NbtCompound n = new NbtCompound();
            n.putInt("base", bnBase);
            n.put("layers", ls);
            SurvieRpgClient.send("fac.banner", n);
        });
        button(c, cx + cw - 140, by, 56, 15, "Annuler", true, Style.NORMAL, () -> bnBase = -1);
        c.drawText(textRenderer, textRenderer.trimToWidth(leader ? "Clique un motif, puis une couleur de la palette. 6 motifs au plus."
                : "Seuls le chef et les officiers modifient la bannière.", cw), cx, by + 20, Ui.FAINT, false);
    }

    private static String patternName(String code) {
        for (net.minecraft.block.entity.BannerPattern p : net.minecraft.registry.Registries.BANNER_PATTERN) {
            if (p.getId().equals(code)) {
                net.minecraft.util.Identifier id = net.minecraft.registry.Registries.BANNER_PATTERN.getId(p);
                String key = "block.minecraft.banner." + (id == null ? code : id.getPath()) + ".white";
                String t = net.minecraft.client.resource.language.I18n.translate(key);
                if (!t.equals(key)) return t.replaceAll("(?i)\\s*(blanc|blanche|white)\\s*", " ").trim();
                return id == null ? code : id.getPath().replace('_', ' ');
            }
        }
        return code;
    }

    private void facPodium(DrawContext c, NbtCompound fac, int y) {
        String[][] crits = {{"power", "Puissance"}, {"level", "Niveau"}, {"totalXp", "XP"}, {"territories", "Terr."}, {"warsWon", "Guerres"},
                {"bank", "Richesse"}, {"active", "Activité"}};
        int x = cx;
        for (String[] k : crits) {
            int bw = textRenderer.getWidth(k[1]) + 8;
            String key = k[0];
            button(c, x, y, bw, 14, k[1], true, key.equals(facRank) ? Style.PRIMARY : Style.NORMAL, () -> facRank = key);
            x += bw + 3;
        }
        y += 20;
        NbtList all = fac.getList("all", NbtElement.COMPOUND_TYPE);
        List<NbtCompound> list = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) list.add(all.getCompound(i));
        list.sort((a, b) -> Long.compare(b.getLong(facRank), a.getLong(facRank)));
        int[] medals = {0xFFE9C25B, 0xFFC9D1DB, 0xFFCD8B4E};
        for (int i = 0; i < Math.min(10, list.size()); i++) {
            NbtCompound f = list.get(i);
            boolean mine = f.getBoolean("mine");
            c.fill(cx, y, cx + cw, y + 17, mine ? 0x40A06AFF : i % 2 == 0 ? 0x18FFFFFF : 0);
            c.drawText(textRenderer, String.valueOf(i + 1), cx + 6, y + 5, i < 3 ? medals[i] : Ui.FAINT, i < 3);
            bannerIcon(c, f.getCompound("banner"), cx + 20, y, 1f);
            c.drawText(textRenderer, f.getString("name"), cx + 40, y + 5, mine ? Ui.GOLD : Ui.TEXT, false);
            String v = switch (facRank) {
                case "level" -> "niveau " + f.getInt("level");
                case "totalXp" -> Ui.number(f.getLong("totalXp")) + " XP";
                case "territories" -> f.getInt("territories") + " territoire(s)";
                case "warsWon" -> f.getInt("warsWon") + " victoire(s), " + f.getInt("warsLost") + " défaite(s)";
                case "bank" -> Ui.number(f.getLong("bank")) + " pièces";
                case "active" -> f.getInt("active") + " / " + f.getInt("members") + " membres actifs";
                default -> Ui.number(f.getLong("power")) + " de puissance";
            };
            textRight(c, v, cx + cw - 6, y + 5, Ui.MUTED);
            y += 18;
        }
        if (list.isEmpty()) centered(c, "Aucune faction pour l'instant.", cx + cw / 2, y + 20, Ui.FAINT);
        c.drawText(textRenderer, "Classements pour comparer, sans récompense.", cx, cy + ch - 10, Ui.FAINT, false);
    }

    // ================================================================ tableau de bord GM

    private void gm(DrawContext c, NbtCompound d) {
        NbtCompound gm = d.getCompound("gm");
        String[][] subs = {{"joueurs", "Joueurs"}, {"monde", "Monde"}, {"economie", "Économie"}, {"auto", "Automatisation"}};
        int bw = (cw - 12) / 4;
        for (int i = 0; i < subs.length; i++) {
            String key = subs[i][0];
            button(c, cx + i * (bw + 4), cy, bw, 16, subs[i][1], true, key.equals(gmSub) ? Style.PRIMARY : Style.NORMAL, () -> {
                gmSub = key;
                clearAndInit();
            });
        }
        int y = cy + 22;
        switch (gmSub) {
            case "monde" -> gmMonde(c, gm, y);
            case "economie" -> gmEconomie(c, gm, y);
            case "auto" -> gmAuto(c, d, gm, y);
            default -> gmJoueurs(c, gm, y);
        }
    }

    private void gmJoueurs(DrawContext c, NbtCompound gm, int y) {
        NbtList players = gm.getList("players", NbtElement.COMPOUND_TYPE);
        int listW = 108, rows = Math.max(1, (cy + ch - 20 - y) / 15);
        int pages = Math.max(1, (players.size() + rows - 1) / rows);
        gmPage = Math.max(0, Math.min(gmPage, pages - 1));
        NbtCompound selected = null;
        for (int i = 0; i < players.size(); i++) {
            if (players.getCompound(i).getString("name").equals(selectedPlayer)) selected = players.getCompound(i);
        }
        if (selected == null && !players.isEmpty()) {
            selected = players.getCompound(0);
            selectedPlayer = selected.getString("name");
        }
        Ui.card(c, cx, y, listW, rows * 15 + 4, false);
        int ly = y + 2;
        for (int i = gmPage * rows; i < Math.min(players.size(), (gmPage + 1) * rows); i++) {
            NbtCompound p = players.getCompound(i);
            String name = p.getString("name");
            boolean sel = name.equals(selectedPlayer);
            if (sel) c.fill(cx + 1, ly, cx + listW - 1, ly + 15, 0xFF3D2765);
            else if (in(cx, ly, listW, 15)) c.fill(cx + 1, ly, cx + listW - 1, ly + 15, 0xFF231A31);
            c.fill(cx + 5, ly + 5, cx + 9, ly + 9, p.getBoolean("online") ? Ui.GREEN : Ui.FAINT);
            c.drawText(textRenderer, textRenderer.trimToWidth(name, listW - 18), cx + 13, ly + 4, sel ? Ui.GOLD : Ui.TEXT, false);
            hots.add(new Hot(cx, ly, listW, 15, () -> selectedPlayer = name));
            ly += 15;
        }
        if (pages > 1) {
            button(c, cx, cy + ch - 15, 20, 14, "◀", gmPage > 0, Style.NORMAL, () -> gmPage--);
            centered(c, (gmPage + 1) + "/" + pages, cx + listW / 2, cy + ch - 12, Ui.MUTED);
            button(c, cx + listW - 20, cy + ch - 15, 20, 14, "▶", gmPage < pages - 1, Style.NORMAL, () -> gmPage++);
        }
        if (selected == null) {
            c.drawText(textRenderer, "Aucun joueur connu.", cx + listW + 10, y + 6, Ui.FAINT, false);
            return;
        }
        int px = cx + listW + 8, pw = cw - listW - 8;
        Ui.card(c, px, y, pw, 54, true);
        String name = selected.getString("name");
        scaled(c, Text.literal(name), px + 6, y + 5, 1.3f, Ui.GOLD);
        textRight(c, selected.getBoolean("online") ? "● en ligne" : "○ hors ligne", px + pw - 6, y + 6,
                selected.getBoolean("online") ? Ui.GREEN : Ui.FAINT);
        Job job = Job.parse(selected.getString("job"));
        String jobLine = job == null ? "Sans métier" : job.displayName + " — niv. " + selected.getInt("level")
                + (selected.getInt("prestige") > 0 ? "  ★" + selected.getInt("prestige") : "");
        c.drawText(textRenderer, jobLine, px + 6, y + 22, job == null ? Ui.MUTED : jobColor(job), false);
        c.drawText(textRenderer, Ui.number(selected.getLong("money")) + " pièces · " + selected.getInt("deaths") + " morts",
                px + 6, y + 33, Ui.MUTED, false);
        SideJob side = SideJob.parse(selected.getString("side"));
        if (side != null) textRight(c, side.displayName + " " + selected.getInt("sideLevel"), px + pw - 6, y + 33, sideColor(side));
        if (selected.getBoolean("awaiting")) c.drawText(textRenderer, "☠ Mort, attend la nuit", px + 6, y + 43, Ui.RED, false);
        else if (selected.getBoolean("frozen")) c.drawText(textRenderer, "❄ Compte gelé", px + 6, y + 43, Ui.RED, false);
        boolean frozen = selected.getBoolean("frozen");
        button(c, px + pw - 58, y + 39, 52, 12, frozen ? "Dégeler" : "Geler", true, frozen ? Style.PRIMARY : Style.DANGER,
                () -> act("gm.player", "name", name, "op", "freeze"));

        int by = y + 58, gw = (pw - 8) / 3;
        c.drawText(textRenderer, "Valeur :", px, by + 4, Ui.MUTED, false);
        String valText = valueField("player.value", px + 44, by + 1, 60, "", "\\d*").getText();
        long val = parse(valText);
        c.drawText(textRenderer, textRenderer.trimToWidth("+ ajoute, = fixe", pw - 112), px + 110, by + 4, Ui.FAINT, false);
        by += 20;
        boolean awaiting = selected.getBoolean("awaiting");
        Object[][] actions = {
                {"+ XP", "xp"}, {"= Niveau", "setlevel"}, {"Pioche", "give:pioche"},
                {"+ Pièces", "money+"}, {"− Pièces", "money-"}, {"= Pièces", "setmoney"},
                {"Larmes", "give:larme"}, {"Crochets", "give:crochet"}, {"Réanimer", "revive"}};
        for (int i = 0; i < actions.length; i++) {
            String label = (String) actions[i][0], op = (String) actions[i][1];
            boolean needsValue = op.equals("xp") || op.equals("setlevel") || op.startsWith("money") || op.equals("setmoney");
            boolean enabled = op.equals("revive") ? awaiting : op.equals("setmoney") ? !valText.isBlank() : !needsValue || val > 0;
            int bx = px + (i % 3) * (gw + 4), byy = by + (i / 3) * 18;
            button(c, bx, byy, gw, 15, label, enabled, op.equals("revive") ? Style.PRIMARY : Style.NORMAL, () -> {
                switch (op) {
                    case "money+" -> act("gm.player", "name", name, "op", "money", "value", val);
                    case "money-" -> act("gm.player", "name", name, "op", "money", "value", -val);
                    case "revive" -> act("gm.player", "name", name, "op", "revive");
                    default -> {
                        if (op.startsWith("give:")) act("gm.player", "name", name, "op", "give", "item", op.substring(5), "count", Math.max(1, val));
                        else act("gm.player", "name", name, "op", op, "value", val);
                    }
                }
            });
        }
        by += 56;
        c.drawText(textRenderer, "Donner le métier :", px, by + 2, Ui.FAINT, false);
        by += 12;
        int jw = (pw - 4) / 2;
        Job[] jobs = Job.values();
        for (int i = 0; i < jobs.length; i++) {
            Job j = jobs[i];
            button(c, px + (i % 2) * (jw + 4), by + (i / 2) * 17, jw, 15, j.displayName, j != job, Style.NORMAL,
                    () -> act("gm.player", "name", name, "op", "job", "job", j.name()));
        }
        by += 34;
        c.drawText(textRenderer, "Métier supplémentaire :", px, by + 1, Ui.FAINT, false);
        by += 11;
        SideJob[] sides = SideJob.values();
        int iw = (pw - 2 * sides.length) / (sides.length + 1);
        for (int i = 0; i <= sides.length; i++) {
            SideJob j = i == 0 ? null : sides[i - 1];
            int bx = px + i * (iw + 2);
            boolean current = j == side, hover = !current && in(bx, by, iw, 18);
            c.fill(bx, by, bx + iw, by + 18, current ? 0xFF30224A : hover ? 0xFF2A2039 : Ui.CARD);
            Ui.border(c, bx, by, iw, 18, current ? Ui.GOLD : hover ? Ui.GOLD_DIM : Ui.LINE);
            c.drawItem(new ItemStack(j == null ? Items.BARRIER : j.icon()), bx + (iw - 16) / 2, by + 1);
            if (in(bx, by, iw, 18)) hoverText = List.of(j == null ? Text.literal("Retirer le métier supplémentaire")
                    : Text.literal(j.displayName).formatted(j.color));
            if (!current) hots.add(new Hot(bx, by, iw, 18, () -> act("gm.player", "name", name, "op", "side", "job", j == null ? "" : j.name())));
        }
    }

    private void gmMonde(DrawContext c, NbtCompound gm, int y) {
        NbtCompound s = gm.getCompound("settings");
        c.drawText(textRenderer, "Heure du monde", cx + 4, y + 4, Ui.TEXT, false);
        button(c, cx + cw - 172, y + 1, 84, 14, "☀ Jour", true, Style.NORMAL, () -> act("gm.time", "to", "day"));
        button(c, cx + cw - 86, y + 1, 84, 14, "☾ Nuit", true, Style.NORMAL, () -> act("gm.time", "to", "night"));
        y += 18;
        boolean perm = s.getBoolean("permanentNight");
        y = toggle(c, y, "Nuit permanente", perm, "Active", "Cycle normal", "Désactiver", "Activer", "permanentNight");
        y = setting(c, y, perm ? "Jour invoqué par un Éclat" : "Durée du jour", "min", "dayMin", s.getDouble("dayMin"), true);
        y = setting(c, y, perm ? "Nuit minimale entre 2 Éclats" : "Durée de la nuit", "min", "nightMin", s.getDouble("nightMin"), true);
        y = toggle(c, y, "Nether", s.getBoolean("nether"), "Ouvert", "Fermé", "Fermer", "Ouvrir", "nether");
        y = toggle(c, y, "End", s.getBoolean("end"), "Ouvert", "Fermé", "Fermer", "Ouvrir", "end");
        c.drawText(textRenderer, "Salle d'attente des morts", cx + 4, y + 4, Ui.TEXT, false);
        boolean room = s.getBoolean("waitingRoom");
        c.drawText(textRenderer, room ? "définie" : "non définie", cx + 150, y + 4, room ? Ui.GREEN : Ui.RED, false);
        button(c, cx + cw - 86, y + 1, 84, 14, "Définir ici", true, Style.NORMAL, () -> act("gm.room.here"));
        y += 18;
        c.drawText(textRenderer, "Zone jouable", cx + 4, y + 4, Ui.TEXT, false);
        String border = s.getString("border");
        boolean partial = border.startsWith("coin");
        int zb = 64;
        button(c, cx + cw - 3 * zb - 4, y + 1, zb, 14, "Coin 1 ici", true, Style.NORMAL, () -> act("gm.border.corner", "corner", 1));
        button(c, cx + cw - 2 * zb - 2, y + 1, zb, 14, "Coin 2 ici", true, Style.NORMAL, () -> act("gm.border.corner", "corner", 2));
        button(c, cx + cw - zb, y + 1, zb, 14, "Retirer", !border.isEmpty(), Style.DANGER, () -> act("gm.border.clear"));
        y += 16;
        c.drawText(textRenderer, textRenderer.trimToWidth(border.isEmpty()
                        ? "Non définie. Place-toi sur un coin du monde (Coin 1), puis sur le coin opposé (Coin 2)." : border, cw - 8),
                cx + 4, y + 1, border.isEmpty() ? Ui.RED : partial ? Ui.ORANGE : Ui.GREEN, false);
        y += 13;
        c.drawText(textRenderer, "Zone protégée autour de moi, rayon", cx + 4, y + 4, Ui.TEXT, false);
        long radius = parse(valueField("zone.radius", cx + cw - 130, y + 1, 40, "20", "\\d*").getText());
        c.drawText(textRenderer, "blocs", cx + cw - 82, y + 4, Ui.MUTED, false);
        button(c, cx + cw - 52, y + 1, 50, 14, "Créer", radius >= 3, Style.PRIMARY, () -> {
            act("gm.zone.protect", "radius", (int) radius);
            unfocus();
        });
        y += 22;
        NbtList zones = gm.getList("zones", NbtElement.COMPOUND_TYPE);
        y = title(c, "Zones (" + zones.size() + ")", y);
        int rows = Math.max(1, (cy + ch - 18 - y) / 16);
        int pages = Math.max(1, (zones.size() + rows - 1) / rows);
        zonePage = Math.max(0, Math.min(zonePage, pages - 1));
        for (int i = zonePage * rows; i < Math.min(zones.size(), (zonePage + 1) * rows); i++) {
            NbtCompound z = zones.getCompound(i);
            String zn = z.getString("name");
            c.drawText(textRenderer, textRenderer.trimToWidth(z.getString("desc"), cw - 50), cx + 4, y + 3, Ui.MUTED, false);
            button(c, cx + cw - 42, y, 40, 13, "Suppr.", true, Style.DANGER, () -> act("gm.zone.delete", "name", zn));
            y += 16;
        }
        pager(c, cy + ch - 15, zonePage, pages, p -> zonePage = p);
    }

    private void gmEconomie(DrawContext c, NbtCompound gm, int y) {
        NbtCompound s = gm.getCompound("settings"), st = gm.getCompound("stats");
        y = setting(c, y, "Prix du changement de métier", "pièces", "jobPrice", s.getInt("jobPrice"), false);
        y = setting(c, y, "Réussite d'un crochet", "%", "lockpick", s.getInt("lockpick"), false);
        y = setting(c, y, "Bonus d'argent par prestige", "%", "prestigeBonus", s.getInt("prestigeBonus"), false);
        NbtCompound eco = gm.getCompound("eco");
        boolean high = eco.getLong("perActive") > eco.getLong("target");
        c.drawText(textRenderer, textRenderer.trimToWidth("Masse " + Ui.number(eco.getLong("supply")) + " · " + Ui.number(eco.getLong("perActive"))
                + " par actif (cible " + Ui.number(eco.getLong("target")) + ") · médiane " + Ui.number(eco.getLong("median")), cw - 4),
                cx + 4, y + 2, high ? Ui.ORANGE : Ui.TEXT, false);
        y += 11;
        c.drawText(textRenderer, textRenderer.trimToWidth("Régulateur : gains ×" + fmt2(eco.getDouble("gainMult")) + ", taxes ×" + fmt2(eco.getDouble("taxMult"))
                + " · " + eco.getInt("frozen") + " compte(s) gelé(s)", cw - 4),
                cx + 4, y + 2, eco.getInt("frozen") > 0 ? Ui.RED : Ui.FAINT, false);
        y += 11;
        NbtList alerts = eco.getList("alerts", NbtElement.STRING_TYPE);
        if (!alerts.isEmpty()) {
            c.drawText(textRenderer, textRenderer.trimToWidth("⚠ " + alerts.getString(alerts.size() - 1), cw - 4), cx + 4, y + 2, Ui.RED, false);
            y += 11;
        }
        y += 3;
        NbtList shop = ClientData.data.getList("shop", NbtElement.COMPOUND_TYPE);
        y = title(c, "Boutique (" + shop.size() + " articles) — prix de base, actuel · stock", y);
        int rows = Math.max(1, (cy + ch - 42 - y) / 18);
        int pages = Math.max(1, (shop.size() + rows - 1) / rows);
        shopPage = Math.max(0, Math.min(shopPage, pages - 1));
        for (int i = shopPage * rows; i < Math.min(shop.size(), (shopPage + 1) * rows); i++) {
            NbtCompound m = shop.getCompound(i);
            ItemStack stack = ItemStack.fromNbt(m.getCompound("stack"));
            int id = m.getInt("id");
            long current = m.getLong("base");
            item(c, stack, cx + 2, y);
            c.drawText(textRenderer, textRenderer.trimToWidth(stack.getName().getString() + (stack.getCount() > 1 ? " ×" + stack.getCount() : ""), cw - 250),
                    cx + 22, y + 4, Ui.TEXT, false);
            c.drawText(textRenderer, m.getLong("price") + " · " + m.getInt("stock") + "%", cx + cw - 222, y + 4, Ui.FAINT, false);
            long typed = parse(valueField("shop:" + id, cx + cw - 166, y + 1, 40, String.valueOf(current), "\\d*").getText());
            coin(c, cx + cw - 124, y + 4);
            button(c, cx + cw - 114, y + 1, 24, 14, "OK", typed > 0 && typed != current, Style.PRIMARY, () -> {
                act("gm.shop.price", "id", id, "value", typed);
                unfocus();
            });
            boolean sold = m.getBoolean("sold"), buyback = m.getBoolean("buyback");
            button(c, cx + cw - 88, y + 1, 30, 14, "Vend", true, sold ? Style.PRIMARY : Style.NORMAL, () -> act("gm.shop.toggle", "id", id, "what", "sold"));
            button(c, cx + cw - 56, y + 1, 30, 14, "Rach", true, buyback ? Style.PRIMARY : Style.NORMAL, () -> act("gm.shop.toggle", "id", id, "what", "buyback"));
            button(c, cx + cw - 24, y + 1, 22, 14, "✖", true, Style.DANGER, () -> act("gm.shop.remove", "id", id));
            y += 18;
        }
        pager(c, cy + ch - 38, shopPage, pages, p -> shopPage = p);
        int by = cy + ch - 18;
        Ui.rule(c, cx, by - 4, cw);
        c.drawText(textRenderer, "Ajouter l'objet en main, prix de base", cx + 2, by + 4, Ui.TEXT, false);
        long price = parse(gmPriceText);
        coin(c, cx + cw - 64, by + 4);
        button(c, cx + cw - 56, by, 54, 16, "Ajouter", price > 0, Style.PRIMARY, () -> act("gm.shop.add", "price", price));
    }

    private void gmAuto(DrawContext c, NbtCompound d, NbtCompound gm, int y) {
        NbtCompound s = gm.getCompound("settings");
        y = setting(c, y, "Quêtes du jour par joueur", "", "dailyCount", s.getInt("dailyCount"), false);
        y = setting(c, y, "Multiplicateur des récompenses", "×", "rewardMult", s.getDouble("rewardMult"), true);
        y = toggle(c, y, "Événements automatiques", s.getBoolean("autoEvents"), "Activés", "Désactivés", "Désactiver", "Activer", "autoEvents");
        y = setting(c, y, "Intervalle entre événements", "min", "eventInterval", s.getInt("eventInterval"), false);
        y = setting(c, y, "Durée d'un événement", "min", "eventDuration", s.getInt("eventDuration"), false);
        y = setting(c, y, "Joueurs connectés minimum", "", "eventMinPlayers", s.getInt("eventMinPlayers"), false);
        // Récompenses du podium : plusieurs nombres séparés par des espaces
        c.drawText(textRenderer, "Podium (1er 2e 3e...)", cx + 4, y + 4, Ui.TEXT, false);
        String rewards = s.getString("eventRewards");
        String typedRewards = valueField("set:eventRewards", cx + cw - 160, y + 1, 82, rewards, "[0-9 ]*").getText();
        c.drawText(textRenderer, "pièces", cx + cw - 74, y + 4, Ui.MUTED, false);
        button(c, cx + cw - 32, y + 1, 30, 14, "OK", !typedRewards.isBlank() && !typedRewards.trim().equals(rewards), Style.PRIMARY, () -> {
            act("gm.set", "key", "eventRewards", "text", typedRewards);
            unfocus();
        });
        y += 22;
        NbtCompound world = d.getCompound("world");
        if (world.contains("eventTitle")) {
            Ui.cardTinted(c, cx, y, cw, 22, Ui.VIOLET_DIM);
            c.drawText(textRenderer, textRenderer.trimToWidth(world.getString("eventTitle") + " · " + Ui.duration(world.getLong("eventLeft")), cw - 90),
                    cx + 6, y + 7, Ui.TEXT, false);
            button(c, cx + cw - 80, y + 4, 76, 14, "Terminer", true, Style.DANGER, () -> act("gm.event.stop"));
        } else {
            Ui.card(c, cx, y, cw, 22, false);
            long next = world.getLong("nextEvent");
            c.drawText(textRenderer, next < 0 ? "Aucun événement en cours (automatiques désactivés)."
                    : "Prochain événement automatique dans " + Ui.duration(next) + ".", cx + 6, y + 7, Ui.MUTED, false);
        }
        y += 28;
        c.drawText(textRenderer, "Lancer :", cx + 4, y + 4, Ui.TEXT, false);
        String[][] types = {{"combat", "Combat"}, {"recolte", "Récolte"}, {"ressource", "Ressource"}, {"random", "Au hasard"}};
        int bw = (cw - 56) / 4;
        for (int i = 0; i < types.length; i++) {
            String t = types[i][0];
            button(c, cx + 50 + i * (bw + 2), y + 1, bw, 14, types[i][1], true, i == 3 ? Style.PRIMARY : Style.NORMAL,
                    () -> act("gm.event.start", "type", t));
        }
    }

    // ================================================================ éléments

    private int title(DrawContext c, String s, int y) {
        c.drawText(textRenderer, s, cx, y, Ui.GOLD, true);
        Ui.rule(c, cx, y + 11, cw);
        return y + 16;
    }

    /** Ligne de réglage : libellé, champ de saisie, unité, bouton OK (actif si la valeur a changé). */
    private int setting(DrawContext c, int y, String label, String unit, String key, double current, boolean decimal) {
        if (in(cx, y, cw, 16)) c.fill(cx, y, cx + cw, y + 16, 0x14FFFFFF);
        c.drawText(textRenderer, label, cx + 4, y + 4, Ui.TEXT, false);
        String shown = fmt(current);
        String typed = valueField("set:" + key, cx + cw - 130, y + 1, 52, shown, decimal ? "\\d*([.,]\\d*)?" : "\\d*").getText();
        c.drawText(textRenderer, unit, cx + cw - 74, y + 4, Ui.MUTED, false);
        double value = parseDouble(typed);
        button(c, cx + cw - 32, y + 1, 30, 14, "OK", !typed.isBlank() && value != current, Style.PRIMARY, () -> {
            act("gm.set", "key", key, "value", value);
            unfocus();
        });
        return y + 18;
    }

    /** Champ de saisie du tableau de bord GM : créé une fois, recalé sur la valeur du serveur tant qu'on n'écrit pas dedans. */
    private TextFieldWidget valueField(String key, int x, int y, int fw, String serverValue, String regex) {
        seenFields.add(key);
        TextFieldWidget f = valueFields.get(key);
        if (f == null) {
            f = new TextFieldWidget(textRenderer, x, y, fw, 14, Text.literal(key));
            f.setMaxLength(24);
            f.setTextPredicate(t -> t.matches(regex));
            f.setText(serverValue);
            valueFields.put(key, f);
            syncedValues.put(key, serverValue);
            addDrawableChild(f);
        }
        f.setX(x);
        f.setY(y);
        f.setWidth(fw);
        f.visible = true;
        if (!f.isFocused() && !serverValue.equals(syncedValues.get(key))) {
            f.setText(serverValue);
            syncedValues.put(key, serverValue);
        }
        return f;
    }

    private void unfocus() {
        valueFields.values().forEach(f -> f.setFocused(false));
        setFocused(null);
    }

    private static double parseDouble(String s) {
        try {
            return s == null || s.isBlank() ? 0 : Double.parseDouble(s.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int toggle(DrawContext c, int y, String label, boolean on, String onText, String offText,
                       String offAction, String onAction, String key) {
        c.drawText(textRenderer, label, cx + 4, y + 4, Ui.TEXT, false);
        c.drawText(textRenderer, on ? onText : offText, cx + 150, y + 4, on ? Ui.GREEN : Ui.RED, false);
        button(c, cx + cw - 86, y + 1, 84, 14, on ? offAction : onAction, true, on ? Style.DANGER : Style.PRIMARY,
                () -> act("gm.set", "key", key, "delta", 0.0));
        return y + 18;
    }

    private void pager(DrawContext c, int y, int current, int pages, IntConsumer set) {
        if (pages <= 1) return;
        int mid = cx + cw / 2;
        button(c, mid - 46, y, 22, 14, "◀", current > 0, Style.NORMAL, () -> set.accept(current - 1));
        centered(c, (current + 1) + " / " + pages, mid, y + 3, Ui.MUTED);
        button(c, mid + 24, y, 22, 14, "▶", current < pages - 1, Style.NORMAL, () -> set.accept(current + 1));
    }

    private void button(DrawContext c, int x, int y, int bw, int bh, String label, boolean enabled, Style style, Runnable action) {
        boolean hover = enabled && in(x, y, bw, bh);
        int fill, border, text;
        if (!enabled) {
            fill = 0xFF15101C;
            border = 0xFF2A2236;
            text = Ui.FAINT;
        } else {
            switch (style) {
                case PRIMARY -> {
                    fill = hover ? 0xFF7B5B1F : 0xFF574015;
                    border = Ui.GOLD;
                    text = 0xFFFFEDB8;
                }
                case DANGER -> {
                    fill = hover ? 0xFF5E2025 : 0xFF3E161A;
                    border = hover ? Ui.RED : 0xFF7E3036;
                    text = 0xFFFFD8D4;
                }
                default -> {
                    fill = hover ? 0xFF36284C : 0xFF251C34;
                    border = hover ? Ui.GOLD : Ui.LINE;
                    text = Ui.TEXT;
                }
            }
        }
        c.fill(x, y, x + bw, y + bh, fill);
        Ui.border(c, x, y, bw, bh, border);
        if (enabled) c.fill(x + 1, y + 1, x + bw - 1, y + 2, 0x22FFFFFF);
        String s = textRenderer.trimToWidth(label, bw - 4);
        c.drawText(textRenderer, s, x + (bw - textRenderer.getWidth(s)) / 2, y + (bh - 8) / 2 + 1, text, false);
        if (enabled) hots.add(new Hot(x, y, bw, bh, action));
    }

    private void jobButton(DrawContext c, int x, int y, int bw, int bh, Job j, boolean current, Runnable action) {
        boolean hover = !current && in(x, y, bw, bh);
        c.fill(x, y, x + bw, y + bh, current ? 0xFF30224A : hover ? 0xFF2A2039 : Ui.CARD);
        Ui.border(c, x, y, bw, bh, current ? Ui.GOLD : hover ? Ui.GOLD_DIM : Ui.LINE);
        c.drawItem(toolIcon(j), x + 4, y + (bh - 16) / 2);
        c.drawText(textRenderer, textRenderer.trimToWidth(j.displayName, bw - 22), x + 20, y + (bh - 8) / 2, jobColor(j), false);
        if (!current) hots.add(new Hot(x, y, bw, bh, action));
    }

    private void item(DrawContext c, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) return;
        c.drawItem(stack, x, y);
        c.drawItemInSlot(textRenderer, stack, x, y);
        RareteClient.outline(c, stack, x, y);
        if (in(x, y, 16, 16)) hoverStack = stack;
    }

    /** Petite pièce d'or (8 px) à côté d'un prix. */
    private void coin(DrawContext c, int x, int y) {
        c.getMatrices().push();
        c.getMatrices().translate(x, y, 0);
        c.getMatrices().scale(0.5f, 0.5f, 1f);
        c.drawItem(new ItemStack(ModItems.PIECE_OR), 0, 0);
        c.getMatrices().pop();
    }

    private void scaled(DrawContext c, Text t, int x, int y, float factor, int color) {
        c.getMatrices().push();
        c.getMatrices().translate(x, y, 0);
        c.getMatrices().scale(factor, factor, 1f);
        c.drawText(textRenderer, t, 0, 0, color, true);
        c.getMatrices().pop();
    }

    private void trimmed(DrawContext c, Text t, int x, int y, int maxW, int color) {
        OrderedText o = Language.getInstance().reorder(textRenderer.trimToWidth(t, maxW));
        c.drawText(textRenderer, o, x, y, color, false);
    }

    private void centered(DrawContext c, String s, int centerX, int y, int color) {
        c.drawText(textRenderer, s, centerX - textRenderer.getWidth(s) / 2, y, color, false);
    }

    private void textRight(DrawContext c, String s, int right, int y, int color) {
        c.drawText(textRenderer, s, right - textRenderer.getWidth(s), y, color, false);
    }

    private boolean in(int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ================================================================ utilitaires

    private static boolean isGm() {
        return ClientData.data != null && ClientData.data.getCompound("me").getBoolean("gm");
    }

    private static ItemStack toolIcon(Job job) {
        return new ItemStack(ModItems.OFFICIAL.get(job).get(ModItems.Tier.FER));
    }

    private static int jobColor(Job job) {
        return switch (job) {
            case MINEUR -> 0xFF86AAEC;
            case BUCHERON -> 0xFFF2A957;
            case FERMIER -> 0xFF86D46E;
            case AVENTURIER -> 0xFFEC6A60;
        };
    }

    private static int sideColor(SideJob job) {
        return switch (job) {
            case INGENIEUR -> 0xFFE0524A;
            case CONSTRUCTEUR -> 0xFFF0D264;
            case ENCHANTEUR -> 0xFFE38CF5;
            case VOYAGEUR -> 0xFF6FE3E8;
            case PACIFICATEUR -> 0xFF7F9CFF;
            case ALCHIMISTE -> 0xFFC07CF0;
            case PECHEUR -> 0xFF4FC3B0;
        };
    }

    private static long parse(String s) {
        try {
            return s == null || s.isBlank() ? 0 : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String fmt2(double v) {
        return String.format("%.2f", v);
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }

    /** Envoie une action avec des paramètres (clé, valeur, clé, valeur...). */
    private static void act(String action, Object... kv) {
        NbtCompound n = new NbtCompound();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            String k = (String) kv[i];
            Object v = kv[i + 1];
            if (v instanceof Integer x) n.putInt(k, x);
            else if (v instanceof Long x) n.putLong(k, x);
            else if (v instanceof Double x) n.putDouble(k, x);
            else if (v instanceof Boolean x) n.putBoolean(k, x);
            else n.putString(k, String.valueOf(v));
        }
        SurvieRpgClient.send(action, n);
    }
}
