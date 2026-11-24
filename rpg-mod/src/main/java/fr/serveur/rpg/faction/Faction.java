package fr.serveur.rpg.faction;

import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.DyeColor;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Une faction : identité, membres et rangs, territoires, trésor et coffre commun, votes, missions, historique. */
public final class Faction {
    public enum Rank {
        CHEF("Chef"), OFFICIER("Officier"), VETERAN("Vétéran"), MEMBRE("Membre"), RECRUE("Recrue");

        public final String nom;

        Rank(String nom) {
            this.nom = nom;
        }

        public static Rank parse(String s) {
            for (Rank r : values()) if (r.name().equalsIgnoreCase(s)) return r;
            return RECRUE;
        }
    }

    public static final class Member {
        public UUID id;
        public String name = "?";
        public Rank rank = Rank.RECRUE;
        public long joined;
        /** Pièces données au trésor (contributions volontaires). */
        public long contributed;
    }

    public static final class Territory {
        public int id;
        public String dim = "minecraft:overworld";
        public BlockPos center = BlockPos.ORIGIN;
        public long created;
    }

    public enum VoteType {
        EXCLUSION("Exclure"), CHEF("Élire chef"), ABANDON("Abandonner le territoire");

        public final String nom;

        VoteType(String nom) {
            this.nom = nom;
        }
    }

    public static final class Vote {
        public int id;
        public VoteType type = VoteType.CHEF;
        public UUID target;
        public int territory;
        public UUID author;
        public long start, end;
        public final Set<UUID> yes = new HashSet<>(), no = new HashSet<>();
    }

    public static final class MissionProgress {
        public int index;
        public int progress;
        public boolean done;
    }

    public static final class Layer {
        public String pattern = "";
        public int color;

        public Layer() {}

        public Layer(String pattern, int color) {
            this.pattern = pattern;
            this.color = color;
        }
    }

    public int id;
    public String name = "";
    public long created;
    public int bannerBase = DyeColor.WHITE.getId();
    public final List<Layer> layers = new ArrayList<>();
    public int level = 1;
    public long xp, totalXp;
    public double xpFrac;
    public long bank;
    /** Extensions de territoire achetées (+2 blocs de rayon chacune). */
    public int extensions;
    public final Map<UUID, Member> members = new LinkedHashMap<>();
    public final List<Territory> territories = new ArrayList<>();
    public int baseTerritory = -1;
    public BlockPos heart;
    public String heartDim = "";
    public int heartHp;
    public long lastHeartHit;
    public final SimpleInventory chest = new FactionChest(this);
    public final List<Vote> votes = new ArrayList<>();
    public int nextVoteId = 1;
    public long lastElection;
    public final List<String> history = new ArrayList<>();
    public int warsWon, warsLost, points;
    public long peaceUntil;
    public String missionsDate = "";
    public final List<MissionProgress> missions = new ArrayList<>();
    public long baseTpReady;

    public Member member(UUID id) {
        return members.get(id);
    }

    public UUID chief() {
        for (Member m : members.values()) if (m.rank == Rank.CHEF) return m.id;
        return null;
    }

    public Territory territory(int tid) {
        for (Territory t : territories) if (t.id == tid) return t;
        return null;
    }

    public Territory base() {
        return territory(baseTerritory);
    }

    public DyeColor color() {
        return DyeColor.byId(bannerBase);
    }

    public int radius() {
        FactionConfig.Data c = FactionConfig.get();
        return c.rayonBase + c.rayonParNiveau * (level - 1) + 2 * extensions;
    }

    public void log(String line) {
        history.add(java.time.LocalDateTime.now().withNano(0).withSecond(0).toString().replace('T', ' ') + " — " + line);
        while (history.size() > 200) history.remove(0);
    }

    // ------------------------------------------------------------------ NBT

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putInt("id", id);
        n.putString("name", name);
        n.putLong("created", created);
        n.putInt("bannerBase", bannerBase);
        NbtList ls = new NbtList();
        for (Layer l : layers) {
            NbtCompound c = new NbtCompound();
            c.putString("p", l.pattern);
            c.putInt("c", l.color);
            ls.add(c);
        }
        n.put("layers", ls);
        n.putInt("level", level);
        n.putLong("xp", xp);
        n.putLong("totalXp", totalXp);
        n.putDouble("xpFrac", xpFrac);
        n.putLong("bank", bank);
        n.putInt("extensions", extensions);
        NbtList ms = new NbtList();
        for (Member m : members.values()) {
            NbtCompound c = new NbtCompound();
            c.putUuid("id", m.id);
            c.putString("name", m.name);
            c.putString("rank", m.rank.name());
            c.putLong("joined", m.joined);
            c.putLong("contributed", m.contributed);
            ms.add(c);
        }
        n.put("members", ms);
        NbtList ts = new NbtList();
        for (Territory t : territories) {
            NbtCompound c = new NbtCompound();
            c.putInt("id", t.id);
            c.putString("dim", t.dim);
            c.putLong("center", t.center.asLong());
            c.putLong("created", t.created);
            ts.add(c);
        }
        n.put("territories", ts);
        n.putInt("baseTerritory", baseTerritory);
        if (heart != null) n.putLong("heart", heart.asLong());
        n.putString("heartDim", heartDim);
        n.putInt("heartHp", heartHp);
        DefaultedList<ItemStack> items = DefaultedList.ofSize(chest.size(), ItemStack.EMPTY);
        for (int i = 0; i < chest.size(); i++) items.set(i, chest.getStack(i));
        n.put("chest", Inventories.writeNbt(new NbtCompound(), items));
        NbtList vs = new NbtList();
        for (Vote v : votes) {
            NbtCompound c = new NbtCompound();
            c.putInt("id", v.id);
            c.putString("type", v.type.name());
            if (v.target != null) c.putUuid("target", v.target);
            c.putInt("territory", v.territory);
            if (v.author != null) c.putUuid("author", v.author);
            c.putLong("start", v.start);
            c.putLong("end", v.end);
            c.put("yes", uuids(v.yes));
            c.put("no", uuids(v.no));
            vs.add(c);
        }
        n.put("votes", vs);
        n.putInt("nextVoteId", nextVoteId);
        n.putLong("lastElection", lastElection);
        NbtList hs = new NbtList();
        history.forEach(h -> hs.add(NbtString.of(h)));
        n.put("history", hs);
        n.putInt("warsWon", warsWon);
        n.putInt("warsLost", warsLost);
        n.putInt("points", points);
        n.putLong("peaceUntil", peaceUntil);
        n.putString("missionsDate", missionsDate);
        NbtList mi = new NbtList();
        for (MissionProgress m : missions) {
            NbtCompound c = new NbtCompound();
            c.putInt("i", m.index);
            c.putInt("p", m.progress);
            c.putBoolean("d", m.done);
            mi.add(c);
        }
        n.put("missions", mi);
        n.putLong("baseTpReady", baseTpReady);
        return n;
    }

    private static NbtList uuids(Set<UUID> set) {
        NbtList l = new NbtList();
        set.forEach(u -> l.add(NbtString.of(u.toString())));
        return l;
    }

    private static void readUuids(NbtList l, Set<UUID> into) {
        for (int i = 0; i < l.size(); i++) {
            try {
                into.add(UUID.fromString(l.getString(i)));
            } catch (IllegalArgumentException ignored) { }
        }
    }

    public static Faction fromNbt(NbtCompound n) {
        Faction f = new Faction();
        f.id = n.getInt("id");
        f.name = n.getString("name");
        f.created = n.getLong("created");
        f.bannerBase = n.getInt("bannerBase");
        NbtList ls = n.getList("layers", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < ls.size(); i++) f.layers.add(new Layer(ls.getCompound(i).getString("p"), ls.getCompound(i).getInt("c")));
        f.level = Math.max(1, n.getInt("level"));
        f.xp = n.getLong("xp");
        f.totalXp = n.getLong("totalXp");
        f.xpFrac = n.getDouble("xpFrac");
        f.bank = n.getLong("bank");
        f.extensions = n.getInt("extensions");
        NbtList ms = n.getList("members", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < ms.size(); i++) {
            NbtCompound c = ms.getCompound(i);
            Member m = new Member();
            m.id = c.getUuid("id");
            m.name = c.getString("name");
            m.rank = Rank.parse(c.getString("rank"));
            m.joined = c.getLong("joined");
            m.contributed = c.getLong("contributed");
            f.members.put(m.id, m);
        }
        NbtList ts = n.getList("territories", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < ts.size(); i++) {
            NbtCompound c = ts.getCompound(i);
            Territory t = new Territory();
            t.id = c.getInt("id");
            t.dim = c.getString("dim");
            t.center = BlockPos.fromLong(c.getLong("center"));
            t.created = c.getLong("created");
            f.territories.add(t);
        }
        f.baseTerritory = n.contains("baseTerritory") ? n.getInt("baseTerritory") : -1;
        if (n.contains("heart")) f.heart = BlockPos.fromLong(n.getLong("heart"));
        f.heartDim = n.getString("heartDim");
        f.heartHp = n.getInt("heartHp");
        DefaultedList<ItemStack> items = DefaultedList.ofSize(f.chest.size(), ItemStack.EMPTY);
        Inventories.readNbt(n.getCompound("chest"), items);
        for (int i = 0; i < items.size(); i++) f.chest.setStack(i, items.get(i));
        NbtList vs = n.getList("votes", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < vs.size(); i++) {
            NbtCompound c = vs.getCompound(i);
            Vote v = new Vote();
            v.id = c.getInt("id");
            try {
                v.type = VoteType.valueOf(c.getString("type"));
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (c.containsUuid("target")) v.target = c.getUuid("target");
            v.territory = c.getInt("territory");
            if (c.containsUuid("author")) v.author = c.getUuid("author");
            v.start = c.getLong("start");
            v.end = c.getLong("end");
            readUuids(c.getList("yes", NbtElement.STRING_TYPE), v.yes);
            readUuids(c.getList("no", NbtElement.STRING_TYPE), v.no);
            f.votes.add(v);
        }
        f.nextVoteId = Math.max(1, n.getInt("nextVoteId"));
        f.lastElection = n.getLong("lastElection");
        NbtList hs = n.getList("history", NbtElement.STRING_TYPE);
        for (int i = 0; i < hs.size(); i++) f.history.add(hs.getString(i));
        f.warsWon = n.getInt("warsWon");
        f.warsLost = n.getInt("warsLost");
        f.points = n.getInt("points");
        f.peaceUntil = n.getLong("peaceUntil");
        f.missionsDate = n.getString("missionsDate");
        NbtList mi = n.getList("missions", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < mi.size(); i++) {
            MissionProgress m = new MissionProgress();
            m.index = mi.getCompound(i).getInt("i");
            m.progress = mi.getCompound(i).getInt("p");
            m.done = mi.getCompound(i).getBoolean("d");
            f.missions.add(m);
        }
        f.baseTpReady = n.getLong("baseTpReady");
        return f;
    }
}
