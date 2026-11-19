package fr.serveur.rpg.mining;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * Mémorise les blocs posés par des joueurs : les recasser ne donne ni XP ni progression de quête
 * (évite la triche « poser / casser »). Sauvegardé avec le monde (world/data/survierpg.dat),
 * donc la protection tient aussi après un redémarrage. Limité à 200 000 blocs par dimension.
 */
public final class PlacedBlocks {
    private static final int MAX_PER_WORLD = 200_000;
    private static final Map<RegistryKey<World>, LongLinkedOpenHashSet> PLACED = new HashMap<>();

    private PlacedBlocks() {}

    public static void add(World world, BlockPos pos) {
        LongLinkedOpenHashSet set = PLACED.computeIfAbsent(world.getRegistryKey(), k -> new LongLinkedOpenHashSet());
        set.add(pos.asLong());
        if (set.size() > MAX_PER_WORLD) set.removeFirstLong();
        dirty(world);
    }

    private static void dirty(World world) {
        if (world instanceof net.minecraft.server.world.ServerWorld sw) fr.serveur.rpg.data.RpgState.get(sw.getServer()).markDirty();
    }

    public static void clear() {
        PLACED.clear();
    }

    public static net.minecraft.nbt.NbtCompound toNbt() {
        net.minecraft.nbt.NbtCompound n = new net.minecraft.nbt.NbtCompound();
        PLACED.forEach((key, set) -> n.putLongArray(key.getValue().toString(), set.toLongArray()));
        return n;
    }

    public static void load(net.minecraft.nbt.NbtCompound n) {
        PLACED.clear();
        for (String dim : n.getKeys()) {
            RegistryKey<World> key = RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, new net.minecraft.util.Identifier(dim));
            PLACED.put(key, new LongLinkedOpenHashSet(n.getLongArray(dim)));
        }
    }

    /** Retire le bloc de la liste ; renvoie vrai s'il avait été posé par un joueur. */
    public static boolean remove(World world, BlockPos pos) {
        LongLinkedOpenHashSet set = PLACED.get(world.getRegistryKey());
        boolean removed = set != null && set.remove(pos.asLong());
        if (removed) dirty(world);
        return removed;
    }
}
