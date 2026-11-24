package fr.serveur.rpg.raid.statue;

import fr.serveur.rpg.SurvieRpg;
import fr.serveur.rpg.items.RaidItems;
import fr.serveur.rpg.raid.RaidMobs;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.Comparator;
import java.util.List;

/** Enregistrement des statues, apparition, limite de 6 statues actives autour d'un joueur. */
public final class Statues {
    public static final EntityType<StatueEntity> TYPE = FabricEntityTypeBuilder.<StatueEntity>create(SpawnGroup.MONSTER, StatueEntity::new)
            .dimensions(EntityDimensions.fixed(1.0f, 3.0f)).fireImmune().trackRangeBlocks(80).build();
    /** Jamais plus de N statues actives autour d'un même joueur (les autres restent figées). */
    public static int MAX_ACTIVE_PER_PLAYER = 6;

    private Statues() {}

    public static void register() {
        Registry.register(Registries.ENTITY_TYPE, new Identifier(SurvieRpg.MOD_ID, "statue"), TYPE);
        FabricDefaultAttributeRegistry.register(TYPE, StatueEntity.createAttributes());
    }

    public static void spawn(ServerWorld w, BlockPos pos) {
        StatueEntity s = TYPE.create(w);
        if (s == null) return;
        s.refreshPositionAndAngles(pos, w.random.nextFloat() * 360, 0);
        s.addCommandTag(RaidMobs.RAID_MOB_TAG);
        w.spawnEntity(s);
    }

    /**
     * Statue « en sommeil » : déjà 6 statues plus proches que celle-ci autour de son joueur le plus proche.
     * Profite du passage pour montrer des particules sombres au porteur d'un Brise-statue tout proche.
     */
    static boolean dormant(StatueEntity statue, List<ServerPlayerEntity> players) {
        ServerPlayerEntity nearest = players.stream().min(Comparator.comparingDouble(p -> p.squaredDistanceTo(statue))).orElse(null);
        if (nearest == null) return true;
        if (nearest.getMainHandStack().isOf(RaidItems.BRISE_STATUE) && nearest.squaredDistanceTo(statue) < 100
                && statue.getWorld() instanceof ServerWorld w) {
            w.spawnParticles(ParticleTypes.SQUID_INK, statue.getX(), statue.getY() + 1.5, statue.getZ(), 3, 0.4, 0.9, 0.4, 0.01);
        }
        double mine = nearest.squaredDistanceTo(statue);
        int closer = statue.getWorld().getEntitiesByClass(StatueEntity.class, new Box(nearest.getBlockPos()).expand(48),
                o -> o != statue && o.isAlive() && nearest.squaredDistanceTo(o) < mine).size();
        return closer >= MAX_ACTIVE_PER_PLAYER;
    }
}
