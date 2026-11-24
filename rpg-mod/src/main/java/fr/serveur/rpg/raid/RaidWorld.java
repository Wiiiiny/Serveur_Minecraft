package fr.serveur.rpg.raid;

import fr.serveur.rpg.SurvieRpg;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Le monde du raid (dimension survierpg:raid, type Nether, vide, biome des deltas de basalte) et le hub.
 * La zone est centrée sur 0 0 ; chaque cellule fait {@code cellule} blocs de côté.
 */
public final class RaidWorld {
    public static final RegistryKey<World> KEY = RegistryKey.of(RegistryKeys.WORLD, new Identifier(SurvieRpg.MOD_ID, "raid"));
    /** Marque des objets trouvés dans le raid : perdus si on ne s'extrait pas, retirée à l'extraction. */
    public static final String LOOT_TAG = "survierpg_raid";

    private RaidWorld() {}

    public static ServerWorld get(MinecraftServer server) {
        return server.getWorld(KEY);
    }

    public static boolean isIn(Entity e) {
        return e.getWorld().getRegistryKey() == KEY;
    }

    public static int originX() {
        RaidConfig.Data c = RaidConfig.get();
        return -(c.tailleZone / c.cellule) * c.cellule / 2;
    }

    public static int originZ() {
        return originX();
    }

    public static int sideBlocks() {
        RaidConfig.Data c = RaidConfig.get();
        return (c.tailleZone / c.cellule) * c.cellule;
    }

    /** Ramène un joueur au hub (ou au point d'apparition du monde si le GM n'a pas défini de hub). */
    public static void sendToHub(ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        RaidState s = RaidState.get(server);
        ServerWorld world = null;
        if (s.hubDim != null) world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, new Identifier(s.hubDim)));
        if (world == null || s.hub == null) {
            world = server.getOverworld();
            BlockPos spawn = world.getSpawnPos();
            p.teleport(world, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, p.getYaw(), p.getPitch());
            return;
        }
        p.teleport(world, s.hub.getX() + 0.5, s.hub.getY(), s.hub.getZ() + 0.5, s.hubYaw, 0);
    }

    public static boolean isLoot(ItemStack s) {
        NbtCompound n = s.getNbt();
        return n != null && n.getBoolean(LOOT_TAG);
    }

    public static void markLoot(ItemStack s) {
        s.getOrCreateNbt().putBoolean(LOOT_TAG, true);
    }

    /** Retire la marque « butin du raid » (le butin est désormais à lui). */
    public static void unmarkLoot(ItemStack s) {
        NbtCompound n = s.getNbt();
        if (n == null) return;
        n.remove(LOOT_TAG);
        if (n.isEmpty()) s.setNbt(null);
    }
}
