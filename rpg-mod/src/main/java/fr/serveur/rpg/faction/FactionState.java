package fr.serveur.rpg.faction;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** État persistant des factions (world/data/survierpg_factions.dat) : factions, guerres, invitations. */
public final class FactionState extends PersistentState {
    private static final String ID = "survierpg_factions";

    public static final class War {
        public int id;
        public int attacker, defender;
        public long declared, starts, ended;
        public boolean active, over;
        /** Combats de nuit : acceptés par le chef attaquant, par le chef défenseur. */
        public boolean nightA, nightD;
        /** Faction qui propose de capituler (0 = aucune). */
        public int offer;
        public int winner;
        public String reason = "";

        public boolean involves(int faction) {
            return attacker == faction || defender == faction;
        }

        public int other(int faction) {
            return attacker == faction ? defender : attacker;
        }
    }

    public final Map<Integer, Faction> factions = new LinkedHashMap<>();
    public final List<War> wars = new ArrayList<>();
    /** Invitations en attente : joueur → factions qui l'ont invité. */
    public final Map<UUID, Set<Integer>> invites = new HashMap<>();
    public int nextFactionId = 1, nextTerritoryId = 1, nextWarId = 1;

    public static FactionState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(FactionState::fromNbt, FactionState::new, ID);
    }

    public Faction of(UUID player) {
        for (Faction f : factions.values()) if (f.members.containsKey(player)) return f;
        return null;
    }

    public Faction byName(String name) {
        for (Faction f : factions.values()) if (f.name.equalsIgnoreCase(name)) return f;
        return null;
    }

    /** Guerre en cours (déclarée ou active) entre ces deux factions, ou null. */
    public War warBetween(int a, int b) {
        for (War w : wars) if (!w.over && w.involves(a) && w.involves(b)) return w;
        return null;
    }

    /** Guerre en cours de cette faction, ou null. */
    public War warOf(int faction) {
        for (War w : wars) if (!w.over && w.involves(faction)) return w;
        return null;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList fs = new NbtList();
        factions.values().forEach(f -> fs.add(f.toNbt()));
        nbt.put("factions", fs);
        NbtList ws = new NbtList();
        for (War w : wars) {
            NbtCompound c = new NbtCompound();
            c.putInt("id", w.id);
            c.putInt("attacker", w.attacker);
            c.putInt("defender", w.defender);
            c.putLong("declared", w.declared);
            c.putLong("starts", w.starts);
            c.putLong("ended", w.ended);
            c.putBoolean("active", w.active);
            c.putBoolean("over", w.over);
            c.putBoolean("nightA", w.nightA);
            c.putBoolean("nightD", w.nightD);
            c.putInt("offer", w.offer);
            c.putInt("winner", w.winner);
            c.putString("reason", w.reason);
            ws.add(c);
        }
        nbt.put("wars", ws);
        NbtCompound inv = new NbtCompound();
        invites.forEach((u, set) -> {
            NbtList l = new NbtList();
            set.forEach(id -> l.add(NbtString.of(String.valueOf(id))));
            inv.put(u.toString(), l);
        });
        nbt.put("invites", inv);
        nbt.putInt("nextFactionId", nextFactionId);
        nbt.putInt("nextTerritoryId", nextTerritoryId);
        nbt.putInt("nextWarId", nextWarId);
        return nbt;
    }

    public static FactionState fromNbt(NbtCompound nbt) {
        FactionState s = new FactionState();
        NbtList fs = nbt.getList("factions", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < fs.size(); i++) {
            Faction f = Faction.fromNbt(fs.getCompound(i));
            s.factions.put(f.id, f);
        }
        NbtList ws = nbt.getList("wars", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < ws.size(); i++) {
            NbtCompound c = ws.getCompound(i);
            War w = new War();
            w.id = c.getInt("id");
            w.attacker = c.getInt("attacker");
            w.defender = c.getInt("defender");
            w.declared = c.getLong("declared");
            w.starts = c.getLong("starts");
            w.ended = c.getLong("ended");
            w.active = c.getBoolean("active");
            w.over = c.getBoolean("over");
            w.nightA = c.getBoolean("nightA");
            w.nightD = c.getBoolean("nightD");
            w.offer = c.getInt("offer");
            w.winner = c.getInt("winner");
            w.reason = c.getString("reason");
            s.wars.add(w);
        }
        NbtCompound inv = nbt.getCompound("invites");
        for (String k : inv.getKeys()) {
            try {
                Set<Integer> set = new HashSet<>();
                NbtList l = inv.getList(k, NbtElement.STRING_TYPE);
                for (int i = 0; i < l.size(); i++) set.add(Integer.parseInt(l.getString(i)));
                s.invites.put(UUID.fromString(k), set);
            } catch (IllegalArgumentException ignored) { }
        }
        s.nextFactionId = Math.max(1, nbt.getInt("nextFactionId"));
        s.nextTerritoryId = Math.max(1, nbt.getInt("nextTerritoryId"));
        s.nextWarId = Math.max(1, nbt.getInt("nextWarId"));
        return s;
    }
}
