package fr.serveur.rpg.raid;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** État persistant du raid (world/data/survierpg_raid.dat) : cycle en cours, marqueurs de la génération, coffres de corps. */
public final class RaidState extends PersistentState {
    private static final String ID = "survierpg_raid";

    public enum Status { FERME, GENERATION, OUVERT }

    public record Chest(BlockPos pos, int tier, boolean trap) {}

    public record Spot(BlockPos pos, String type) {}

    /** Empreinte d'une arène (pour interdire la balise pendant un combat de boss). */
    public record Area(int minX, int minZ, int maxX, int maxZ) {
        public boolean contains(double x, double z) {
            return x >= minX && x < maxX + 1 && z >= minZ && z < maxZ + 1;
        }
    }

    public static final class Corpse {
        public BlockPos pos;
        public UUID owner;
        public String ownerName = "";
        public final List<UUID> group = new ArrayList<>();
        public long expires;
        public NbtList items = new NbtList();
    }

    public Status status = Status.FERME;
    public int cycle;
    public long cycleStart, nextReset;
    public int threat;
    /** Point de retour (hub) et portail d'entrée dans le monde normal. */
    public String hubDim;
    public BlockPos hub, portal;
    public float hubYaw;
    public int height;
    public final List<BlockPos> spawns = new ArrayList<>();
    public final List<Chest> chests = new ArrayList<>();
    /** Mini-boss, statues, voleurs : position et type. */
    public final List<Spot> spots = new ArrayList<>();
    public final List<Area> arenas = new ArrayList<>();
    public BlockPos pedestal, portalCenter, treasure;
    public int keysInserted;
    /** Extraction finale : le portail s'ouvre à {@code portalOpensAt} et se ferme à {@code portalClosesAt} (ms, 0 = inactif). */
    public long portalOpensAt, portalClosesAt;
    public final List<Corpse> corpses = new ArrayList<>();
    public final List<String> rooms = new ArrayList<>();

    public static RaidState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(RaidState::fromNbt, RaidState::new, ID);
    }

    public RaidConfig.Threat threat() {
        var list = RaidConfig.get().niveaux;
        return list.get(Math.max(0, Math.min(threat, list.size() - 1)));
    }

    public void clearLayout() {
        spawns.clear();
        chests.clear();
        spots.clear();
        arenas.clear();
        rooms.clear();
        pedestal = portalCenter = treasure = null;
        keysInserted = 0;
        portalOpensAt = portalClosesAt = 0;
        markDirty();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putString("status", status.name());
        nbt.putInt("cycle", cycle);
        nbt.putLong("cycleStart", cycleStart);
        nbt.putLong("nextReset", nextReset);
        nbt.putInt("threat", threat);
        if (hubDim != null && hub != null) {
            nbt.putString("hubDim", hubDim);
            nbt.putLong("hub", hub.asLong());
            nbt.putFloat("hubYaw", hubYaw);
        }
        if (portal != null) nbt.putLong("portal", portal.asLong());
        nbt.putInt("height", height);
        nbt.putLongArray("spawns", spawns.stream().mapToLong(BlockPos::asLong).toArray());
        NbtList c = new NbtList();
        for (Chest ch : chests) {
            NbtCompound e = new NbtCompound();
            e.putLong("pos", ch.pos().asLong());
            e.putInt("tier", ch.tier());
            e.putBoolean("trap", ch.trap());
            c.add(e);
        }
        nbt.put("chests", c);
        NbtList s = new NbtList();
        for (Spot sp : spots) {
            NbtCompound e = new NbtCompound();
            e.putLong("pos", sp.pos().asLong());
            e.putString("type", sp.type());
            s.add(e);
        }
        nbt.put("spots", s);
        NbtList a = new NbtList();
        for (Area ar : arenas) a.add(new net.minecraft.nbt.NbtIntArray(new int[]{ar.minX(), ar.minZ(), ar.maxX(), ar.maxZ()}));
        nbt.put("arenas", a);
        if (pedestal != null) nbt.putLong("pedestal", pedestal.asLong());
        if (portalCenter != null) nbt.putLong("portalCenter", portalCenter.asLong());
        if (treasure != null) nbt.putLong("treasure", treasure.asLong());
        nbt.putInt("keysInserted", keysInserted);
        nbt.putLong("portalOpensAt", portalOpensAt);
        nbt.putLong("portalClosesAt", portalClosesAt);
        NbtList co = new NbtList();
        for (Corpse cp : corpses) {
            NbtCompound e = new NbtCompound();
            e.putLong("pos", cp.pos.asLong());
            e.putUuid("owner", cp.owner);
            e.putString("ownerName", cp.ownerName);
            NbtList g = new NbtList();
            cp.group.forEach(u -> g.add(NbtString.of(u.toString())));
            e.put("group", g);
            e.putLong("expires", cp.expires);
            e.put("items", cp.items);
            co.add(e);
        }
        nbt.put("corpses", co);
        NbtList r = new NbtList();
        rooms.forEach(x -> r.add(NbtString.of(x)));
        nbt.put("rooms", r);
        return nbt;
    }

    public static RaidState fromNbt(NbtCompound nbt) {
        RaidState s = new RaidState();
        try {
            s.status = Status.valueOf(nbt.getString("status"));
        } catch (IllegalArgumentException e) {
            s.status = Status.FERME;
        }
        if (s.status == Status.GENERATION) s.status = Status.FERME; // génération interrompue par un arrêt : on recommencera
        s.cycle = nbt.getInt("cycle");
        s.cycleStart = nbt.getLong("cycleStart");
        s.nextReset = nbt.getLong("nextReset");
        s.threat = nbt.getInt("threat");
        if (nbt.contains("hub")) {
            s.hubDim = nbt.getString("hubDim");
            s.hub = BlockPos.fromLong(nbt.getLong("hub"));
            s.hubYaw = nbt.getFloat("hubYaw");
        }
        if (nbt.contains("portal")) s.portal = BlockPos.fromLong(nbt.getLong("portal"));
        s.height = nbt.getInt("height");
        for (long l : nbt.getLongArray("spawns")) s.spawns.add(BlockPos.fromLong(l));
        NbtList c = nbt.getList("chests", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < c.size(); i++) {
            NbtCompound e = c.getCompound(i);
            s.chests.add(new Chest(BlockPos.fromLong(e.getLong("pos")), e.getInt("tier"), e.getBoolean("trap")));
        }
        NbtList sp = nbt.getList("spots", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < sp.size(); i++) {
            NbtCompound e = sp.getCompound(i);
            s.spots.add(new Spot(BlockPos.fromLong(e.getLong("pos")), e.getString("type")));
        }
        NbtList a = nbt.getList("arenas", NbtElement.INT_ARRAY_TYPE);
        for (int i = 0; i < a.size(); i++) {
            int[] v = a.getIntArray(i);
            if (v.length == 4) s.arenas.add(new Area(v[0], v[1], v[2], v[3]));
        }
        if (nbt.contains("pedestal")) s.pedestal = BlockPos.fromLong(nbt.getLong("pedestal"));
        if (nbt.contains("portalCenter")) s.portalCenter = BlockPos.fromLong(nbt.getLong("portalCenter"));
        if (nbt.contains("treasure")) s.treasure = BlockPos.fromLong(nbt.getLong("treasure"));
        s.keysInserted = nbt.getInt("keysInserted");
        s.portalOpensAt = nbt.getLong("portalOpensAt");
        s.portalClosesAt = nbt.getLong("portalClosesAt");
        NbtList co = nbt.getList("corpses", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < co.size(); i++) {
            NbtCompound e = co.getCompound(i);
            Corpse cp = new Corpse();
            cp.pos = BlockPos.fromLong(e.getLong("pos"));
            cp.owner = e.getUuid("owner");
            cp.ownerName = e.getString("ownerName");
            NbtList g = e.getList("group", NbtElement.STRING_TYPE);
            for (int k = 0; k < g.size(); k++) cp.group.add(UUID.fromString(g.getString(k)));
            cp.expires = e.getLong("expires");
            cp.items = e.getList("items", NbtElement.COMPOUND_TYPE);
            s.corpses.add(cp);
        }
        NbtList r = nbt.getList("rooms", NbtElement.STRING_TYPE);
        for (int i = 0; i < r.size(); i++) s.rooms.add(r.getString(i));
        return s;
    }
}
