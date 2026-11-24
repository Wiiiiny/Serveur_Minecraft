package fr.serveur.rpg.faction;

import fr.serveur.rpg.data.PlayerData;
import fr.serveur.rpg.data.RpgState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.Set;

/** Ce que l'onglet Faction (menu B) affiche, construit pour un joueur. */
public final class FactionSnapshot {
    private FactionSnapshot() {}

    private static NbtCompound summary(MinecraftServer server, Faction f) {
        NbtCompound c = new NbtCompound();
        c.putInt("id", f.id);
        c.putString("name", f.name);
        c.putInt("level", f.level);
        c.putLong("totalXp", f.totalXp);
        c.putInt("members", f.members.size());
        c.putInt("active", Factions.activeMembers(server, f));
        c.putInt("territories", f.territories.size());
        c.putInt("warsWon", f.warsWon);
        c.putInt("warsLost", f.warsLost);
        c.putLong("bank", f.bank);
        c.putInt("points", f.points);
        c.putLong("power", Factions.power(server, f));
        c.putInt("base", f.bannerBase);
        c.put("banner", Banners.stack(f).writeNbt(new NbtCompound()));
        c.putLong("peaceLeft", Math.max(0, (f.peaceUntil - System.currentTimeMillis()) / 1000));
        c.putBoolean("atWar", FactionState.get(server).warOf(f.id) != null);
        return c;
    }

    public static NbtCompound build(ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        FactionState s = FactionState.get(server);
        Faction f = s.of(p.getUuid());
        NbtCompound root = new NbtCompound();

        NbtList all = new NbtList();
        for (Faction o : s.factions.values()) {
            NbtCompound c = summary(server, o);
            c.putBoolean("mine", f != null && o.id == f.id);
            all.add(c);
        }
        root.put("all", all);

        NbtList inv = new NbtList();
        for (int id : s.invites.getOrDefault(p.getUuid(), Set.of())) {
            Faction o = s.factions.get(id);
            if (o != null) inv.add(summary(server, o));
        }
        root.put("invites", inv);

        // Carte : territoires à moins de 2 000 blocs, dans le monde du joueur.
        String dim = p.getWorld().getRegistryKey().getValue().toString();
        NbtList map = new NbtList();
        for (Faction o : s.factions.values()) {
            for (Faction.Territory t : o.territories) {
                if (!t.dim.equals(dim) || Math.abs(t.center.getX() - p.getX()) > 2000 || Math.abs(t.center.getZ() - p.getZ()) > 2000) continue;
                NbtCompound c = new NbtCompound();
                c.putInt("x", t.center.getX());
                c.putInt("z", t.center.getZ());
                c.putInt("r", o.radius());
                c.putInt("fid", o.id);
                c.putString("name", o.name);
                c.putInt("level", o.level);
                c.putInt("color", o.bannerBase);
                c.putBoolean("base", t.id == o.baseTerritory);
                c.put("banner", Banners.stack(o).writeNbt(new NbtCompound()));
                map.add(c);
            }
        }
        root.put("map", map);
        root.putInt("px", p.getBlockX());
        root.putInt("pz", p.getBlockZ());
        root.putFloat("yaw", p.getYaw());
        Territories.Owner here = Territories.ownerAt(server, p.getWorld(), p.getBlockPos());
        if (here != null) {
            root.putString("hereName", here.faction().name);
            root.putInt("hereTerritory", here.territory().id);
            root.putInt("hereFaction", here.faction().id);
        }
        root.putInt("heartMax", FactionConfig.get().coeurPointsDeVie);
        root.putInt("distanceMin", FactionConfig.get().distanceMinEntreFactions);

        if (f == null) return root;
        long now = System.currentTimeMillis();
        NbtCompound m = summary(server, f);
        m.putLong("xp", f.xp);
        m.putLong("xpNeeded", Factions.xpNeeded(f.level));
        m.putInt("radius", f.radius());
        NbtList layers = new NbtList();
        for (Faction.Layer l : f.layers) {
            NbtCompound c = new NbtCompound();
            c.putString("p", l.pattern);
            c.putInt("c", l.color);
            layers.add(c);
        }
        m.put("layers", layers);
        Faction.Member me = f.member(p.getUuid());
        m.putString("myRank", me.rank.name());
        m.putBoolean("chief", me.rank == Faction.Rank.CHEF);
        m.putBoolean("leader", Factions.isLeader(f, p.getUuid()));
        m.putInt("heartHp", f.heartHp);
        if (f.heart != null) {
            m.putInt("heartX", f.heart.getX());
            m.putInt("heartZ", f.heart.getZ());
        }
        NbtList ts = new NbtList();
        for (Faction.Territory t : f.territories) {
            NbtCompound c = new NbtCompound();
            c.putInt("id", t.id);
            c.putInt("x", t.center.getX());
            c.putInt("z", t.center.getZ());
            c.putString("dim", t.dim.replace("minecraft:", ""));
            c.putBoolean("base", t.id == f.baseTerritory);
            ts.add(c);
        }
        m.put("terr", ts);

        NbtList ms = new NbtList();
        RpgState rs = RpgState.get(server);
        for (Faction.Member mb : f.members.values()) {
            NbtCompound c = new NbtCompound();
            c.putString("uuid", mb.id.toString());
            c.putString("name", mb.name);
            c.putString("rank", mb.rank.name());
            c.putBoolean("online", server.getPlayerManager().getPlayer(mb.id) != null);
            c.putLong("contributed", mb.contributed);
            PlayerData d = rs.players.get(mb.id);
            c.putLong("seenDays", d == null || d.lastSeen <= 0 ? -1 : (now - d.lastSeen) / 86_400_000L);
            ms.add(c);
        }
        m.put("memberList", ms);

        NbtList vs = new NbtList();
        for (Faction.Vote v : f.votes) {
            NbtCompound c = new NbtCompound();
            c.putInt("id", v.id);
            c.putString("label", Factions.describe(f, v));
            c.putInt("yes", v.yes.size());
            c.putInt("no", v.no.size());
            c.putInt("my", v.yes.contains(p.getUuid()) ? 1 : v.no.contains(p.getUuid()) ? -1 : 0);
            c.putLong("left", Math.max(0, (v.end - now) / 1000));
            vs.add(c);
        }
        m.put("votes", vs);

        NbtList hs = new NbtList();
        for (int i = f.history.size() - 1; i >= Math.max(0, f.history.size() - 14); i--) hs.add(NbtString.of(f.history.get(i)));
        m.put("history", hs);

        Factions.ensureMissions(f);
        NbtList mi = new NbtList();
        List<FactionConfig.Mission> defs = FactionConfig.get().missions;
        for (Faction.MissionProgress mp : f.missions) {
            if (mp.index >= defs.size()) continue;
            FactionConfig.Mission d = defs.get(mp.index);
            NbtCompound c = new NbtCompound();
            c.putString("text", d.texte);
            c.putInt("progress", mp.progress);
            c.putInt("total", d.nombre);
            c.putBoolean("done", mp.done);
            c.putInt("xp", d.xp);
            c.putLong("pieces", d.pieces);
            mi.add(c);
        }
        m.put("missions", mi);

        NbtList shop = new NbtList();
        List<FactionConfig.Article> arts = FactionConfig.get().boutique;
        for (int i = 0; i < arts.size(); i++) {
            FactionConfig.Article a = arts.get(i);
            var st = Factions.articleStack(a);
            if (st.isEmpty()) continue;
            NbtCompound c = new NbtCompound();
            c.putInt("index", i);
            c.put("stack", st.copyWithCount(Math.min(a.quantite, st.getMaxCount())).writeNbt(new NbtCompound()));
            c.putString("label", "extension".equals(a.objet) ? "Extension de territoire (+2 blocs)" : st.getName().getString() + (a.quantite > 1 ? " ×" + a.quantite : ""));
            c.putLong("price", Factions.price(f, a));
            c.putInt("level", a.niveau);
            shop.add(c);
        }
        m.put("shop", shop);

        FactionState.War w = s.warOf(f.id);
        if (w != null) {
            Faction o = s.factions.get(w.other(f.id));
            NbtCompound c = new NbtCompound();
            c.putInt("otherId", o == null ? 0 : o.id);
            c.putString("otherName", o == null ? "?" : o.name);
            if (o != null) c.put("otherBanner", Banners.stack(o).writeNbt(new NbtCompound()));
            c.putBoolean("attacker", w.attacker == f.id);
            c.putString("status", Wars.status(server, w));
            c.putBoolean("open", Wars.attackOpen(server, w));
            boolean mineNight = w.attacker == f.id ? w.nightA : w.nightD, theirNight = w.attacker == f.id ? w.nightD : w.nightA;
            c.putBoolean("nightMine", mineNight);
            c.putBoolean("nightTheirs", theirNight);
            c.putInt("offer", w.offer == 0 ? 0 : w.offer == f.id ? 1 : 2);
            c.putInt("heartOther", o == null ? 0 : o.heartHp);
            m.put("war", c);
        }
        NbtList wh = new NbtList();
        List<FactionState.War> past = s.wars.stream().filter(x -> x.over && x.involves(f.id)).toList();
        for (int i = past.size() - 1; i >= Math.max(0, past.size() - 5); i--) {
            FactionState.War x = past.get(i);
            Faction o = s.factions.get(x.other(f.id));
            NbtCompound c = new NbtCompound();
            c.putString("other", o == null ? "faction dissoute" : o.name);
            c.putInt("result", x.winner == 0 ? 0 : x.winner == f.id ? 1 : -1);
            c.putString("reason", x.reason);
            wh.add(c);
        }
        m.put("warHistory", wh);
        root.put("mine", m);
        return root;
    }
}
