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
import fr.serveur.rpg.world.DayCycle;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
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
        root.put("me", me);
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
        root.put("shop", market(state.shop, player.getUuid()));

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
            c.putLong("money", p.money);
            c.putInt("deaths", p.deaths);
            c.putBoolean("awaiting", p.awaitingRevive);
            list.add(c);
        }
        gm.put("players", list);

        AutoConfig.Data cfg = AutoConfig.get();
        NbtCompound set = new NbtCompound();
        set.putDouble("dayMin", state.dayMinutes);
        set.putDouble("nightMin", state.nightMinutes);
        set.putInt("lockpick", state.lockpickChance);
        set.putInt("prestigeBonus", state.prestigeBonusPercent);
        set.putInt("jobPrice", cfg.prixChangementMetier);
        set.putInt("dailyCount", cfg.quetesParJour);
        set.putDouble("rewardMult", cfg.multiplicateurRecompenses);
        set.putBoolean("autoEvents", cfg.evenementsAuto);
        set.putInt("eventInterval", cfg.evenementIntervalleMinutes);
        set.putInt("eventDuration", cfg.evenementDureeMinutes);
        set.putInt("eventMinPlayers", cfg.evenementJoueursMin);
        set.putBoolean("nether", state.netherOpen);
        set.putBoolean("end", state.endOpen);
        set.putBoolean("waitingRoom", state.waitingRoom != null);
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
        return gm;
    }
}
