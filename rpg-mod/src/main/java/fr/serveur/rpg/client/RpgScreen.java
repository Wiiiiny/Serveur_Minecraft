package fr.serveur.rpg.client;

import fr.serveur.rpg.items.ModItems;
import fr.serveur.rpg.jobs.Job;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
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
        PROFIL("Profil"), QUETES("Quêtes"), FORGE("Forge"), BANQUE("Banque"), MARCHE("Marché"),
        BOUTIQUE("Boutique"), CLASSEMENT("Classements"), GM("Maître du jeu");
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
    /** Champs de saisie du tableau de bord GM, par clé (réglage, article, joueur...). */
    private final Map<String, TextFieldWidget> valueFields = new HashMap<>();
    private final Map<String, String> syncedValues = new HashMap<>();
    private final Set<String> seenFields = new HashSet<>();
    private boolean confirmPrestige;

    public RpgScreen() {
        super(Text.literal("Survie RPG"));
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
        switch (tab) {
            case BANQUE -> {
                field(cx + 8, cy + 86, 64, amountText, true, s -> amountText = s, "Montant");
                field(cx + 8, cy + 142, 104, nameText, false, s -> nameText = s, "Pseudo");
                field(cx + 118, cy + 142, 56, transferText, true, s -> transferText = s, "Montant");
            }
            case MARCHE -> field(cx + cw - 116, cy + ch - 18, 50, priceText, true, s -> priceText = s, "Prix");
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
        confirmPrestige = false;
        clearAndInit();
    }

    @Override
    public void tick() {
        for (Element e : children()) if (e instanceof TextFieldWidget f) f.tick();
        if (++refresh % 40 == 0) SurvieRpgClient.send("refresh", null);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (getFocused() instanceof TextFieldWidget f && f.isFocused()) {
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
                case QUETES -> quetes(c, d);
                case FORGE -> forge(c, d);
                case BANQUE -> banque(c, d);
                case MARCHE -> marche(c, d);
                case BOUTIQUE -> boutique(c, d);
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
            boolean hover = in(sx, sy, 90, 18);
            if (active) {
                c.fillGradient(sx, sy, sx + 90, sy + 18, 0xFF3D2765, 0xFF221539);
                c.fill(sx, sy, sx + 2, sy + 18, Ui.GOLD);
            } else if (hover) {
                c.fill(sx, sy, sx + 90, sy + 18, 0xFF231A31);
            }
            int color = t == Tab.GM ? (active ? Ui.GOLD : Ui.ORANGE) : active ? Ui.GOLD : hover ? Ui.TEXT : Ui.MUTED;
            c.drawText(textRenderer, t.label, sx + 8, sy + 5, color, active);
            if (!active) hots.add(new Hot(sx, sy, 90, 18, () -> switchTab(t)));
            sy += 20;
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
        NbtList forge = d.getList("forge", NbtElement.COMPOUND_TYPE);
        int ready = 0;
        for (int i = 0; i < forge.size(); i++) if (forge.getCompound(i).getBoolean("ok")) ready++;
        if (ready > 1) {
            y += 26;
            Ui.card(c, cx, y, cw, 22, false);
            c.drawText(textRenderer, "Tu peux forger " + ready + " outils officiels.", cx + 6, y + 7, Ui.GOLD, false);
            button(c, cx + cw - 60, y + 4, 56, 14, "Forge", true, Style.PRIMARY, () -> switchTab(Tab.FORGE));
        }
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
        c.drawText(textRenderer, "Marché, boutique, forge et changement de métier", cx, cy + 172, Ui.FAINT, false);
        c.drawText(textRenderer, "se paient avec l'argent de ton compte.", cx, cy + 183, Ui.FAINT, false);
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
        c.drawText(textRenderer, held.isEmpty() ? "Prends un objet en main pour le vendre" : "Vendre l'objet en main pour",
                cx + 22, by + 4, held.isEmpty() ? Ui.FAINT : Ui.TEXT, false);
        long price = parse(priceText);
        coin(c, cx + cw - 64, by + 4);
        button(c, cx + cw - 56, by, 54, 16, "Vendre", !held.isEmpty() && price > 0, Style.PRIMARY, () -> act("hdv.sell", "price", price));
    }

    private void boutique(DrawContext c, NbtCompound d) {
        NbtList list = d.getList("shop", NbtElement.COMPOUND_TYPE);
        int y = title(c, "Boutique du royaume", cy);
        long money = d.getCompound("me").getLong("money");
        int rows = Math.max(1, (cy + ch - 26 - y) / 22);
        int pages = Math.max(1, (list.size() + rows - 1) / rows);
        page = Math.max(0, Math.min(page, pages - 1));
        if (list.isEmpty()) centered(c, "La boutique est vide.", cx + cw / 2, y + 30, Ui.FAINT);
        for (int i = page * rows; i < Math.min(list.size(), (page + 1) * rows); i++) {
            NbtCompound m = list.getCompound(i);
            ItemStack stack = ItemStack.fromNbt(m.getCompound("stack"));
            long price = m.getLong("price");
            Ui.card(c, cx, y, cw, 20, false);
            item(c, stack, cx + 3, y + 2);
            c.drawText(textRenderer, textRenderer.trimToWidth(stack.getName().getString() + (stack.getCount() > 1 ? " ×" + stack.getCount() : ""), cw - 140),
                    cx + 23, y + 6, Ui.TEXT, false);
            textRight(c, Ui.number(price), cx + cw - 72, y + 6, Ui.GOLD);
            coin(c, cx + cw - 70, y + 6);
            int id = m.getInt("id");
            button(c, cx + cw - 58, y + 3, 54, 14, "Acheter", money >= price, Style.PRIMARY, () -> act("shop.buy", "id", id));
            y += 22;
        }
        pager(c, cy + ch - 18, page, pages, p -> page = p);
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
        if (selected.getBoolean("awaiting")) c.drawText(textRenderer, "☠ Mort, attend la nuit", px + 6, y + 43, Ui.RED, false);

        int by = y + 60, gw = (pw - 8) / 3;
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
    }

    private void gmMonde(DrawContext c, NbtCompound gm, int y) {
        NbtCompound s = gm.getCompound("settings");
        c.drawText(textRenderer, "Heure du monde", cx + 4, y + 4, Ui.TEXT, false);
        button(c, cx + cw - 172, y + 1, 84, 14, "☀ Jour", true, Style.NORMAL, () -> act("gm.time", "to", "day"));
        button(c, cx + cw - 86, y + 1, 84, 14, "☾ Nuit", true, Style.NORMAL, () -> act("gm.time", "to", "night"));
        y += 18;
        y = setting(c, y, "Durée du jour", "min", "dayMin", s.getDouble("dayMin"), true);
        y = setting(c, y, "Durée de la nuit", "min", "nightMin", s.getDouble("nightMin"), true);
        y = toggle(c, y, "Nether", s.getBoolean("nether"), "Ouvert", "Fermé", "Fermer", "Ouvrir", "nether");
        y = toggle(c, y, "End", s.getBoolean("end"), "Ouvert", "Fermé", "Fermer", "Ouvrir", "end");
        c.drawText(textRenderer, "Salle d'attente des morts", cx + 4, y + 4, Ui.TEXT, false);
        boolean room = s.getBoolean("waitingRoom");
        c.drawText(textRenderer, room ? "définie" : "non définie", cx + 150, y + 4, room ? Ui.GREEN : Ui.RED, false);
        button(c, cx + cw - 86, y + 1, 84, 14, "Définir ici", true, Style.NORMAL, () -> act("gm.room.here"));
        y += 18;
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
        c.drawText(textRenderer, textRenderer.trimToWidth("En circulation : " + Ui.number(st.getLong("totalMoney")) + " pièces · "
                + st.getInt("auctions") + " annonces · " + st.getInt("chests") + " coffres protégés", cw - 4), cx + 4, y + 2, Ui.FAINT, false);
        y += 14;
        NbtList shop = ClientData.data.getList("shop", NbtElement.COMPOUND_TYPE);
        y = title(c, "Boutique (" + shop.size() + " articles)", y);
        int rows = Math.max(1, (cy + ch - 42 - y) / 18);
        int pages = Math.max(1, (shop.size() + rows - 1) / rows);
        shopPage = Math.max(0, Math.min(shopPage, pages - 1));
        for (int i = shopPage * rows; i < Math.min(shop.size(), (shopPage + 1) * rows); i++) {
            NbtCompound m = shop.getCompound(i);
            ItemStack stack = ItemStack.fromNbt(m.getCompound("stack"));
            int id = m.getInt("id");
            long current = m.getLong("price");
            item(c, stack, cx + 2, y);
            c.drawText(textRenderer, textRenderer.trimToWidth(stack.getName().getString() + (stack.getCount() > 1 ? " ×" + stack.getCount() : ""), cw - 146),
                    cx + 22, y + 4, Ui.TEXT, false);
            long typed = parse(valueField("shop:" + id, cx + cw - 118, y + 1, 44, String.valueOf(current), "\\d*").getText());
            coin(c, cx + cw - 70, y + 4);
            button(c, cx + cw - 58, y + 1, 26, 14, "OK", typed > 0 && typed != current, Style.PRIMARY, () -> {
                act("gm.shop.price", "id", id, "value", typed);
                unfocus();
            });
            button(c, cx + cw - 30, y + 1, 28, 14, "✖", true, Style.DANGER, () -> act("gm.shop.remove", "id", id));
            y += 18;
        }
        pager(c, cy + ch - 38, shopPage, pages, p -> shopPage = p);
        int by = cy + ch - 18;
        Ui.rule(c, cx, by - 4, cw);
        c.drawText(textRenderer, "Ajouter l'objet en main au prix de", cx + 2, by + 4, Ui.TEXT, false);
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

    private static long parse(String s) {
        try {
            return s == null || s.isBlank() ? 0 : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
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
