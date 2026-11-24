package fr.serveur.rpg.raid.statue;

import fr.serveur.rpg.items.RaidItems;
import fr.serveur.rpg.raid.RaidWorld;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Comparator;
import java.util.List;

/**
 * Statue (« ange pleureur ») : 3 blocs de haut, figée tant qu'au moins un joueur la regarde, et qui fonce sur
 * le joueur le plus proche dès que plus personne ne la voit. Invulnérable sauf au Brise-statue : 3 coups,
 * seulement quand elle est regardée. Elle se met en pause si aucun joueur n'est à moins de 64 blocs.
 */
public class StatueEntity extends HostileEntity {
    /** Demi-angle du champ de vision d'un joueur (degrés), portée de vue, portée d'activité. */
    public static double FOV_HALF_DEG = 50, SIGHT = 48, ACTIVE = 64;
    /** Vitesse quand elle n'est pas vue (blocs par tick). */
    public static double SPEED = 0.35;

    private boolean watched = true, wasWatched = true;
    private Vec3d chaseDir;
    private int hits;

    public StatueEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        setPersistent();
        setStepHeight(1.1f);
        experiencePoints = 0;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 100)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64);
    }

    @Override
    protected void initGoals() {
        // Pas d'IA vanilla : le mouvement est géré dans tick().
    }

    public boolean isWatched() {
        return watched;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld world)) return;
        // Vérification de la vue répartie sur 3 ticks (coûteuse quand il y a beaucoup de statues) ; mouvement à chaque tick.
        if ((age + getId()) % 3 == 0) update(world);
        if (watched || chaseDir == null) {
            setVelocity(0, getVelocity().y, 0);
        } else {
            boolean climb = horizontalCollision && isOnGround();
            setVelocity(chaseDir.x * SPEED, climb ? 0.45 : getVelocity().y, chaseDir.z * SPEED);
            velocityModified = true;
        }
    }

    private void update(ServerWorld world) {
        List<ServerPlayerEntity> players = world.getPlayers(p -> p.isAlive() && !p.isSpectator() && p.squaredDistanceTo(this) < ACTIVE * ACTIVE);
        if (players.isEmpty()) {
            watched = true; // en pause : aucun joueur à moins de 64 blocs
            chaseDir = null;
            return;
        }
        watched = Statues.dormant(this, players) || players.stream().anyMatch(this::seenBy);
        if (watched) {
            chaseDir = null;
            getNavigation().stop();
            if (!wasWatched) world.playSound(null, getBlockPos(), SoundEvents.BLOCK_STONE_PLACE, SoundCategory.HOSTILE, 0.6f, 0.6f);
        } else {
            ServerPlayerEntity target = players.stream().min(Comparator.comparingDouble(p -> p.squaredDistanceTo(this))).orElse(null);
            if (target != null) chase(target);
        }
        wasWatched = watched;
    }

    private void chase(ServerPlayerEntity target) {
        Vec3d to = target.getPos().subtract(getPos());
        double dist = to.horizontalLength();
        setYaw((float) (MathHelper.atan2(to.z, to.x) * 180 / Math.PI) - 90f);
        setBodyYaw(getYaw());
        setHeadYaw(getYaw());
        if (dist < 1.6 && Math.abs(to.y) < 2.5) {
            chaseDir = null;
            if (tryAttack(target)) Fear.onHit(target);
            return;
        }
        // Direction : prochain point du chemin calculé par le jeu (contourne les murs), sinon tout droit.
        Vec3d dir = to.multiply(1, 0, 1).normalize();
        var path = getNavigation().findPathTo(target, 0);
        if (path != null && path.getLength() > 1) {
            var node = path.getNode(1);
            Vec3d nodeDir = new Vec3d(node.x + 0.5 - getX(), 0, node.z + 0.5 - getZ());
            if (nodeDir.lengthSquared() > 0.04) dir = nodeDir.normalize();
        }
        chaseDir = dir;
    }

    /** Le joueur voit-il la statue (dans son champ de vision et sans obstacle opaque) ? */
    private boolean seenBy(ServerPlayerEntity p) {
        if (p.hasStatusEffect(StatusEffects.BLINDNESS) || p.hasStatusEffect(StatusEffects.DARKNESS)) return false;
        if (p.squaredDistanceTo(this) > SIGHT * SIGHT) return false;
        Vec3d eye = p.getEyePos(), look = p.getRotationVec(1f);
        double cos = Math.cos(Math.toRadians(FOV_HALF_DEG));
        for (double h : new double[]{2.7, 1.5, 0.3}) {
            Vec3d point = getPos().add(0, h, 0);
            Vec3d to = point.subtract(eye);
            double len = to.length();
            if (len < 1e-3) return true;
            if (to.multiply(1 / len).dotProduct(look) < cos) continue;
            if (clearLine(eye, point, len)) return true;
        }
        return false;
    }

    /** Aucun bloc opaque plein entre les deux points (verre et feuilles laissent voir). */
    private boolean clearLine(Vec3d from, Vec3d to, double len) {
        Vec3d step = to.subtract(from).multiply(0.3 / len);
        Vec3d at = from;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (double d = 0; d < len - 0.4; d += 0.3) {
            at = at.add(step);
            pos.set(at.x, at.y, at.z);
            if (getWorld().getBlockState(pos).isOpaqueFullCube(getWorld(), pos)) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ dégâts

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        return !(source.getAttacker() instanceof ServerPlayerEntity p && source.getSource() == p
                && p.getMainHandStack().isOf(RaidItems.BRISE_STATUE));
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.damage(source, amount);
        if (!(getWorld() instanceof ServerWorld world)) return false;
        if (!(source.getAttacker() instanceof ServerPlayerEntity p) || source.getSource() != p) return false;
        ItemStack held = p.getMainHandStack();
        if (!held.isOf(RaidItems.BRISE_STATUE)) {
            p.sendMessage(Text.literal("La pierre ne cède pas.").formatted(Formatting.GRAY), true);
            world.playSound(null, getBlockPos(), SoundEvents.BLOCK_STONE_HIT, SoundCategory.HOSTILE, 1f, 0.5f);
            return false;
        }
        if (!watched) return false; // on ne peut la frapper que quand elle est figée
        hits++;
        world.playSound(null, getBlockPos(), SoundEvents.BLOCK_DEEPSLATE_BRICKS_BREAK, SoundCategory.HOSTILE, 1f, 0.7f + hits * 0.15f);
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.CALCITE.getDefaultState()),
                getX(), getY() + 1.5, getZ(), 25, 0.4, 0.8, 0.4, 0.1);
        if (hits < 3) {
            p.sendMessage(Text.literal("La statue se fissure (" + hits + " / 3).").formatted(Formatting.GRAY), true);
            return true;
        }
        shatter(world, p, held);
        return true;
    }

    private void shatter(ServerWorld world, ServerPlayerEntity p, ItemStack tool) {
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.CALCITE.getDefaultState()),
                getX(), getY() + 1.5, getZ(), 120, 0.5, 1.2, 0.5, 0.2);
        world.spawnParticles(ParticleTypes.CLOUD, getX(), getY() + 1, getZ(), 30, 0.5, 1, 0.5, 0.02);
        world.playSound(null, getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_DEATH, SoundCategory.HOSTILE, 1f, 1.4f);
        tool.damage(1, p, pl -> pl.sendToolBreakStatus(net.minecraft.util.Hand.MAIN_HAND));
        int fragments = 1 + world.random.nextInt(2);
        ItemStack drop = new ItemStack(RaidItems.FRAGMENT_TENEBRES, fragments);
        RaidWorld.markLoot(drop);
        world.spawnEntity(new ItemEntity(world, getX(), getY() + 0.5, getZ(), drop));
        p.sendMessage(Text.literal("La statue tombe en poussière.").formatted(Formatting.GOLD), true);
        Fear.onStatueDestroyed(world, getPos());
        discard();
    }

    @Override
    public boolean isFireImmune() {
        return true;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Hits", hits);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        hits = nbt.getInt("Hits");
    }
}
