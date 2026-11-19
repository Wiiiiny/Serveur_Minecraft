package fr.serveur.rpg.data;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Données persistantes du serveur RPG. Sauvegardées dans world/data/survierpg.dat
 * à chaque sauvegarde automatique et à l'arrêt du serveur.
 */
public final class RpgState extends PersistentState {
    private static final String ID = "survierpg";

    public final Map<UUID, PlayerData> players = new HashMap<>();
    /** Identifiants des outils officiels valides (un outil révoqué n'y est plus). */
    public final Set<UUID> officialTools = new HashSet<>();
    public final Map<String, Zone> zones = new LinkedHashMap<>();
    public int nextQuestId = 1;
    /** Bonus d'argent sur les quêtes, en % par niveau de prestige (réglé par le GM). */
    public int prestigeBonusPercent = 10;
    /** Hôtel des ventes. */
    public final List<MarketItem> auctions = new ArrayList<>();
    /** Boutique du GM (stock illimité). */
    public final List<MarketItem> shop = new ArrayList<>();
    public int nextMarketId = 1;
    /** Propriétaires des coffres : dimension -> position (long) -> joueur. */
    public final Map<String, Map<Long, UUID>> chestOwners = new HashMap<>();
    /** Chance de réussite d'un crochet, en %. */
    public int lockpickChance = 50;
    /** Durée du jour et de la nuit, en minutes réelles (réglées par le GM). */
    public double dayMinutes = 12, nightMinutes = 8;
    /** Salle d'attente des morts, à la mairie (null = pas encore définie). */
    public String waitingRoomDim;
    public BlockPos waitingRoom;
    /** Accès au Nether et à l'End (débloqués manuellement par le GM). */
    public boolean netherOpen, endOpen;
    /** La boutique a-t-elle déjà été remplie avec les articles par défaut ? */
    public boolean shopSeeded;

    public static RpgState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(RpgState::fromNbt, RpgState::new, ID);
    }

    public PlayerData player(PlayerEntity player) {
        PlayerData d = players.computeIfAbsent(player.getUuid(), u -> new PlayerData());
        String name = player.getGameProfile().getName();
        if (!name.equals(d.name)) {
            d.name = name;
            markDirty();
        }
        return d;
    }

    /** Retrouve un joueur (même hors ligne) par son pseudo. */
    public Map.Entry<UUID, PlayerData> findByName(String name) {
        for (Map.Entry<UUID, PlayerData> e : players.entrySet()) {
            if (e.getValue().name.equalsIgnoreCase(name)) return e;
        }
        return null;
    }

    public Zone zoneAt(World world, BlockPos pos) {
        for (Zone z : zones.values()) if (z.contains(world, pos)) return z;
        return null;
    }

    public boolean isProtected(World world, BlockPos pos) {
        for (Zone z : zones.values()) if (z.protectedZone && z.contains(world, pos)) return true;
        return false;
    }

    /** Une zone protégée se trouve-t-elle à moins de {@code r} blocs ? */
    public boolean nearProtected(World world, BlockPos pos, int r) {
        for (Zone z : zones.values()) if (z.protectedZone && z.intersects(world, pos, r)) return true;
        return false;
    }

    /** Zone de métier qui interdit la récolte à ce métier, ou null. */
    public Zone forbiddingJobZone(World world, BlockPos pos, fr.serveur.rpg.jobs.Job job) {
        for (Zone z : zones.values()) if (z.job != null && z.job != job && z.contains(world, pos)) return z;
        return null;
    }

    public UUID chestOwner(World world, BlockPos pos) {
        Map<Long, UUID> m = chestOwners.get(world.getRegistryKey().getValue().toString());
        return m == null ? null : m.get(pos.asLong());
    }

    public void setChestOwner(World world, BlockPos pos, UUID owner) {
        String dim = world.getRegistryKey().getValue().toString();
        if (owner == null) {
            Map<Long, UUID> m = chestOwners.get(dim);
            if (m != null && m.remove(pos.asLong()) != null) markDirty();
        } else {
            chestOwners.computeIfAbsent(dim, d -> new HashMap<>()).put(pos.asLong(), owner);
            markDirty();
        }
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound p = new NbtCompound();
        players.forEach((uuid, data) -> p.put(uuid.toString(), data.toNbt()));
        nbt.put("players", p);

        NbtList tools = new NbtList();
        officialTools.forEach(u -> tools.add(NbtString.of(u.toString())));
        nbt.put("tools", tools);

        NbtList z = new NbtList();
        zones.values().forEach(zone -> z.add(zone.toNbt()));
        nbt.put("zones", z);

        nbt.putInt("nextQuestId", nextQuestId);
        nbt.putInt("prestigeBonusPercent", prestigeBonusPercent);
        nbt.put("auctions", MarketItem.toNbtList(auctions));
        nbt.put("shop", MarketItem.toNbtList(shop));
        nbt.putInt("nextMarketId", nextMarketId);
        nbt.putInt("lockpickChance", lockpickChance);
        nbt.putBoolean("netherOpen", netherOpen);
        nbt.putBoolean("shopSeeded", shopSeeded);
        nbt.putBoolean("endOpen", endOpen);
        nbt.putDouble("dayMinutes", dayMinutes);
        nbt.putDouble("nightMinutes", nightMinutes);
        if (waitingRoom != null) {
            nbt.putString("waitingRoomDim", waitingRoomDim);
            nbt.putLong("waitingRoom", waitingRoom.asLong());
        }
        NbtCompound chests = new NbtCompound();
        chestOwners.forEach((dim, m) -> {
            NbtList l = new NbtList();
            m.forEach((pos, owner) -> {
                NbtCompound c = new NbtCompound();
                c.putLong("pos", pos);
                c.putUuid("owner", owner);
                l.add(c);
            });
            chests.put(dim, l);
        });
        nbt.put("chests", chests);
        return nbt;
    }

    public static RpgState fromNbt(NbtCompound nbt) {
        RpgState s = new RpgState();
        NbtCompound p = nbt.getCompound("players");
        for (String key : p.getKeys()) {
            try {
                s.players.put(UUID.fromString(key), PlayerData.fromNbt(p.getCompound(key)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        NbtList tools = nbt.getList("tools", NbtElement.STRING_TYPE);
        for (int i = 0; i < tools.size(); i++) {
            try {
                s.officialTools.add(UUID.fromString(tools.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        NbtList z = nbt.getList("zones", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < z.size(); i++) {
            Zone zone = Zone.fromNbt(z.getCompound(i));
            if (zone != null) s.zones.put(zone.name, zone);
        }
        s.nextQuestId = Math.max(1, nbt.getInt("nextQuestId"));
        if (nbt.contains("prestigeBonusPercent")) s.prestigeBonusPercent = nbt.getInt("prestigeBonusPercent");
        s.auctions.addAll(MarketItem.fromNbtList(nbt.getList("auctions", NbtElement.COMPOUND_TYPE)));
        s.shop.addAll(MarketItem.fromNbtList(nbt.getList("shop", NbtElement.COMPOUND_TYPE)));
        s.nextMarketId = Math.max(1, nbt.getInt("nextMarketId"));
        if (nbt.contains("lockpickChance")) s.lockpickChance = nbt.getInt("lockpickChance");
        s.netherOpen = nbt.getBoolean("netherOpen");
        s.shopSeeded = nbt.getBoolean("shopSeeded");
        s.endOpen = nbt.getBoolean("endOpen");
        if (nbt.contains("dayMinutes")) s.dayMinutes = nbt.getDouble("dayMinutes");
        if (nbt.contains("nightMinutes")) s.nightMinutes = nbt.getDouble("nightMinutes");
        if (nbt.contains("waitingRoom")) {
            s.waitingRoomDim = nbt.getString("waitingRoomDim");
            s.waitingRoom = BlockPos.fromLong(nbt.getLong("waitingRoom"));
        }
        NbtCompound chests = nbt.getCompound("chests");
        for (String dim : chests.getKeys()) {
            NbtList l = chests.getList(dim, NbtElement.COMPOUND_TYPE);
            Map<Long, UUID> m = new HashMap<>();
            for (int i = 0; i < l.size(); i++) {
                NbtCompound c = l.getCompound(i);
                if (c.containsUuid("owner")) m.put(c.getLong("pos"), c.getUuid("owner"));
            }
            s.chestOwners.put(dim, m);
        }
        return s;
    }
}
