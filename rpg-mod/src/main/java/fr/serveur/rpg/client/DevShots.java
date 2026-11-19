package fr.serveur.rpg.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Outil de développement uniquement : actif seulement avec l'option JVM -Dsurvierpg.devshots=true
 * (tâche Gradle « runShots »). Prépare un monde de test, ouvre chaque onglet de l'interface
 * et en fait une capture dans run/screenshots/, puis quitte le jeu.
 */
final class DevShots {
    private record Step(int delay, Runnable action) {}

    private static final List<Step> STEPS = new ArrayList<>();
    private static int index = -1, timer;

    private DevShots() {}

    static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.getServer() == null) return;
            if (index < 0) {
                client.options.pauseOnLostFocus = false;
                build(client);
                index = 0;
                timer = 0;
            }
            if (index >= STEPS.size()) return;
            if (++timer < STEPS.get(index).delay) return;
            timer = 0;
            STEPS.get(index++).action.run();
        });
    }

    private static void server(MinecraftClient client, String... commands) {
        IntegratedServer server = client.getServer();
        String name = client.player.getGameProfile().getName();
        server.execute(() -> {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(name);
            if (p == null) return;
            server.getPlayerManager().addToOperators(p.getGameProfile());
            for (String cmd : commands) {
                server.getCommandManager().executeWithPrefix(p.getCommandSource().withLevel(4), cmd.replace("@me", name));
            }
        });
    }

    private static void act(String action, String key, String value) {
        net.minecraft.nbt.NbtCompound n = new net.minecraft.nbt.NbtCompound();
        if (value.matches("-?\\d+")) n.putLong(key, Long.parseLong(value));
        else n.putString(key, value);
        SurvieRpgClient.send(action, n);
    }

    private static void act2(String action, String... kv) {
        net.minecraft.nbt.NbtCompound n = new net.minecraft.nbt.NbtCompound();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            String value = kv[i + 1];
            if (value.matches("-?\\d+")) n.putLong(kv[i], Long.parseLong(value));
            else if (value.matches("-?\\d+\\.\\d+")) n.putDouble(kv[i], Double.parseDouble(value));
            else n.putString(kv[i], value);
        }
        SurvieRpgClient.send(action, n);
    }

    private static void shot(MinecraftClient client, String name) {
        client.getToastManager().clear();
        ScreenshotRecorder.saveScreenshot(client.runDirectory, "survierpg-" + name + ".png", client.getFramebuffer(), msg -> { });
    }

    private static void open(MinecraftClient client, RpgScreen.Tab tab, String gmSub) {
        RpgScreen.devSelect(tab, gmSub);
        client.setScreen(new RpgScreen());
        SurvieRpgClient.send("open", null);
    }

    private static void build(MinecraftClient client) {
        STEPS.add(new Step(100, () -> server(client,
                "gamemode survival @me", "time set 6000", "gamerule announceAdvancements false",
                "advancement grant @me everything", "metier choisir mineur",
                "gm argent donner @me 480", "gm niveau @me 9", "gm xp @me 120",
                "gm quete donner @me Rapporter 20 bûches de chêne à la mairie",
                "item replace entity @me weapon.mainhand with minecraft:diamond 3", "hdv vendre 45",
                "setblock ~2 ~ ~2 survierpg:pierre_obscure", "setblock ~2 ~1 ~2 survierpg:pierre_obscure",
                "setblock ~3 ~ ~2 survierpg:pierre_obscure", "setblock ~3 ~1 ~3 survierpg:pierre_obscure",
                "gm event lancer recolte 15 Grande récolte",
                "item replace entity @me hotbar.0 with survierpg:pioche_tenebres",
                "item replace entity @me hotbar.1 with survierpg:piece_or 37",
                "item replace entity @me hotbar.2 with survierpg:pierre_obscure 12",
                "item replace entity @me hotbar.3 with survierpg:larme_de_vie 2",
                "item replace entity @me hotbar.4 with survierpg:crochet 3",
                "item replace entity @me hotbar.5 with survierpg:outil_mineur_fer",
                "item replace entity @me hotbar.6 with survierpg:outil_bucheron_diamant",
                "item replace entity @me hotbar.7 with survierpg:outil_aventurier_netherite",
                "give @me survierpg:piece_or 37", "give @me minecraft:iron_ingot 5", "give @me minecraft:stick 6",
                "give @me minecraft:cobblestone 12", "give @me minecraft:diamond 3", "gm larme @me 2",
                "tp @me ~ ~ ~ -45 25")));
        STEPS.add(new Step(80, () -> client.getToastManager().clear()));
        STEPS.add(new Step(60, () -> shot(client, "00-monde")));
        RpgScreen.Tab[] tabs = {RpgScreen.Tab.PROFIL, RpgScreen.Tab.QUETES, RpgScreen.Tab.FORGE, RpgScreen.Tab.BANQUE,
                RpgScreen.Tab.MARCHE, RpgScreen.Tab.BOUTIQUE, RpgScreen.Tab.CLASSEMENT};
        for (int i = 0; i < tabs.length; i++) {
            RpgScreen.Tab t = tabs[i];
            int n = i + 1;
            STEPS.add(new Step(10, () -> open(client, t, null)));
            STEPS.add(new Step(30, () -> shot(client, String.format("%02d-%s", n, t.name().toLowerCase()))));
        }
        String[] subs = {"joueurs", "monde", "economie", "auto"};
        for (int i = 0; i < subs.length; i++) {
            String s = subs[i];
            int n = 10 + i;
            STEPS.add(new Step(10, () -> open(client, RpgScreen.Tab.GM, s)));
            STEPS.add(new Step(30, () -> shot(client, n + "-gm-" + s)));
        }
        // Actions réelles envoyées au serveur, comme un clic dans l'interface.
        STEPS.add(new Step(10, () -> open(client, RpgScreen.Tab.FORGE, null)));
        STEPS.add(new Step(20, () -> act("forge", "tier", "FER")));
        STEPS.add(new Step(30, () -> shot(client, "20-action-forge")));
        STEPS.add(new Step(10, () -> open(client, RpgScreen.Tab.BANQUE, null)));
        STEPS.add(new Step(20, () -> act("bank.deposit", "amount", "-1")));
        STEPS.add(new Step(30, () -> shot(client, "21-action-depot")));
        STEPS.add(new Step(10, () -> open(client, RpgScreen.Tab.BOUTIQUE, null)));
        STEPS.add(new Step(20, () -> act("shop.buy", "id", "3")));
        STEPS.add(new Step(30, () -> shot(client, "22-action-achat")));
        STEPS.add(new Step(10, () -> open(client, RpgScreen.Tab.PROFIL, null)));
        STEPS.add(new Step(20, () -> act("job.choose", "job", "BUCHERON")));
        STEPS.add(new Step(30, () -> shot(client, "23-action-metier")));
        // Valeurs saisies dans le tableau de bord GM.
        STEPS.add(new Step(10, () -> open(client, RpgScreen.Tab.GM, "monde")));
        STEPS.add(new Step(20, () -> act2("gm.set", "key", "dayMin", "value", "7.5")));
        STEPS.add(new Step(30, () -> shot(client, "24-gm-valeur-jour")));
        STEPS.add(new Step(10, () -> open(client, RpgScreen.Tab.GM, "joueurs")));
        STEPS.add(new Step(20, () -> act2("gm.player", "name", client.player.getGameProfile().getName(), "op", "setmoney", "value", "1234")));
        STEPS.add(new Step(30, () -> shot(client, "25-gm-valeur-pieces")));
        // Nuit (avec Nightfall dans le client de test) : brume, feux follets.
        STEPS.add(new Step(10, () -> client.setScreen(null)));
        STEPS.add(new Step(5, () -> server(client, "gm cycle passer nuit", "tp @me ~ ~ ~ 0 0")));
        STEPS.add(new Step(200, () -> shot(client, "30-nuit")));
        STEPS.add(new Step(20, () -> client.scheduleStop()));
    }
}
