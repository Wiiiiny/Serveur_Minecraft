package fr.serveur.rpg.mining;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * Mémorise les blocs posés par des joueurs pour ne pas donner d'XP quand on les recasse
 * (évite la triche « poser / casser »). En mémoire seulement, limité en taille.
 */
public final class PlacedBlocks {
    private static final int MAX_PER_WORLD = 200_000;
    private static final Map<RegistryKey<World>, LongLinkedOpenHashSet> PLACED = new HashMap<>();

    private PlacedBlocks() {}

    public static void add(World world, BlockPos pos) {
        LongLinkedOpenHashSet set = PLACED.computeIfAbsent(world.getRegistryKey(), k -> new LongLinkedOpenHashSet());
        set.add(pos.asLong());
        if (set.size() > MAX_PER_WORLD) set.removeFirstLong();
    }

    /** Retire le bloc de la liste ; renvoie vrai s'il avait été posé par un joueur. */
    public static boolean remove(World world, BlockPos pos) {
        LongLinkedOpenHashSet set = PLACED.get(world.getRegistryKey());
        return set != null && set.remove(pos.asLong());
    }
}
