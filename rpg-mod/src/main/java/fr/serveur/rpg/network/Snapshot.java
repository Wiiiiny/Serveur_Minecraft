package fr.serveur.rpg.network;

import fr.serveur.rpg.auto.AutoConfig;
import fr.serveur.rpg.auto.DailyQuests;
import fr.serveur.rpg.auto.Forge;
import fr.serveur.rpg.data.DailyQuest;
import fr.serveur.rpg.data.MarketItem;
import fr.serveur.rpg.data.PlayerData;
import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.data.Zone;
import fr.serveur.rpg.death.DeathSystem;
import fr.serveur.rpg.economy.Bank;
import fr.serveur.rpg.events.GmEvents;
import fr.serveur.rpg.items.ModItems;
import fr.serveur.rpg.jobs.Job;
import fr.serveur.rpg.jobs.JobConfig;
import fr.serveur.rpg.jobs.SideJob;
import fr.serveur.rpg.jobs.SideJobConfig;
import fr.serveur.rpg.jobs.SideJobs;
import fr.serveur.rpg.world.DayCycle;
import fr.serveur.rpg.world.PlayableZone;
import fr.serveur.rpg.world.TravelBeacon;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** Construit l'état affiché par l'interface (touche B) pour un joueur. */
public final class Snapshot {
    private Snapshot() {}

    public static NbtCompound build(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        RpgState state = RpgState.get(server);
        PlayerData data = state.player(player);
        List<DailyQuest> daily = DailyQuests.ensure(player);
        NbtCompound root = new NbtCompound();

        // ---- profil
        NbtCompound me = new NbtCompound();
        me.putString("name", data.name);
        me.putString("job", data.job == null ? "" : data.job.name());
        PlayerData.JobProgress jp = data.current();
        me.putInt("level", jp == null ? 0 : jp.level);
        me.putInt("xp", jp == null ? 0 : jp.xp);
        me.putInt("xpNeeded", jp == null ? 0 : JobConfig.xpNeeded(jp.level));
        me.putInt("prestige", jp == null ? 0 : jp.prestige);
        me.putLong("money", data.money);
        me.putInt("coinsOnHand", Bank.countCurrency(player));
        me.putInt("deaths", data.deaths);
        me.putInt("pvpKills", data.pvpKills);
        me.putInt("mobKills", data.mobKills);
        me.putBoolean("awaitingRevive", data.awaitingRevive);
        int tears = 0;
        for (ItemStack s : player.getInventory().main) if (DeathSystem.isTear(s)) tears += s.getCount();
        me.putInt("tears", tears);
        me.putBoolean("gm", player.hasPermissionLevel(2));
        // économie : compte gelé, plafonds du jour
        me.putBoolean("frozen", data.frozen);
        me.putString("capMetier", fr.serveur.rpg.economy.Economy.capInfo(data, fr.serveur.rpg.economy.Economy.Source.METIER));
        me.putString("capRachat", fr.serveur.rpg.economy.Economy.capInfo(data, fr.serveur.rpg.economy.Economy.Source.RACHAT));
        me.putLong("transferLeft", fr.serveur.rpg.economy.EconomyConfig.get().virementsIllimites ? -1
                : Math.max(0, fr.serveur.rpg.economy.EconomyConfig.get().virementPlafondJour - data.transferredToday));
        me.putLong("conversionMax", fr.serveur.rpg.economy.EconomyConfig.get().conversionMaxParTransaction);
        // métier supplémentaire
        PlayerData.JobProgress sp = data.side();
        me.putString("side", data.sideJob == null ? "" : data.sideJob.name());
        me.putInt("sideLevel", sp == null ? 0 : sp.level);
        me.putInt("sideXp", sp == null ? 0 : sp.xp);
        me.putInt("sideXpNeeded", sp == null ? 0 : JobConfig.xpNeeded(sp.level));
        me.putBoolean("sideFree", !data.sideChosenOnce);
        if (data.sideJob == SideJob.VOYAGEUR) me.putLong("beaconLeft", TravelBeacon.secondsLeft(data));
        // monde sombre : personnage, Obscurcie, lore, découvertes
        fr.serveur.rpg.sombre.SombreConfig.Data sc = fr.serveur.rpg.sombre.SombreConfig.get();
        me.putInt("baseLevel", data.baseLevel);
        me.putInt("baseXp", data.baseXp);
        me.putInt("baseXpNeeded", fr.serveur.rpg.sombre.Personnage.xpNeeded(data.baseLevel));
        me.putInt("baseMax", fr.serveur.rpg.sombre.Personnage.maxLevel());
        me.putDouble("deathLoss", sc.personnage.pertePourcentMort);
        me.putInt("obsLevel", data.obsLevel);
        me.putInt("obsXp", data.obsXp);
        me.putInt("obsXpNeeded", fr.serveur.rpg.sombre.Obscurcie.xpNeeded(data.obsLevel));
        me.putInt("obsNextEclat", fr.serveur.rpg.sombre.Obscurcie.nextMilestone(data.obsLevel, sc.obscurcie.palierEclat));
        me.putInt("obsNextDetector", fr.serveur.rpg.sombre.Obscurcie.nextMilestone(data.obsLevel, sc.obscurcie.palierDetecteur));
        me.putInt("eclatChance", (int) Math.round(sc.obscurcie.chanceEclat * 100));
        me.putString("lore", data.lore);
        me.putBoolean("lorePublic", data.lorePublic);
        int eclats = 0;
        for (ItemStack s : player.getInventory().main) if (s.isOf(fr.serveur.rpg.sombre.SombreItems.ECLAT_LUMIERE)) eclats += s.getCount();
        me.putInt("eclats", eclats);
        int[] disc = fr.serveur.rpg.sombre.Decouvertes.progress(state, data);
        me.putInt("discKnown", disc[0]);
        me.putInt("discTotal", disc[1]);
        me.putInt("chestsOpened", data.chestsOpened);
        root.put("me", me);
        root.putInt("sidePrice", SideJobConfig.data().prixChangement);
        root.putBoolean("border", PlayableZone.box(state) != null);
        NbtCompound sideRules = new NbtCompound();
        for (SideJob j : SideJob.values()) {
            NbtList lines = new NbtList();
            for (net.minecraft.text.Text t : SideJobs.describe(j)) lines.add(NbtString.of(net.minecraft.text.Text.Serializer.toJson(t)));
            sideRules.put(j.name(), lines);
        }
        root.put("sideRules", sideRules);
        root.put("raid", raid(player, data));
        root.put("fac", fr.serveur.rpg.faction.FactionSnapshot.build(player));
        root.putInt("jobPrice", AutoConfig.get().prixChangementMetier);
        root.putInt("maxLevel", Job.MAX_LEVEL);
        root.putInt("prestigeBonus", state.prestigeBonusPercent);

        // ---- quêtes
        NbtList dq = new NbtList();
        daily.forEach(q -> {
            NbtCompound c = q.toNbt();
            c.put("icon", DailyQuests.iconFor(q).writeNbt(new NbtCompound()));
            dq.add(c);
        });
        root.put("daily", dq);
        LocalDateTime now = LocalDateTime.now();
        root.putLong("dailyResetIn", Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).getSeconds());
        NbtList quests = new NbtList();
        for (PlayerData.Quest q : data.quests) {
            NbtCompound c = new NbtCompound();
            c.putInt("id", q.id);
            c.putString("text", q.text);
            c.putBoolean("done", q.done);
            quests.add(c);
        }
        root.put("quests", quests);

        // ---- forge
        NbtList forge = new NbtList();
        if (data.job != null) {
            for (ModItems.Tier tier : ModItems.Tier.values()) {
                AutoConfig.ForgeTier t = Forge.tier(tier);
                NbtCompound c = new NbtCompound();
                c.putString("tier", tier.name());
                c.put("item", new ItemStack(ModItems.OFFICIAL.get(data.job).get(tier)).writeNbt(new NbtCompound()));
                c.putInt("level", t.niveau);
                c.putInt("coins", t.pieces);
                boolean ok = jp.level >= t.niveau && data.money >= t.pieces;
                NbtList mats = new NbtList();
                for (Forge.Requirement r : Forge.requirements(data.job, tier)) {
                    NbtCompound m = new NbtCompound();
                    m.put("icon", r.icon().writeNbt(new NbtCompound()));
                    m.putInt("need", r.count());
                    int have = Forge.count(player, r.id());
                    m.putInt("have", have);
                    ok &= have >= r.count();
                    mats.add(m);
                }
                c.put("mats", mats);
                c.putBoolean("ok", ok);
                forge.add(c);
            }
        }
        root.put("forge", forge);

        // ---- marché et boutique
        root.put("auctions", market(state.auctions, player.getUuid()));
        NbtList shop = market(state.shop, player.getUuid());
        for (int i = 0; i < shop.size(); i++) {
            MarketItem m = state.shop.get(i);
            NbtCompound c = shop.getCompound(i);
            c.putLong("price", fr.serveur.rpg.economy.DynamicShop.buyPrice(m));
            c.putLong("sellPrice", fr.serveur.rpg.economy.DynamicShop.sellPrice(m));
            c.putLong("base", m.basePrice);
            c.putInt("stock", (int) Math.round(100 * m.stock / Math.max(1, m.stockTarget)));
            c.putBoolean("sold", m.sold);
            c.putBoolean("buyback", m.buyback);
            // Boutique de découverte : un objet jamais ramassé reste masqué (on n'envoie même pas ce que c'est).
            boolean known = !m.sold || fr.serveur.rpg.sombre.Decouvertes.canBuy(data, m) == null;
            c.putBoolean("known", known);
            if (!known) c.remove("stack");
        }
        root.put("shop", shop);
        root.putDouble("hdvTax", fr.serveur.rpg.economy.EconomyConfig.get().taxeHdv * state.econTaxMult);

        // ---- classements
        NbtCompound rank = new NbtCompound();
        rank.put("metiers", ranking(state, Comparator.comparingInt((PlayerData p) -> p.current() == null ? -1 : p.current().prestige)
                        .thenComparingInt(p -> p.current() == null ? 0 : p.current().level)
                        .thenComparingInt(p -> p.current() == null ? 0 : p.current().xp),
                p -> p.current() == null ? "sans métier" : p.job.displayName + " niv. " + p.current().level
                        + (p.current().prestige > 0 ? "  ★" + p.current().prestige : "")));
        rank.put("richesse", ranking(state, Comparator.comparingLong((PlayerData p) -> p.money), p -> Bank.format(p.money)));
        rank.put("combats", ranking(state, Comparator.comparingInt((PlayerData p) -> p.pvpKills).thenComparingInt(p -> p.mobKills),
                p -> p.pvpKills + " joueurs · " + p.mobKills + " créatures"));
        rank.put("morts", ranking(state, Comparator.comparingInt((PlayerData p) -> p.deaths), p -> p.deaths + " morts"));
        root.put("rank", rank);

        // ---- monde
        NbtCompound world = new NbtCompound();
        world.putBoolean("night", DayCycle.isNight(server.getOverworld()));
        NbtCompound lum = new NbtCompound();
        lum.putString("phase", fr.serveur.rpg.sombre.Lumiere.phase(server).name());
        lum.putLong("left", fr.serveur.rpg.sombre.Lumiere.secondsLeft(server));
        lum.putLong("next", fr.serveur.rpg.sombre.Lumiere.secondsToNextEclat(server));
        lum.putString("by", fr.serveur.rpg.sombre.SombreState.get(server).eclatBy);
        world.put("lumiere", lum);
        if (GmEvents.running()) {
            world.putString("eventTitle", GmEvents.currentTitle());
            world.putString("eventType", GmEvents.currentType());
            world.putLong("eventLeft", GmEvents.secondsLeft());
        }
        world.putLong("nextEvent", GmEvents.secondsToNextAuto());
        root.put("world", world);

        if (player.hasPermissionLevel(2)) root.put("gm", gm(server, state));
        return root;
    }

    private static NbtCompound raid(ServerPlayerEntity p, PlayerData data) {
        var server = p.getServer();
        var s = fr.serveur.rpg.raid.RaidState.get(server);
        NbtCompound r = new NbtCompound();
        r.putString("status", s.status.name());
        r.putString("threat", s.threat().nom);
        r.putInt("statues", s.threat().statues);
        r.putLong("fee", fr.serveur.rpg.raid.RaidCycle.entryFee(server));
        r.putLong("left", Math.max(0, (s.nextReset - System.currentTimeMillis()) / 1000));
        r.putInt("progress", fr.serveur.rpg.raid.RaidBuilder.progress());
        r.putBoolean("inRaid", fr.serveur.rpg.raid.RaidWorld.isIn(p));
        r.putBoolean("portalSet", s.portal != null);
        r.putBoolean("nearPortal", s.portal != null && s.hubDim != null
                && p.getWorld().getRegistryKey().getValue().toString().equals(s.hubDim) && p.getBlockPos().getSquaredDistance(s.portal) <= 36);
        r.putInt("keys", s.keysInserted);
        r.putInt("keysNeeded", fr.serveur.rpg.raid.RaidConfig.get().clesRequises);
        r.putInt("affinity", data.thiefAffinity);
        r.putBoolean("companionOff", data.companionDisabled);
        r.putLong("companionCooldown", Math.max(0, (data.companionCooldownUntil - System.currentTimeMillis()) / 1000));
        NbtList party = new NbtList();
        for (UUID u : fr.serveur.rpg.raid.Party.members(p.getUuid())) {
            PlayerData m = RpgState.get(server).players.get(u);
            party.add(net.minecraft.nbt.NbtString.of(m == null ? "?" : m.name));
        }
        r.put("party", party);
        var held = p.getMainHandStack();
        if (held.isOf(fr.serveur.rpg.items.RaidItems.BRISE_STATUE)) {
            r.putInt("breakerDamage", held.getDamage());
            r.putLong("repairPrice", (long) held.getDamage() * fr.serveur.rpg.raid.RaidConfig.get().reparationBriseStatue);
        }
        r.putInt("rooms", s.rooms.size());
        return r;
    }

    private static NbtList market(List<MarketItem> items, UUID viewer) {
        NbtList list = new NbtList();
        for (MarketItem m : items) {
            NbtCompound c = new NbtCompound();
            c.putInt("id", m.id);
            c.put("stack", m.stack.writeNbt(new NbtCompound()));
            c.putLong("price", m.price);
            c.putString("seller", m.sellerName);
            c.putBoolean("own", viewer.equals(m.seller));
            list.add(c);
        }
        return list;
    }

    private static NbtList ranking(RpgState state, Comparator<PlayerData> order, Function<PlayerData, String> label) {
        NbtList list = new NbtList();
        state.players.values().stream().sorted(order.reversed()).limit(10).forEach(p -> {
            NbtCompound c = new NbtCompound();
            c.putString("name", p.name);
            c.putString("value", label.apply(p));
            list.add(c);
        });
        return list;
    }

    private static NbtCompound gm(MinecraftServer server, RpgState state) {
        NbtCompound gm = new NbtCompound();
        List<Map.Entry<UUID, PlayerData>> players = new ArrayList<>(state.players.entrySet());
        players.sort(Comparator.comparing((Map.Entry<UUID, PlayerData> e) -> server.getPlayerManager().getPlayer(e.getKey()) == null)
                .thenComparing(e -> e.getValue().name.toLowerCase()));
        NbtList list = new NbtList();
        long totalMoney = 0;
        for (Map.Entry<UUID, PlayerData> e : players) {
            PlayerData p = e.getValue();
            totalMoney += p.money;
            NbtCompound c = new NbtCompound();
            c.putString("name", p.name);
            c.putBoolean("online", server.getPlayerManager().getPlayer(e.getKey()) != null);
            c.putString("job", p.job == null ? "" : p.job.name());
            c.putInt("level", p.current() == null ? 0 : p.current().level);
            c.putInt("prestige", p.current() == null ? 0 : p.current().prestige);
            c.putString("side", p.sideJob == null ? "" : p.sideJob.name());
            c.putInt("sideLevel", p.side() == null ? 0 : p.side().level);
            c.putLong("money", p.money);
            c.putInt("deaths", p.deaths);
            c.putBoolean("awaiting", p.awaitingRevive);
            c.putBoolean("frozen", p.frozen);
            list.add(c);
        }
        gm.put("players", list);

        AutoConfig.Data cfg = AutoConfig.get();
        NbtCompound set = new NbtCompound();
        set.putDouble("dayMin", state.dayMinutes);
        set.putDouble("nightMin", state.nightMinutes);
        set.putBoolean("permanentNight", fr.serveur.rpg.sombre.SombreState.get(server).nuitPermanente);
        set.putInt("lockpick", state.lockpickChance);
        set.putInt("prestigeBonus", state.prestigeBonusPercent);
        set.putInt("jobPrice", cfg.prixChangementMetier);
        set.putInt("dailyCount", cfg.quetesParJour);
        set.putDouble("rewardMult", cfg.multiplicateurRecompenses);
        set.putBoolean("autoEvents", cfg.evenementsAuto);
        set.putInt("eventInterval", cfg.evenementIntervalleMinutes);
        set.putInt("eventDuration", cfg.evenementDureeMinutes);
        set.putInt("eventMinPlayers", cfg.evenementJoueursMin);
        set.putString("eventRewards", cfg.evenementRecompenses.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(" ")));
        set.putBoolean("nether", state.netherOpen);
        set.putBoolean("end", state.endOpen);
        set.putBoolean("waitingRoom", state.waitingRoom != null);
        PlayableZone.Box box = PlayableZone.box(state);
        set.putString("border", box != null ? box.describe()
                : state.borderCorner1 != null ? "coin 1 seul : X " + state.borderCorner1.getX() + ", Z " + state.borderCorner1.getZ()
                : state.borderCorner2 != null ? "coin 2 seul : X " + state.borderCorner2.getX() + ", Z " + state.borderCorner2.getZ() : "");
        gm.put("settings", set);

        NbtList zones = new NbtList();
        for (Zone z : state.zones.values()) {
            NbtCompound c = new NbtCompound();
            c.putString("name", z.name);
            c.putString("desc", z.describe());
            zones.add(c);
        }
        gm.put("zones", zones);

        NbtCompound stats = new NbtCompound();
        stats.putInt("online", server.getCurrentPlayerCount());
        stats.putInt("known", state.players.size());
        stats.putLong("totalMoney", totalMoney);
        stats.putInt("auctions", state.auctions.size());
        int chests = 0;
        for (var m : state.chestOwners.values()) chests += m.size();
        stats.putInt("chests", chests);
        gm.put("stats", stats);

        // économie automatisée : masse, régulateur, flux de la dernière mesure, alertes
        NbtCompound eco = new NbtCompound();
        var snap = fr.serveur.rpg.economy.EconomyTicker.snapshot(state);
        eco.putLong("supply", snap.supply());
        eco.putInt("active", snap.active());
        eco.putLong("median", snap.median());
        eco.putLong("perActive", snap.active() == 0 ? 0 : snap.supply() / snap.active());
        eco.putLong("target", fr.serveur.rpg.economy.EconomyConfig.get().masseCibleParJoueurActif);
        eco.putDouble("gainMult", state.econGainMult);
        eco.putDouble("taxMult", state.econTaxMult);
        eco.putInt("frozen", (int) state.players.values().stream().filter(d -> d.frozen).count());
        if (!state.econMetrics.isEmpty()) eco.put("last", state.econMetrics.getCompound(state.econMetrics.size() - 1).copy());
        NbtList alerts = new NbtList();
        state.econAlerts.subList(Math.max(0, state.econAlerts.size() - 3), state.econAlerts.size())
                .forEach(a -> alerts.add(NbtString.of(a)));
        eco.put("alerts", alerts);
        gm.put("eco", eco);
        return gm;
    }
}
