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
                String scenario = System.getProperty("survierpg.devshots.scenario");
                if ("sombre".equals(scenario)) buildSombre(client);
                else if ("factions".equals(scenario)) buildFactions(client);
                else build(client);
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

    /** Exécute du code côté serveur intégré, avec le joueur de test. */
    private static void onServer(MinecraftClient client, java.util.function.BiConsumer<net.minecraft.server.MinecraftServer, ServerPlayerEntity> action) {
        IntegratedServer server = client.getServer();
        String name = client.player.getGameProfile().getName();
        server.execute(() -> {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(name);
            if (p != null) action.accept(server, p);
        });
    }

    private static void log(MinecraftClient client, String message) {
        fr.serveur.rpg.SurvieRpg.LOGGER.info("[devshots] {}", message);
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
        // Charge les classes visées par les mixins des métiers supplémentaires : un mixin mal ciblé ferait planter ici.
        STEPS.add(new Step(5, () -> {
            String[] classes = {"net.minecraft.screen.AnvilScreenHandler", "net.minecraft.screen.EnchantmentScreenHandler",
                    "net.minecraft.screen.BrewingStandScreenHandler$PotionSlot", "net.minecraft.block.entity.BrewingStandBlockEntity",
                    "net.minecraft.block.entity.LootableContainerBlockEntity", "net.minecraft.entity.passive.MerchantEntity",
                    "net.minecraft.entity.projectile.FishingBobberEntity", "net.minecraft.item.FishingRodItem"};
            for (String name : classes) {
                try {
                    Class.forName(net.fabricmc.loader.api.FabricLoader.getInstance().getMappingResolver()
                            .mapClassName("named", name), true, DevShots.class.getClassLoader());
                    log(client, "mixin OK : " + name);
                } catch (Throwable t) {
                    log(client, "mixin ÉCHEC : " + name + " : " + t);
                }
            }
        }));
        // Métier supplémentaire, zone jouable et balise du Voyageur.
        STEPS.add(new Step(10, () -> server(client, "gm metier2 retirer @me")));
        STEPS.add(new Step(10, () -> open(client, RpgScreen.Tab.METIER2, null)));
        STEPS.add(new Step(30, () -> shot(client, "26-metier2-choix")));
        STEPS.add(new Step(10, () -> act("side.choose", "job", "INGENIEUR")));
        STEPS.add(new Step(30, () -> shot(client, "27-metier2-ingenieur")));
        STEPS.add(new Step(10, () -> open(client, RpgScreen.Tab.PROFIL, null)));
        STEPS.add(new Step(30, () -> shot(client, "28-profil-2e-metier")));
        STEPS.add(new Step(10, () -> open(client, RpgScreen.Tab.GM, "joueurs")));
        STEPS.add(new Step(30, () -> shot(client, "29-gm-joueurs-metier2")));
        int[] origin = new int[2];
        STEPS.add(new Step(10, () -> {
            origin[0] = client.player.getBlockX();
            origin[1] = client.player.getBlockZ();
            server(client, "gm bordure coin1", "gm bordure coin2 " + (origin[0] + 40) + " " + (origin[1] + 40));
            open(client, RpgScreen.Tab.GM, "monde");
        }));
        STEPS.add(new Step(30, () -> shot(client, "31-gm-monde-zone")));
        // Un non-Voyageur qui sort de la zone est ramené à l'intérieur.
        STEPS.add(new Step(10, () -> {
            client.setScreen(null);
            server(client, "tp @me " + (origin[0] - 6) + " ~ " + (origin[1] + 20) + " 90 10");
        }));
        STEPS.add(new Step(40, () -> log(client, "limite : x=" + client.player.getBlockX() + " (zone à partir de x=" + origin[0] + ")")));
        STEPS.add(new Step(10, () -> shot(client, "32-limite-ramene")));
        // Le Constructeur ne peut pas poser de redstone.
        STEPS.add(new Step(10, () -> server(client, "gm metier2 definir @me constructeur",
                "item replace entity @me weapon.mainhand with minecraft:redstone 8", "tp @me ~ ~ ~ 90 60")));
        STEPS.add(new Step(20, () -> {
            net.minecraft.util.math.BlockPos ground = client.player.getBlockPos().down().west();
            client.interactionManager.interactBlock(client.player, net.minecraft.util.Hand.MAIN_HAND,
                    new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(ground).add(0, 0.5, 0),
                            net.minecraft.util.math.Direction.UP, ground, false));
        }));
        STEPS.add(new Step(10, () -> shot(client, "33-redstone-refusee")));
        STEPS.add(new Step(5, () -> {
            net.minecraft.util.math.BlockPos above = client.player.getBlockPos().west();
            client.getServer().execute(() -> log(client, "redstone posée par le Constructeur ? "
                    + client.getServer().getOverworld().getBlockState(above).getBlock()));
        }));
        // Le Voyageur franchit la limite avec sa balise (10 s immobile).
        STEPS.add(new Step(10, () -> server(client, "gm metier2 definir @me voyageur", "tp @me ~ ~ ~ -90 10")));
        STEPS.add(new Step(10, () -> server(client, "clear @me survierpg:balise",
                "item replace entity @me weapon.mainhand with survierpg:balise")));
        STEPS.add(new Step(20, () -> {
            log(client, "balise en main : " + client.player.getMainHandStack().getItem());
            client.interactionManager.interactItem(client.player, net.minecraft.util.Hand.MAIN_HAND);
        }));
        STEPS.add(new Step(100, () -> shot(client, "34-balise-canalisation")));
        STEPS.add(new Step(150, () -> log(client, "après la balise : x=" + client.player.getBlockX() + " (limite x=" + origin[0] + ")")));
        STEPS.add(new Step(10, () -> shot(client, "35-balise-dehors")));
        STEPS.add(new Step(10, () -> open(client, RpgScreen.Tab.METIER2, null)));
        STEPS.add(new Step(30, () -> shot(client, "36-metier2-voyageur")));
        STEPS.add(new Step(10, () -> server(client, "gm bordure retirer")));
        // Biome Terres cendrées (TerraBlender) : on le cherche et on s'y téléporte.
        STEPS.add(new Step(10, () -> {
            client.setScreen(null);
            var server = client.getServer();
            String name = client.player.getGameProfile().getName();
            server.execute(() -> {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(name);
                var world = server.getOverworld();
                var found = world.locateBiome(e -> e.matchesKey(fr.serveur.rpg.world.biome.AshlandsTerraBlender.TERRES_CENDREES),
                        p.getBlockPos().add(4000, 0, 4000), 6400, 32, 64); // loin : chunks pas encore générés
                if (found == null) {
                    log(client, "Terres cendrées introuvables à moins de 6400 blocs");
                    return;
                }
                var pos = found.getFirst();
                world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
                int y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
                log(client, "Terres cendrées trouvées en " + pos.getX() + " " + y + " " + pos.getZ());
                p.teleport(world, pos.getX() + 0.5, y + 1, pos.getZ() + 0.5, 0, 12);
            });
        }));
        STEPS.add(new Step(200, () -> log(client, "biome sous le joueur : " + client.world.getBiome(client.player.getBlockPos())
                .getKey().map(k -> k.getValue().toString()).orElse("?") + ", sol : " + client.world.getBlockState(client.player.getBlockPos().down()).getBlock())));
        STEPS.add(new Step(5, () -> shot(client, "37-terres-cendrees")));
        STEPS.add(new Step(5, () -> server(client, "tp @me ~ ~ ~ 180 20")));
        STEPS.add(new Step(40, () -> shot(client, "38-terres-cendrees-2")));
        // Économie automatisée : paie des métiers et plafond, boutique dynamique, virement plafonné, gel, audit.
        STEPS.add(new Step(10, () -> onServer(client, (server, p) -> {
            var data = fr.serveur.rpg.data.RpgState.get(server).player(p);
            data.earnedToday.clear();
            long before = data.money;
            long a = fr.serveur.rpg.economy.Economy.payJob(p, 100); // 100 XP × 0,05 = 5 pièces
            long b = fr.serveur.rpg.economy.Economy.earn(server, p.getUuid(), fr.serveur.rpg.economy.Economy.Source.METIER, 1000, "test");
            log(client, "paie métier : 100 XP → " + a + " pièces ; gain de 1000 au-delà du plafond 150 → " + b
                    + " versées (attendu 287) ; solde " + before + " → " + data.money);
        })));
        STEPS.add(new Step(10, () -> {
            server(client, "give @me minecraft:cod 16");
            open(client, RpgScreen.Tab.BOUTIQUE, null);
        }));
        STEPS.add(new Step(30, () -> shot(client, "40-boutique-dynamique")));
        STEPS.add(new Step(5, () -> onServer(client, (server, p) -> {
            var state = fr.serveur.rpg.data.RpgState.get(server);
            var torch = state.shop.stream().filter(m -> m.stack.isOf(net.minecraft.item.Items.TORCH)).findFirst().orElseThrow();
            var cod = state.shop.stream().filter(m -> m.stack.isOf(net.minecraft.item.Items.COD)).findFirst().orElseThrow();
            long p0 = fr.serveur.rpg.economy.DynamicShop.buyPrice(torch);
            for (int i = 0; i < 30; i++) fr.serveur.rpg.economy.DynamicShop.buy(p, torch);
            long p1 = fr.serveur.rpg.economy.DynamicShop.buyPrice(torch);
            long[] got = {0};
            String err = fr.serveur.rpg.economy.DynamicShop.sell(p, cod, got);
            log(client, "boutique : torches " + p0 + " → " + p1 + " après 30 achats (stock " + Math.round(torch.stock) + "/" + Math.round(torch.stockTarget)
                    + ") ; rachat de 16 morues : " + (err == null ? got[0] + " pièces" : err));
        })));
        STEPS.add(new Step(10, () -> open(client, RpgScreen.Tab.BOUTIQUE, null)));
        STEPS.add(new Step(30, () -> shot(client, "41-boutique-apres-achats")));
        STEPS.add(new Step(5, () -> onServer(client, (server, p) -> {
            var state = fr.serveur.rpg.data.RpgState.get(server);
            var other = state.players.entrySet().stream().filter(e -> !e.getKey().equals(p.getUuid())).findFirst().orElse(null);
            if (other == null) {
                log(client, "virement : pas d'autre joueur connu pour tester");
                return;
            }
            String big = fr.serveur.rpg.economy.Economy.transfer(server, p.getUuid(), other.getKey(), 5000);
            String small = fr.serveur.rpg.economy.Economy.transfer(server, p.getUuid(), other.getKey(), 10);
            log(client, "virement de 5000 : " + big + " | virement de 10 : " + (small == null ? "ok" : small));
            state.player(p).frozen = true;
            var torch = state.shop.stream().filter(m -> m.stack.isOf(net.minecraft.item.Items.TORCH)).findFirst().orElseThrow();
            log(client, "achat avec un compte gelé : " + fr.serveur.rpg.economy.DynamicShop.buy(p, torch));
            state.player(p).frozen = false;
        })));
        STEPS.add(new Step(10, () -> server(client, "gm economie mesurer", "gm economie transactions @me 6")));
        STEPS.add(new Step(30, () -> shot(client, "42-audit-economie")));
        STEPS.add(new Step(10, () -> open(client, RpgScreen.Tab.GM, "economie")));
        STEPS.add(new Step(30, () -> shot(client, "43-gm-economie")));
        STEPS.add(new Step(10, () -> open(client, RpgScreen.Tab.BANQUE, null)));
        STEPS.add(new Step(30, () -> shot(client, "44-banque")));
        STEPS.add(new Step(5, () -> onServer(client, (server, p) -> log(client, "journal : "
                + fr.serveur.rpg.economy.Ledger.read(server, 1).size() + " transactions aujourd'hui, sauvegardes : "
                + fr.serveur.rpg.economy.EconomyTicker.backups(server).size()))));
        // ---------------------------------------------------------------- zone de raid
        STEPS.add(new Step(5, () -> onServer(client, (server, p) -> {
            var s = fr.serveur.rpg.raid.RaidState.get(server);
            log(client, "raid : " + s.status + " " + fr.serveur.rpg.raid.RaidBuilder.progress() + " % — " + fr.serveur.rpg.raid.RoomLibrary.load());
            if (s.status != fr.serveur.rpg.raid.RaidState.Status.OUVERT && !fr.serveur.rpg.raid.RaidBuilder.running()) {
                log(client, "raid : régénération forcée → " + fr.serveur.rpg.raid.RaidCycle.startCycle(server, "test"));
            }
        })));
        STEPS.add(new Step(600, () -> onServer(client, (server, p) -> {
            var s = fr.serveur.rpg.raid.RaidState.get(server);
            log(client, "raid : " + s.status + " " + fr.serveur.rpg.raid.RaidBuilder.progress() + " %, " + s.rooms.size() + " salles, "
                    + s.chests.size() + " coffres, " + s.spawns.size() + " arrivées, marqueurs " + s.spots.stream().map(x -> x.type()).toList()
                    + ", socle " + s.pedestal + ", portail " + s.portalCenter + ", trésor " + s.treasure);
        })));
        STEPS.add(new Step(5, () -> server(client, "gm raid portail", "gamemode survival @me", "effect clear @me")));
        STEPS.add(new Step(10, () -> {
            open(client, RpgScreen.Tab.RAID, null);
        }));
        STEPS.add(new Step(30, () -> shot(client, "50-onglet-raid")));
        STEPS.add(new Step(5, () -> act("raid.enter", "x", "1")));
        STEPS.add(new Step(40, () -> {
            client.setScreen(null);
            onServer(client, (server, p) -> {
                var w = fr.serveur.rpg.raid.RaidWorld.get(server);
                int statues = 0, thieves = 0, bosses = 0;
                for (var e : w.iterateEntities()) {
                    if (e instanceof fr.serveur.rpg.raid.statue.StatueEntity) statues++;
                    if (e instanceof fr.serveur.rpg.raid.thief.ThiefEntity) thieves++;
                    if (e.getCommandTags().contains(fr.serveur.rpg.raid.RaidMobs.BOSS_TAG)) bosses++;
                }
                log(client, "dans le raid ? " + fr.serveur.rpg.raid.RaidWorld.isIn(p) + " en " + p.getBlockPos().toShortString()
                        + " — statues " + statues + ", voleurs " + thieves + ", gardiens " + bosses);
            });
        }));
        STEPS.add(new Step(20, () -> shot(client, "51-raid-arrivee")));
        // Statue : figée quand on la regarde, elle avance quand on se retourne.
        double[] statueStart = new double[1];
        java.util.UUID[] statueId = new java.util.UUID[1];
        STEPS.add(new Step(5, () -> onServer(client, (server, p) -> {
            var w = fr.serveur.rpg.raid.RaidWorld.get(server);
            var look = p.getRotationVec(1f);
            var at = net.minecraft.util.math.BlockPos.ofFloored(p.getX() + look.x * 6, p.getY(), p.getZ() + look.z * 6);
            var st = fr.serveur.rpg.raid.statue.Statues.TYPE.create(w);
            st.refreshPositionAndAngles(at, 0, 0);
            w.spawnEntity(st);
            statueId[0] = st.getUuid();
            statueStart[0] = st.distanceTo(p);
            p.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.RESISTANCE, 20 * 120, 4));
        })));
        STEPS.add(new Step(40, () -> shot(client, "52-statue-regardee")));
        STEPS.add(new Step(5, () -> onServer(client, (server, p) -> {
            var st = fr.serveur.rpg.raid.RaidWorld.get(server).getEntity(statueId[0]);
            log(client, "statue regardée : distance " + String.format("%.1f", statueStart[0]) + " → " + String.format("%.1f", st == null ? -1 : st.distanceTo(p))
                    + " (doit rester pareille)");
            statueStart[0] = st == null ? -1 : st.distanceTo(p);
        })));
        STEPS.add(new Step(5, () -> server(client, "tp @me ~ ~ ~ ~180 ~")));
        STEPS.add(new Step(30, () -> onServer(client, (server, p) -> {
            var st = fr.serveur.rpg.raid.RaidWorld.get(server).getEntity(statueId[0]);
            log(client, "statue dans le dos : distance " + String.format("%.1f", statueStart[0]) + " → " + String.format("%.1f", st == null ? -1 : st.distanceTo(p))
                    + " (doit diminuer) ; peur " + fr.serveur.rpg.raid.statue.Fear.level(p));
        })));
        STEPS.add(new Step(5, () -> server(client, "tp @me ~ ~ ~ ~180 ~")));
        STEPS.add(new Step(40, () -> shot(client, "53-statue-proche")));
        // Coups : sans Brise-statue, rien ; avec, 3 coups et elle tombe.
        STEPS.add(new Step(5, () -> onServer(client, (server, p) -> {
            var st = fr.serveur.rpg.raid.RaidWorld.get(server).getEntity(statueId[0]);
            if (st == null) return;
            p.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, st.getPos().add(0, 1.5, 0));
            p.networkHandler.requestTeleport(p.getX(), p.getY(), p.getZ(), p.getYaw(), p.getPitch());
        })));
        STEPS.add(new Step(15, () -> onServer(client, (server, p) -> {
            var st = (fr.serveur.rpg.raid.statue.StatueEntity) fr.serveur.rpg.raid.RaidWorld.get(server).getEntity(statueId[0]);
            if (st == null) return;
            log(client, "statue regardée avant les coups ? " + st.isWatched());
            p.getInventory().setStack(p.getInventory().selectedSlot, net.minecraft.item.ItemStack.EMPTY);
            boolean hurt = st.damage(p.getDamageSources().playerAttack(p), 5);
            p.getInventory().setStack(p.getInventory().selectedSlot, new net.minecraft.item.ItemStack(fr.serveur.rpg.items.RaidItems.BRISE_STATUE));
            for (int i = 0; i < 3; i++) st.damage(p.getDamageSources().playerAttack(p), 5);
            log(client, "statue frappée à la main : " + hurt + " ; après 3 coups de Brise-statue : " + (st.isRemoved() ? "détruite" : "encore là")
                    + " ; Brise-statue usé de " + p.getMainHandStack().getDamage());
        })));
        STEPS.add(new Step(20, () -> shot(client, "54-statue-brisee")));
        // Voleur : il vole un objet, on paie la rançon.
        java.util.UUID[] thiefId = new java.util.UUID[1];
        STEPS.add(new Step(5, () -> onServer(client, (server, p) -> {
            var w = fr.serveur.rpg.raid.RaidWorld.get(server);
            p.getInventory().insertStack(new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND, 5));
            var t = fr.serveur.rpg.raid.thief.Thieves.TYPE.create(w);
            t.refreshPositionAndAngles(p.getBlockPos().add(1, 0, 0), 0, 0);
            w.spawnEntity(t);
            thiefId[0] = t.getUuid();
        })));
        STEPS.add(new Step(60, () -> onServer(client, (server, p) -> {
            var t = (fr.serveur.rpg.raid.thief.ThiefEntity) fr.serveur.rpg.raid.RaidWorld.get(server).getEntity(thiefId[0]);
            log(client, "voleur : mode " + (t == null ? "?" : t.mode()) + ", tient " + (t == null ? "?" : t.getMainHandStack()));
        })));
        STEPS.add(new Step(5, () -> shot(client, "55-voleur")));
        STEPS.add(new Step(5, () -> onServer(client, (server, p) -> {
            var t = (fr.serveur.rpg.raid.thief.ThiefEntity) fr.serveur.rpg.raid.RaidWorld.get(server).getEntity(thiefId[0]);
            if (t == null) return;
            p.getInventory().setStack(p.getInventory().selectedSlot, new net.minecraft.item.ItemStack(fr.serveur.rpg.items.ModItems.PIECE_OR, 64));
            var stolen = t.getMainHandStack().copy();
            t.interact(p, net.minecraft.util.Hand.MAIN_HAND);
            log(client, "rançon : objet volé " + stolen + " ; voleur tient maintenant " + t.getMainHandStack() + " ; pièces en main "
                    + p.getMainHandStack().getCount() + " ; affinité " + fr.serveur.rpg.data.RpgState.get(server).player(p).thiefAffinity);
        })));
        // Mort dans le raid : coffre de corps, retour au hub.
        STEPS.add(new Step(10, () -> server(client, "effect clear @me", "kill @me")));
        STEPS.add(new Step(30, () -> {
            if (client.player != null) client.player.requestRespawn();
            client.setScreen(null);
        }));
        STEPS.add(new Step(40, () -> onServer(client, (server, p) -> {
            var s = fr.serveur.rpg.raid.RaidState.get(server);
            log(client, "après la mort : dans le raid ? " + fr.serveur.rpg.raid.RaidWorld.isIn(p) + " ; coffres de corps " + s.corpses.size()
                    + (s.corpses.isEmpty() ? "" : " (" + s.corpses.get(0).items.size() + " piles)"));
        })));
        // Balise de retour.
        STEPS.add(new Step(5, () -> act("raid.enter", "x", "1")));
        STEPS.add(new Step(30, () -> onServer(client, (server, p) -> {
            p.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.RESISTANCE, 20 * 120, 4));
            p.getInventory().setStack(p.getInventory().selectedSlot, new net.minecraft.item.ItemStack(fr.serveur.rpg.items.RaidItems.BALISE_RETOUR));
            var loot = new net.minecraft.item.ItemStack(fr.serveur.rpg.items.RaidItems.COURONNE_DECHUE);
            fr.serveur.rpg.raid.RaidWorld.markLoot(loot);
            p.getInventory().insertStack(loot);
            log(client, "balise de retour lancée : " + fr.serveur.rpg.raid.ReturnBeacon.start(p, net.minecraft.util.Hand.MAIN_HAND));
        })));
        STEPS.add(new Step(40, () -> onServer(client, (server, p) -> log(client, "balise toujours en cours après 2 s ? "
                + fr.serveur.rpg.raid.ReturnBeacon.isChanneling(p) + " (peur " + fr.serveur.rpg.raid.statue.Fear.level(p) + ")"))));
        STEPS.add(new Step(240, () -> onServer(client, (server, p) -> {
            boolean marked = false;
            for (int i = 0; i < p.getInventory().size(); i++) marked |= fr.serveur.rpg.raid.RaidWorld.isLoot(p.getInventory().getStack(i));
            log(client, "balise de retour : dans le raid ? " + fr.serveur.rpg.raid.RaidWorld.isIn(p) + " ; butin encore marqué ? " + marked);
        })));
        // Extraction finale : 2 clés sur le socle, portail, bonus.
        STEPS.add(new Step(5, () -> act("raid.enter", "x", "1")));
        long[] moneyBefore = new long[1];
        STEPS.add(new Step(30, () -> onServer(client, (server, p) -> {
            var s = fr.serveur.rpg.raid.RaidState.get(server);
            if (s.pedestal == null) {
                log(client, "extraction : pas de socle");
                return;
            }
            p.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.RESISTANCE, 20 * 120, 4));
            var w = fr.serveur.rpg.raid.RaidWorld.get(server);
            p.teleport(w, s.pedestal.getX() + 0.5, s.pedestal.getY() + 1, s.pedestal.getZ() + 1.5, 180, 30);
            for (int i = 0; i < 2; i++) {
                var key = new net.minecraft.item.ItemStack(fr.serveur.rpg.items.RaidItems.CLE_RAID);
                fr.serveur.rpg.raid.Extraction.useKey(p, key, s.pedestal);
            }
            var crown = new net.minecraft.item.ItemStack(fr.serveur.rpg.items.RaidItems.COURONNE_DECHUE);
            fr.serveur.rpg.raid.RaidWorld.markLoot(crown);
            p.getInventory().insertStack(crown);
            moneyBefore[0] = fr.serveur.rpg.data.RpgState.get(server).player(p).money;
            log(client, "extraction : clés " + s.keysInserted + ", portail dans " + (s.portalOpensAt - System.currentTimeMillis()) / 1000 + " s");
            s.portalOpensAt = System.currentTimeMillis() + 3000; // on abrège l'attente pour le test
            s.portalClosesAt = s.portalOpensAt + 60_000;
        })));
        STEPS.add(new Step(40, () -> shot(client, "56-extraction-socle")));
        STEPS.add(new Step(40, () -> onServer(client, (server, p) -> {
            var s = fr.serveur.rpg.raid.RaidState.get(server);
            var w = fr.serveur.rpg.raid.RaidWorld.get(server);
            p.teleport(w, s.portalCenter.getX() + 0.5, s.portalCenter.getY(), s.portalCenter.getZ() + 0.5, 0, 0);
        })));
        STEPS.add(new Step(40, () -> onServer(client, (server, p) -> log(client, "extraction finale : dans le raid ? " + fr.serveur.rpg.raid.RaidWorld.isIn(p)
                + " ; solde " + moneyBefore[0] + " → " + fr.serveur.rpg.data.RpgState.get(server).player(p).money))));
        // Gardien : on le tue, il laisse une clé.
        STEPS.add(new Step(5, () -> act("raid.enter", "x", "1")));
        STEPS.add(new Step(30, () -> server(client, "gm raid spawn gardien")));
        STEPS.add(new Step(40, () -> shot(client, "57-gardien")));
        STEPS.add(new Step(5, () -> onServer(client, (server, p) -> {
            var w = fr.serveur.rpg.raid.RaidWorld.get(server);
            for (var m : w.getEntitiesByClass(net.minecraft.entity.mob.MobEntity.class, p.getBoundingBox().expand(12),
                    x -> x.getCommandTags().contains(fr.serveur.rpg.raid.RaidMobs.BOSS_TAG))) {
                {
                    var name = m.getName().getString();
                    log(client, "gardien « " + name + " » : " + Math.round(m.getMaxHealth()) + " PV");
                    m.damage(w.getDamageSources().outOfWorld(), 100000);
                    int keys = w.getEntitiesByClass(net.minecraft.entity.ItemEntity.class, m.getBoundingBox().expand(3),
                            i -> i.getStack().isOf(fr.serveur.rpg.items.RaidItems.CLE_RAID)).size();
                    log(client, "gardien « " + name + " » tué : clés au sol " + keys);
                    break;
                }
            }
        })));
        // Nuit (avec Nightfall dans le client de test) : brume, feux follets.
        STEPS.add(new Step(10, () -> client.setScreen(null)));
        STEPS.add(new Step(5, () -> server(client, "gm cycle passer nuit", "tp @me ~ ~ ~ 0 0")));
        STEPS.add(new Step(200, () -> shot(client, "30-nuit")));
        STEPS.add(new Step(20, () -> client.scheduleStop()));
    }

    // ================================================================ monde sombre

    private static int count(ServerPlayerEntity p, net.minecraft.item.Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().size(); i++) if (p.getInventory().getStack(i).isOf(item)) n += p.getInventory().getStack(i).getCount();
        return n;
    }

    private static int exotics(ServerPlayerEntity p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().size(); i++) if (fr.serveur.rpg.sombre.Rarete.isExotic(p.getInventory().getStack(i))) n++;
        return n;
    }

    private static int items(ServerPlayerEntity p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().size(); i++) n += p.getInventory().getStack(i).getCount();
        return n;
    }

    private static void click(MinecraftClient client, int[] pos) {
        net.minecraft.util.math.BlockPos b = new net.minecraft.util.math.BlockPos(pos[0], pos[1], pos[2]);
        client.interactionManager.interactBlock(client.player, net.minecraft.util.Hand.MAIN_HAND,
                new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(b), net.minecraft.util.math.Direction.UP, b, false));
    }

    /** Pose un coffre caché à côté du joueur (décalage dx, dz) et retient sa position. */
    private static void chestAt(MinecraftClient client, int[] pos, int dx, int dz, fr.serveur.rpg.sombre.CoffresCaches.Type type, boolean trap) {
        onServer(client, (server, p) -> {
            net.minecraft.util.math.BlockPos b = p.getBlockPos().add(dx, 0, dz);
            p.getServerWorld().setBlockState(b, net.minecraft.block.Blocks.AIR.getDefaultState());
            p.getServerWorld().setBlockState(b.up(), net.minecraft.block.Blocks.AIR.getDefaultState());
            String e = fr.serveur.rpg.sombre.CoffresCaches.spawnAt(p.getServerWorld(), b, type, trap);
            pos[0] = b.getX();
            pos[1] = b.getY();
            pos[2] = b.getZ();
            log(client, "coffre " + type + (trap ? " piégé" : "") + " posé en " + b.toShortString() + (e == null ? "" : " — ERREUR " + e)
                    + " ; coffres cachés : " + fr.serveur.rpg.sombre.SombreState.get(server).coffres.size());
        });
    }

    // ================================================================ factions

    private static fr.serveur.rpg.faction.Faction fac(net.minecraft.server.MinecraftServer server, String name) {
        return fr.serveur.rpg.faction.FactionState.get(server).byName(name);
    }

    private static void buildFactions(MinecraftClient client) {
        int[] heartA = new int[3], heartB = new int[3], chestB = new int[3];
        STEPS.add(new Step(100, () -> {
            onServer(client, (server, p) -> {
                var s = fr.serveur.rpg.faction.FactionState.get(server);
                for (var f : java.util.List.copyOf(s.factions.values())) fr.serveur.rpg.faction.Factions.dissolve(server, f, "test");
                s.wars.clear();
                s.invites.clear();
            });
            server(client, "gamemode survival @me", "gamerule announceAdvancements false", "gamerule doMobSpawning false",
                    "effect give @me minecraft:resistance 900 4 true", "effect give @me minecraft:saturation 900 0 true",
                    "gm lumiere permanente true", "gm lumiere nuit", "gm coffres vider", "clear @me", "gm argent donner @me 3000",
                    "item replace entity @me hotbar.0 with survierpg:etendard 2", "item replace entity @me hotbar.1 with minecraft:white_banner",
                    "tp @me ~ ~ ~ 0 10");
        }));
        STEPS.add(new Step(20, () -> client.getToastManager().clear()));
        STEPS.add(new Step(10, () -> open(client, RpgScreen.Tab.FACTION, null)));
        STEPS.add(new Step(30, () -> shot(client, "80-faction-sans")));
        STEPS.add(new Step(5, () -> act2("fac.create", "name", "Les Veilleurs")));
        STEPS.add(new Step(20, () -> onServer(client, (server, p) -> {
            var f = fac(server, "Les Veilleurs");
            log(client, "faction créée : " + (f != null) + (f == null ? "" : ", chef " + f.member(p.getUuid()).rank + ", niveau " + f.level + ", rayon " + f.radius()));
        })));
        // Étendard : vrai clic droit avec l'objet en main.
        STEPS.add(new Step(5, () -> {
            client.setScreen(null);
            client.player.getInventory().selectedSlot = 0;
            client.interactionManager.interactItem(client.player, net.minecraft.util.Hand.MAIN_HAND);
        }));
        STEPS.add(new Step(20, () -> onServer(client, (server, p) -> {
            var f = fac(server, "Les Veilleurs");
            heartA[0] = f.heart.getX();
            heartA[1] = f.heart.getY();
            heartA[2] = f.heart.getZ();
            log(client, "territoire : " + f.territories.size() + ", base " + f.baseTerritory + ", cœur " + f.heart.toShortString() + " bloc "
                    + p.getWorld().getBlockState(f.heart).getBlock() + ", bannière au-dessus " + p.getWorld().getBlockState(f.heart.up()).getBlock()
                    + ", étendards restants " + p.getInventory().getStack(0).getCount());
        })));
        STEPS.add(new Step(30, () -> shot(client, "81-coeur-pose")));
        // Trésor, boutique, bannière.
        STEPS.add(new Step(5, () -> act2("fac.deposit", "amount", "1500")));
        STEPS.add(new Step(10, () -> act2("fac.buy", "index", "0")));
        STEPS.add(new Step(10, () -> {
            net.minecraft.nbt.NbtCompound n = new net.minecraft.nbt.NbtCompound();
            n.putInt("base", 15);
            net.minecraft.nbt.NbtList ls = new net.minecraft.nbt.NbtList();
            String[][] layers = {{"bs", "14"}, {"cr", "4"}, {"bo", "1"}};
            for (String[] l : layers) {
                net.minecraft.nbt.NbtCompound c = new net.minecraft.nbt.NbtCompound();
                c.putString("p", l[0]);
                c.putInt("c", Integer.parseInt(l[1]));
                ls.add(c);
            }
            n.put("layers", ls);
            SurvieRpgClient.send("fac.banner", n);
        }));
        STEPS.add(new Step(20, () -> onServer(client, (server, p) -> {
            var f = fac(server, "Les Veilleurs");
            log(client, "trésor " + f.bank + ", niveau " + f.level + " (" + f.xp + " XP), étendards " + count(p, fr.serveur.rpg.faction.FactionItems.ETENDARD)
                    + ", bannière : fond " + f.bannerBase + ", " + f.layers.size() + " motifs");
        })));
        String[] subs = {"accueil", "membres", "tresor", "banniere", "podium"};
        for (int i = 0; i < subs.length; i++) {
            String sub = subs[i];
            int n = 82 + i;
            STEPS.add(new Step(5, () -> {
                setFacSub(sub);
                open(client, RpgScreen.Tab.FACTION, null);
            }));
            STEPS.add(new Step(30, () -> shot(client, n + "-faction-" + sub)));
        }
        // Deuxième faction (créée par le serveur, membre fictif) à 160 blocs, avec deux territoires.
        STEPS.add(new Step(5, () -> onServer(client, (server, p) -> {
            var s = fr.serveur.rpg.faction.FactionState.get(server);
            var b = new fr.serveur.rpg.faction.Faction();
            b.id = s.nextFactionId++;
            b.name = "Cendres Rouges";
            b.created = System.currentTimeMillis();
            b.bannerBase = net.minecraft.util.DyeColor.RED.getId();
            b.level = 2;
            b.xp = 100;
            b.heartHp = fr.serveur.rpg.faction.FactionConfig.get().coeurPointsDeVie;
            var m = new fr.serveur.rpg.faction.Faction.Member();
            m.id = java.util.UUID.randomUUID();
            m.name = "Ennemi";
            m.rank = fr.serveur.rpg.faction.Faction.Rank.CHEF;
            b.members.put(m.id, m);
            s.factions.put(b.id, b);
            var w = p.getServerWorld();
            int bx = p.getBlockX() + 160, bz = p.getBlockZ();
            int y = w.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            var t = new fr.serveur.rpg.faction.Faction.Territory();
            t.id = s.nextTerritoryId++;
            t.center = new net.minecraft.util.math.BlockPos(bx, y, bz);
            t.created = System.currentTimeMillis() - 1000;
            b.territories.add(t);
            fr.serveur.rpg.faction.Territories.setBase(server, b, t, w, t.center);
            var t2 = new fr.serveur.rpg.faction.Faction.Territory();
            t2.id = s.nextTerritoryId++;
            t2.center = new net.minecraft.util.math.BlockPos(bx + 60, y, bz + 40);
            t2.created = System.currentTimeMillis();
            b.territories.add(t2);
            s.markDirty();
            heartB[0] = b.heart.getX();
            heartB[1] = b.heart.getY();
            heartB[2] = b.heart.getZ();
            var chest = t.center.add(3, 0, 0);
            w.setBlockState(chest, net.minecraft.block.Blocks.CHEST.getDefaultState());
            w.setBlockState(chest.down(), net.minecraft.block.Blocks.STONE.getDefaultState());
            chestB[0] = chest.getX();
            chestB[1] = chest.getY();
            chestB[2] = chest.getZ();
            p.teleport(w, chest.getX() - 1.5, chest.getY(), chest.getZ() + 0.5, -90, 30);
            var near = new fr.serveur.rpg.faction.Territories.Owner(b, t);
            log(client, "faction B : cœur " + b.heart.toShortString() + " ; ici : " + fr.serveur.rpg.faction.Territories.ownerAt(server, w, p.getBlockPos()).faction().name
                    + " ; peut construire ? " + fr.serveur.rpg.faction.Territories.canBuild(p, near));
        })));
        // Protection : casser un bloc et ouvrir un coffre chez l'autre faction.
        STEPS.add(new Step(20, () -> onServer(client, (server, p) -> {
            var pos = new net.minecraft.util.math.BlockPos(chestB[0], chestB[1] - 1, chestB[2] + 1);
            p.getServerWorld().setBlockState(pos, net.minecraft.block.Blocks.DIRT.getDefaultState());
            boolean broken = p.interactionManager.tryBreakBlock(pos);
            log(client, "casser chez l'ennemi (hors guerre) : " + (broken ? "CASSÉ" : "refusé") + ", bloc " + p.getWorld().getBlockState(pos).getBlock());
        })));
        STEPS.add(new Step(5, () -> client.setScreen(null)));
        STEPS.add(new Step(5, () -> click(client, chestB)));
        STEPS.add(new Step(20, () -> log(client, "ouvrir un coffre chez l'ennemi : écran " + (client.currentScreen == null ? "aucun (refusé)" : client.currentScreen.getClass().getSimpleName()))));
        STEPS.add(new Step(5, () -> shot(client, "87-territoire-ennemi")));
        // Explosion hors guerre dans le territoire ennemi : aucun bloc détruit.
        STEPS.add(new Step(5, () -> onServer(client, (server, p) -> {
            var w = p.getServerWorld();
            var at = new net.minecraft.util.math.BlockPos(heartB[0] + 4, heartB[1], heartB[2] + 4);
            var ground = at.down();
            w.setBlockState(ground, net.minecraft.block.Blocks.STONE.getDefaultState());
            var before = w.getBlockState(ground).getBlock();
            w.createExplosion(null, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 3f, net.minecraft.world.World.ExplosionSourceType.TNT);
            log(client, "explosion en territoire : sol " + before + " → " + w.getBlockState(ground).getBlock());
        })));
        // Guerre : déclaration, nuit (attaques fermées), jour (ouvertes), Cœur brisé.
        STEPS.add(new Step(5, () -> onServer(client, (server, p) -> {
            var b = fac(server, "Cendres Rouges");
            log(client, "déclaration : " + fr.serveur.rpg.faction.Wars.declare(p, b.id));
        })));
        STEPS.add(new Step(5, () -> {
            setFacSub("guerre");
            open(client, RpgScreen.Tab.FACTION, null);
        }));
        STEPS.add(new Step(30, () -> shot(client, "88-guerre-declaree")));
        STEPS.add(new Step(5, () -> onServer(client, (server, p) -> {
            var a = fac(server, "Les Veilleurs");
            var w = fr.serveur.rpg.faction.FactionState.get(server).warOf(a.id);
            w.starts = System.currentTimeMillis(); // début abrégé pour le test
        })));
        STEPS.add(new Step(40, () -> onServer(client, (server, p) -> {
            var a = fac(server, "Les Veilleurs");
            var b = fac(server, "Cendres Rouges");
            var w = fr.serveur.rpg.faction.FactionState.get(server).warOf(a.id);
            log(client, "guerre active ? " + w.active + " ; attaques (nuit) ? " + fr.serveur.rpg.faction.Wars.attackOpen(server, w)
                    + " ; construire chez eux ? " + fr.serveur.rpg.faction.Territories.canBuild(p, new fr.serveur.rpg.faction.Territories.Owner(b, b.base())));
            fr.serveur.rpg.sombre.Lumiere.forceDay(server, "test");
        })));
        STEPS.add(new Step(5, () -> {
            client.setScreen(null);
            onServer(client, (server, p) -> p.teleport(p.getServerWorld(), heartB[0] + 0.5, heartB[1], heartB[2] - 1.5, 0, 35));
        }));
        STEPS.add(new Step(40, () -> onServer(client, (server, p) -> {
            var a = fac(server, "Les Veilleurs");
            var w = fr.serveur.rpg.faction.FactionState.get(server).warOf(a.id);
            log(client, "jour : attaques ouvertes ? " + fr.serveur.rpg.faction.Wars.attackOpen(server, w) + " ; cœur ennemi " + fac(server, "Cendres Rouges").heartHp);
        })));
        // Vrai coup (clic gauche) sur le Cœur ennemi.
        STEPS.add(new Step(5, () -> client.interactionManager.attackBlock(new net.minecraft.util.math.BlockPos(heartB[0], heartB[1], heartB[2]), net.minecraft.util.math.Direction.UP)));
        STEPS.add(new Step(5, () -> client.interactionManager.cancelBlockBreaking()));
        STEPS.add(new Step(15, () -> onServer(client, (server, p) -> {
            var b = fac(server, "Cendres Rouges");
            log(client, "après un coup : cœur ennemi " + b.heartHp + " / " + fr.serveur.rpg.faction.FactionConfig.get().coeurPointsDeVie);
            b.heartHp = 1; // on abrège : dernier point
        })));
        STEPS.add(new Step(20, () -> shot(client, "89-coeur-attaque")));
        STEPS.add(new Step(5, () -> client.interactionManager.attackBlock(new net.minecraft.util.math.BlockPos(heartB[0], heartB[1], heartB[2]), net.minecraft.util.math.Direction.UP)));
        STEPS.add(new Step(5, () -> client.interactionManager.cancelBlockBreaking()));
        STEPS.add(new Step(20, () -> onServer(client, (server, p) -> {
            var s = fr.serveur.rpg.faction.FactionState.get(server);
            var a = fac(server, "Les Veilleurs");
            var b = fac(server, "Cendres Rouges");
            var last = s.wars.get(s.wars.size() - 1);
            log(client, "fin : guerre finie ? " + last.over + ", vainqueur " + (last.winner == a.id ? a.name : String.valueOf(last.winner)) + " (" + last.reason + ")");
            log(client, "vainqueur : " + a.territories.size() + " territoires, " + a.warsWon + " victoire, " + a.points + " points, niveau " + a.level + " (" + a.xp + " XP)");
            log(client, "vaincu : " + b.territories.size() + " territoire(s), niveau " + b.level + " (" + b.xp + " XP), " + b.points + " points, cœur " + b.heartHp
                    + ", paix " + (b.peaceUntil - System.currentTimeMillis()) / 3_600_000 + " h");
            log(client, "nouvelle guerre pendant la paix : " + fr.serveur.rpg.faction.Wars.declare(p, b.id));
        })));
        STEPS.add(new Step(20, () -> shot(client, "90-victoire")));
        // Vote : abandonner le territoire gagné (un seul membre : adopté tout de suite).
        STEPS.add(new Step(5, () -> onServer(client, (server, p) -> {
            var a = fac(server, "Les Veilleurs");
            var t = a.territories.stream().filter(x -> x.id != a.baseTerritory).findFirst().orElse(null);
            String r = t == null ? "pas de territoire gagné" : fr.serveur.rpg.faction.Factions.startVote(p, fr.serveur.rpg.faction.Faction.VoteType.ABANDON, null, t.id);
            log(client, "vote d'abandon : " + r + " ; territoires " + a.territories.size());
        })));
        // Carte et guerre (sous-pages), puis retour à la base et coffre commun.
        STEPS.add(new Step(5, () -> {
            setFacSub("carte");
            open(client, RpgScreen.Tab.FACTION, null);
        }));
        STEPS.add(new Step(30, () -> shot(client, "91-faction-carte")));
        STEPS.add(new Step(5, () -> {
            setFacSub("guerre");
            open(client, RpgScreen.Tab.FACTION, null);
        }));
        STEPS.add(new Step(30, () -> shot(client, "92-faction-guerre-fin")));
        STEPS.add(new Step(5, () -> act2("fac.base.tp")));
        STEPS.add(new Step(130, () -> onServer(client, (server, p) -> log(client, "téléportation à la base : à " + Math.round(Math.sqrt(p.getBlockPos().getSquaredDistance(
                new net.minecraft.util.math.BlockPos(heartA[0], heartA[1], heartA[2])))) + " blocs du cœur"))));
        STEPS.add(new Step(5, () -> act2("fac.chest")));
        STEPS.add(new Step(20, () -> log(client, "coffre commun : écran " + (client.currentScreen == null ? "aucun" : client.currentScreen.getClass().getSimpleName()))));
        STEPS.add(new Step(10, () -> shot(client, "93-coffre-commun")));
        STEPS.add(new Step(5, () -> client.setScreen(null)));
        STEPS.add(new Step(5, () -> onServer(client, (server, p) -> {
            try (var files = java.nio.file.Files.list(fr.serveur.rpg.sombre.Journal.dir(server))) {
                files.filter(f -> f.getFileName().toString().startsWith("factions")).forEach(f -> {
                    try {
                        log(client, "journal " + f.getFileName() + " : " + java.nio.file.Files.readAllLines(f).size() + " lignes");
                    } catch (java.io.IOException ignored) { }
                });
            } catch (java.io.IOException e) {
                log(client, "journaux illisibles : " + e);
            }
            fr.serveur.rpg.sombre.Lumiere.forceNight(server);
        })));
        STEPS.add(new Step(20, () -> client.scheduleStop()));
    }

    private static void setFacSub(String sub) {
        RpgScreen.devFaction(sub);
    }

    private static void buildSombre(MinecraftClient client) {
        int[] chest = new int[3], boss = new int[3], exo = new int[3];
        long[] before = new long[4];
        String[] serial = new String[1];
        int[] shopId = new int[1];
        STEPS.add(new Step(100, () -> server(client,
                "gamemode survival @me", "gamerule announceAdvancements false", "gamerule doMobSpawning false",
                "effect give @me minecraft:resistance 900 4 true", "effect give @me minecraft:regeneration 900 2 true",
                "effect give @me minecraft:saturation 900 0 true",
                "gm lumiere permanente true", "gm lumiere nuit", "gm coffres vider",
                "clear @me",
                "item replace entity @me hotbar.0 with survierpg:eclat_lumiere 2",
                "item replace entity @me hotbar.1 with survierpg:detecteur_ombre",
                "item replace entity @me hotbar.2 with minecraft:diamond 3",
                "item replace entity @me hotbar.3 with minecraft:iron_ingot 5",
                "item replace entity @me hotbar.4 with minecraft:netherite_ingot",
                "item replace entity @me hotbar.5 with survierpg:brise_statue",
                "item replace entity @me hotbar.6 with minecraft:lantern",
                "item replace entity @me hotbar.7 with minecraft:emerald 4",
                "gm obscurcie @me 4", "gm personnage @me 1", "tp @me ~ ~ ~ -45 20")));
        STEPS.add(new Step(20, () -> onServer(client, (server, p) -> {
            fr.serveur.rpg.sombre.SombreState.get(server).nextEclatAt = 0;
            log(client, "lumière : phase " + fr.serveur.rpg.sombre.Lumiere.phase(server) + ", heure "
                    + Math.floorMod(server.getOverworld().getTimeOfDay(), 24000L) + ", nuit ? " + fr.serveur.rpg.world.DayCycle.isNight(server.getOverworld()));
        })));
        STEPS.add(new Step(20, () -> client.getToastManager().clear()));
        STEPS.add(new Step(10, () -> open(client, RpgScreen.Tab.PERSO, null)));
        STEPS.add(new Step(40, () -> shot(client, "60-personnage")));
        // Éclat de Lumière : annonce, préparation, jour.
        STEPS.add(new Step(5, () -> client.setScreen(null)));
        STEPS.add(new Step(5, () -> act2("eclat.use")));
        STEPS.add(new Step(15, () -> onServer(client, (server, p) -> log(client, "éclat utilisé : phase " + fr.serveur.rpg.sombre.Lumiere.phase(server)
                + ", jour dans " + fr.serveur.rpg.sombre.Lumiere.secondsLeft(server) + " s, éclats restants " + count(p, fr.serveur.rpg.sombre.SombreItems.ECLAT_LUMIERE)))));
        STEPS.add(new Step(5, () -> shot(client, "61-eclat-annonce")));
        STEPS.add(new Step(5, () -> onServer(client, (server, p) -> {
            fr.serveur.rpg.sombre.SombreState.get(server).prepUntil = System.currentTimeMillis() + 1000; // préparation abrégée pour le test
        })));
        STEPS.add(new Step(60, () -> onServer(client, (server, p) -> log(client, "jour : phase " + fr.serveur.rpg.sombre.Lumiere.phase(server)
                + ", heure " + Math.floorMod(server.getOverworld().getTimeOfDay(), 24000L) + ", nuit ? " + fr.serveur.rpg.world.DayCycle.isNight(server.getOverworld())
                + ", reste " + fr.serveur.rpg.sombre.Lumiere.secondsLeft(server) + " s ; 2e éclat : " + fr.serveur.rpg.sombre.Lumiere.use(p)))));
        STEPS.add(new Step(40, () -> shot(client, "62-jour-invoque")));
        STEPS.add(new Step(5, () -> onServer(client, (server, p) -> {
            fr.serveur.rpg.sombre.SombreState.get(server).dayUntil = System.currentTimeMillis() + 500; // jour abrégé pour le test
        })));
        STEPS.add(new Step(40, () -> onServer(client, (server, p) -> log(client, "fin du jour : phase " + fr.serveur.rpg.sombre.Lumiere.phase(server)
                + ", heure " + Math.floorMod(server.getOverworld().getTimeOfDay(), 24000L) + ", prochain éclat dans "
                + fr.serveur.rpg.sombre.Lumiere.secondsToNextEclat(server) + " s ; éclat refusé : " + fr.serveur.rpg.sombre.Lumiere.use(p)))));
        // Obscurcie : paliers 5 (Éclat 50 %) et 10 (détecteur).
        STEPS.add(new Step(10, () -> onServer(client, (server, p) -> {
            var d = fr.serveur.rpg.data.RpgState.get(server).player(p);
            int e0 = count(p, fr.serveur.rpg.sombre.SombreItems.ECLAT_LUMIERE), det0 = count(p, fr.serveur.rpg.sombre.SombreItems.DETECTEUR);
            d.obsLevel = 4;
            d.obsXp = fr.serveur.rpg.sombre.Obscurcie.xpNeeded(4) - 1;
            fr.serveur.rpg.sombre.Obscurcie.addXp(p, 5, true);
            int lvl5 = d.obsLevel, e1 = count(p, fr.serveur.rpg.sombre.SombreItems.ECLAT_LUMIERE);
            d.obsLevel = 9;
            d.obsXp = fr.serveur.rpg.sombre.Obscurcie.xpNeeded(9) - 1;
            fr.serveur.rpg.sombre.Obscurcie.addXp(p, 5, true);
            log(client, "obscurcie : niv. " + lvl5 + " (éclats " + e0 + " → " + e1 + "), puis niv. " + d.obsLevel + " (détecteurs " + det0 + " → "
                    + count(p, fr.serveur.rpg.sombre.SombreItems.DETECTEUR) + ", éclats " + count(p, fr.serveur.rpg.sombre.SombreItems.ECLAT_LUMIERE) + ")");
        })));
        // Niveau du personnage, perte à la mort, XP de combat.
        STEPS.add(new Step(10, () -> onServer(client, (server, p) -> {
            var st = fr.serveur.rpg.data.RpgState.get(server);
            var d = st.player(p);
            long m0 = d.money;
            int l0 = d.baseLevel;
            fr.serveur.rpg.sombre.Personnage.addXp(p, 260, "test");
            log(client, "personnage : niv. " + l0 + " → " + d.baseLevel + " (" + d.baseXp + " XP), pièces " + m0 + " → " + d.money);
            d.baseXp = 100;
            fr.serveur.rpg.sombre.Personnage.onDeath(p, d);
            log(client, "mort : XP 100 → " + d.baseXp + ", niveau " + d.baseLevel);
            int obs0 = d.obsXp, base0 = d.baseXp;
            var z = net.minecraft.entity.EntityType.ZOMBIE.create(p.getServerWorld());
            z.refreshPositionAndAngles(p.getX() + 2, p.getY(), p.getZ(), 0, 0);
            p.getServerWorld().spawnEntity(z);
            z.damage(p.getDamageSources().playerAttack(p), 1000f);
            log(client, "zombie tué de nuit : obscurcie " + obs0 + " → " + d.obsXp + " XP, personnage " + base0 + " → " + d.baseXp + " XP");
        })));
        // Coffre caché classique piégé : ouverture par un vrai clic.
        STEPS.add(new Step(10, () -> chestAt(client, chest, 2, 0, fr.serveur.rpg.sombre.CoffresCaches.Type.CLASSIQUE, true)));
        STEPS.add(new Step(10, () -> onServer(client, (server, p) -> {
            before[0] = fr.serveur.rpg.data.RpgState.get(server).player(p).money;
            before[1] = items(p);
        })));
        STEPS.add(new Step(10, () -> click(client, chest)));
        STEPS.add(new Step(40, () -> onServer(client, (server, p) -> {
            var w = p.getServerWorld();
            int mobs = w.getEntitiesByClass(net.minecraft.entity.mob.MobEntity.class, p.getBoundingBox().expand(12),
                    m -> m.getCommandTags().contains("survierpg_piege")).size();
            log(client, "coffre ouvert : bloc " + w.getBlockState(new net.minecraft.util.math.BlockPos(chest[0], chest[1], chest[2])).getBlock()
                    + ", objets " + before[1] + " → " + items(p) + ", pièces " + before[0] + " → " + fr.serveur.rpg.data.RpgState.get(server).player(p).money
                    + ", monstres du piège " + mobs + ", coffres restants " + fr.serveur.rpg.sombre.SombreState.get(server).coffres.size());
        })));
        STEPS.add(new Step(5, () -> shot(client, "63-coffre-ouvert")));
        STEPS.add(new Step(5, () -> server(client, "kill @e[tag=survierpg_piege]")));
        // Coffre de boss : gardien, puis butin.
        STEPS.add(new Step(10, () -> chestAt(client, boss, 0, 3, fr.serveur.rpg.sombre.CoffresCaches.Type.BOSS, false)));
        STEPS.add(new Step(10, () -> click(client, boss)));
        STEPS.add(new Step(30, () -> onServer(client, (server, p) -> {
            for (var m : p.getServerWorld().getEntitiesByClass(net.minecraft.entity.mob.MobEntity.class, p.getBoundingBox().expand(10),
                    x -> x.hasCustomName() && x.getCustomName().getString().contains("Gardien"))) {
                log(client, "gardien du coffre : " + Math.round(m.getMaxHealth()) + " PV");
            }
        })));
        STEPS.add(new Step(5, () -> shot(client, "64-gardien-coffre")));
        STEPS.add(new Step(5, () -> onServer(client, (server, p) -> {
            for (var m : p.getServerWorld().getEntitiesByClass(net.minecraft.entity.mob.MobEntity.class, p.getBoundingBox().expand(10),
                    x -> x.hasCustomName() && x.getCustomName().getString().contains("Gardien"))) {
                m.damage(p.getDamageSources().playerAttack(p), 100000f);
            }
            before[1] = items(p);
        })));
        STEPS.add(new Step(20, () -> click(client, boss)));
        STEPS.add(new Step(30, () -> onServer(client, (server, p) -> log(client, "coffre de boss après le gardien : objets " + before[1] + " → " + items(p)
                + ", coffres restants " + fr.serveur.rpg.sombre.SombreState.get(server).coffres.size()))));
        STEPS.add(new Step(5, () -> server(client, "kill @e[tag=survierpg_piege]")));
        // Exotique : don GM, duplicatas supprimés.
        STEPS.add(new Step(10, () -> server(client, "gm exotique donner lame_crepuscule @me")));
        STEPS.add(new Step(10, () -> onServer(client, (server, p) -> {
            var e = fr.serveur.rpg.sombre.SombreState.get(server).exotiques.get("lame_crepuscule");
            serial[0] = e.serial;
            log(client, "exotique donné : " + exotics(p) + " dans l'inventaire, statut " + e.statut + ", porteur " + e.porteur);
            for (int i = 0; i < p.getInventory().size(); i++) {
                var s = p.getInventory().getStack(i);
                if (!fr.serveur.rpg.sombre.Rarete.isExotic(s)) continue;
                p.getInventory().setStack(20, s.copy());
                var fake = s.copy();
                fake.getNbt().getCompound(fr.serveur.rpg.sombre.Rarete.EXO_NBT).putString("serial", "falsifie");
                p.getInventory().setStack(21, fake);
                break;
            }
            log(client, "duplicatas ajoutés : " + exotics(p) + " exotiques dans l'inventaire");
        })));
        STEPS.add(new Step(60, () -> onServer(client, (server, p) -> log(client, "après contrôle : " + exotics(p) + " exotique(s) dans l'inventaire"))));
        STEPS.add(new Step(5, () -> client.setScreen(new net.minecraft.client.gui.screen.ingame.InventoryScreen(client.player))));
        STEPS.add(new Step(30, () -> shot(client, "65-inventaire-raretes")));
        STEPS.add(new Step(5, () -> client.setScreen(null)));
        STEPS.add(new Step(30, () -> shot(client, "66-barre-raretes")));
        // Exotique perdu (détruit au sol), réintroduction, coffre exotique.
        STEPS.add(new Step(5, () -> onServer(client, (server, p) -> {
            for (int i = 0; i < p.getInventory().size(); i++) {
                var s = p.getInventory().getStack(i);
                if (!fr.serveur.rpg.sombre.Rarete.isExotic(s)) continue;
                var item = new net.minecraft.entity.ItemEntity(p.getServerWorld(), p.getX() + 1, p.getY() + 1, p.getZ(), s.copy());
                item.setPickupDelay(200);
                p.getServerWorld().spawnEntity(item);
                p.getInventory().setStack(i, net.minecraft.item.ItemStack.EMPTY);
                break;
            }
        })));
        STEPS.add(new Step(30, () -> shot(client, "67-exotique-au-sol")));
        STEPS.add(new Step(5, () -> onServer(client, (server, p) -> {
            for (var item : p.getServerWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class, p.getBoundingBox().expand(5),
                    i -> fr.serveur.rpg.sombre.Rarete.isExotic(i.getStack()))) item.kill();
            var e = fr.serveur.rpg.sombre.SombreState.get(server).exotiques.get("lame_crepuscule");
            log(client, "exotique détruit : statut " + e.statut + ", disponible tout de suite ? " + fr.serveur.rpg.sombre.Exotiques.available(server));
        })));
        STEPS.add(new Step(5, () -> server(client, "gm exotique reintroduire lame_crepuscule")));
        STEPS.add(new Step(5, () -> onServer(client, (server, p) -> {
            // Seule la lame est disponible pour le test : les autres passent « en jeu ».
            var s = fr.serveur.rpg.sombre.SombreState.get(server);
            for (var d : fr.serveur.rpg.sombre.SombreConfig.objets().exotiques) {
                if (!d.id.equals("lame_crepuscule") && s.exo(d.id).statut == fr.serveur.rpg.sombre.SombreState.Statut.ABSENT) {
                    s.exo(d.id).statut = fr.serveur.rpg.sombre.SombreState.Statut.EN_JEU;
                    s.exo(d.id).serial = "test";
                }
            }
            log(client, "après réintroduction : disponible " + fr.serveur.rpg.sombre.Exotiques.available(server));
        })));
        STEPS.add(new Step(5, () -> chestAt(client, exo, -2, 0, fr.serveur.rpg.sombre.CoffresCaches.Type.EXOTIQUE, false)));
        STEPS.add(new Step(10, () -> click(client, exo)));
        STEPS.add(new Step(30, () -> onServer(client, (server, p) -> {
            var e = fr.serveur.rpg.sombre.SombreState.get(server).exotiques.get("lame_crepuscule");
            log(client, "coffre exotique : " + exotics(p) + " exotique(s), statut " + e.statut + ", nouveau numéro ? " + !e.serial.equals(serial[0]));
        })));
        STEPS.add(new Step(5, () -> server(client, "kill @e[tag=survierpg_piege]")));
        // Boutique de découverte.
        STEPS.add(new Step(5, () -> onServer(client, (server, p) -> {
            var st = fr.serveur.rpg.data.RpgState.get(server);
            var m = fr.serveur.rpg.economy.DynamicShop.create(st, new net.minecraft.item.ItemStack(net.minecraft.item.Items.GOLDEN_APPLE), 30, true, false);
            st.shop.add(0, m);
            shopId[0] = m.id;
            var d = st.player(p);
            d.discovered.remove("minecraft:golden_apple");
            d.money = Math.max(d.money, 500);
            log(client, "boutique : pomme dorée inconnue → " + fr.serveur.rpg.sombre.Decouvertes.canBuy(d, m)
                    + " ; achat : " + fr.serveur.rpg.gui.MarketMenus.buyFromShop(p, m.id).message());
        })));
        STEPS.add(new Step(10, () -> open(client, RpgScreen.Tab.BOUTIQUE, null)));
        STEPS.add(new Step(30, () -> shot(client, "68-boutique-inconnu")));
        STEPS.add(new Step(5, () -> server(client, "give @me minecraft:golden_apple")));
        STEPS.add(new Step(60, () -> onServer(client, (server, p) -> {
            var st = fr.serveur.rpg.data.RpgState.get(server);
            var m = st.shop.stream().filter(x -> x.id == shopId[0]).findFirst().orElseThrow();
            log(client, "après ramassage : " + fr.serveur.rpg.sombre.Decouvertes.canBuy(st.player(p), m)
                    + " ; achat : " + fr.serveur.rpg.gui.MarketMenus.buyFromShop(p, m.id).message());
        })));
        STEPS.add(new Step(10, () -> open(client, RpgScreen.Tab.BOUTIQUE, null)));
        STEPS.add(new Step(30, () -> shot(client, "69-boutique-decouvert")));
        // Lore personnel.
        STEPS.add(new Step(5, () -> act2("lore.save", "text", "Né sous la dernière aube, j'ai juré de rallumer le soleil. Mes mains portent encore la cendre du village.", "public", "1")));
        STEPS.add(new Step(20, () -> onServer(client, (server, p) -> {
            var d = fr.serveur.rpg.data.RpgState.get(server).player(p);
            log(client, "lore : public " + d.lorePublic + ", " + d.lore.length() + " caractères");
        })));
        STEPS.add(new Step(10, () -> open(client, RpgScreen.Tab.PERSO, null)));
        STEPS.add(new Step(40, () -> shot(client, "70-personnage-lore")));
        // Coffres posés tout seuls autour du joueur, puis Détecteur d'ombre.
        STEPS.add(new Step(5, () -> client.setScreen(null)));
        STEPS.add(new Step(5, () -> server(client, "gm coffres generer 8", "gm coffres info")));
        STEPS.add(new Step(20, () -> onServer(client, (server, p) -> {
            var s = fr.serveur.rpg.sombre.SombreState.get(server);
            StringBuilder sb = new StringBuilder();
            for (var c : s.coffres) sb.append(c.type).append(c.piege ? "*" : "").append("@").append(c.pos.toShortString())
                    .append(" (").append(Math.round(Math.sqrt(c.pos.getSquaredDistance(p.getPos())))).append(") ");
            log(client, "coffres générés : " + s.coffres.size() + " — " + sb);
        })));
        STEPS.add(new Step(5, () -> {
            client.player.getInventory().selectedSlot = 1;
            client.interactionManager.interactItem(client.player, net.minecraft.util.Hand.MAIN_HAND);
        }));
        STEPS.add(new Step(30, () -> onServer(client, (server, p) -> log(client, "détecteur : actif ? "
                + (p.getMainHandStack().getNbt() != null && p.getMainHandStack().getNbt().getLong("survierpg_fin") > 0) + ", tenu : " + p.getMainHandStack().getItem()))));
        STEPS.add(new Step(10, () -> shot(client, "71-detecteur")));
        STEPS.add(new Step(100, () -> shot(client, "72-nuit-permanente")));
        STEPS.add(new Step(5, () -> onServer(client, (server, p) -> {
            try (var files = java.nio.file.Files.list(fr.serveur.rpg.sombre.Journal.dir(server))) {
                files.forEach(f -> {
                    try {
                        log(client, "journal " + f.getFileName() + " : " + java.nio.file.Files.readAllLines(f).size() + " lignes");
                    } catch (java.io.IOException ignored) { }
                });
            } catch (java.io.IOException e) {
                log(client, "journaux illisibles : " + e);
            }
        })));
        STEPS.add(new Step(20, () -> client.scheduleStop()));
    }
}
