package fr.serveur.rpg.raid;

import fr.serveur.rpg.Msg;
import fr.serveur.rpg.items.RaidItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MagmaCubeEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Créatures du raid : les 3 mini-boss (un par arène, tirés au sort), leurs pouvoirs et leurs clés,
 * les pièges des coffres, et le placement des statues et des voleurs aux marqueurs.
 */
public final class RaidMobs {
    public static final String RAID_MOB_TAG = "survierpg_raid_mob", BOSS_TAG = "survierpg_boss", SCALED_TAG = "survierpg_boss_scaled";

    private record BossType(String id, String name, EntityType<? extends MobEntity> type, EntityType<? extends MobEntity> minion, Formatting color) {}

    private static final List<BossType> BOSSES = List.of(
            new BossType("capitaine", "Capitaine Wither Squelette", EntityType.WITHER_SKELETON, EntityType.WITHER_SKELETON, Formatting.DARK_GRAY),
            new BossType("chef_piglin", "Chef Piglin Brute", EntityType.PIGLIN_BRUTE, EntityType.PIGLIN, Formatting.GOLD),
            new BossType("matriarche", "Matriarche Blaze", EntityType.BLAZE, EntityType.BLAZE, Formatting.YELLOW),
            new BossType("golem_magma", "Golem de magma", EntityType.MAGMA_CUBE, EntityType.MAGMA_CUBE, Formatting.RED));

    private static final Map<UUID, ServerBossBar> BARS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_ATTACK = new HashMap<>();

    private RaidMobs() {}

    /** Après la génération : mini-boss, statues (selon le niveau de menace) et voleurs aux marqueurs. */
    public static void populate(ServerWorld w, RaidState s) {
        List<BlockPos> statueSpots = new ArrayList<>(), thiefSpots = new ArrayList<>();
        for (RaidState.Spot spot : s.spots) {
            switch (spot.type()) {
                case "boss" -> spawnBoss(w, s, spot.pos());
                case "statue" -> statueSpots.add(spot.pos());
                case "voleur" -> thiefSpots.add(spot.pos());
                default -> { }
            }
        }
        java.util.Random r = new java.util.Random(w.random.nextLong());
        java.util.Collections.shuffle(statueSpots, r);
        java.util.Collections.shuffle(thiefSpots, r);
        for (int i = 0; i < Math.min(statueSpots.size(), s.threat().statues); i++) fr.serveur.rpg.raid.statue.Statues.spawn(w, statueSpots.get(i));
        for (int i = 0; i < Math.min(thiefSpots.size(), RaidConfig.get().voleursMax); i++) fr.serveur.rpg.raid.thief.Thieves.spawn(w, thiefSpots.get(i));
    }

    // ------------------------------------------------------------------ mini-boss

    /** /gm raid spawn gardien : un mini-boss tiré au sort, ici. */
    public static void spawnBoss(ServerWorld w, BlockPos pos) {
        spawnBoss(w, RaidState.get(w.getServer()), pos);
    }

    private static void spawnBoss(ServerWorld w, RaidState s, BlockPos pos) {
        BossType t = BOSSES.get(w.random.nextInt(BOSSES.size()));
        MobEntity mob = t.type().create(w);
        if (mob == null) return;
        mob.refreshPositionAndAngles(pos, w.random.nextFloat() * 360, 0);
        mob.initialize(w, w.getLocalDifficulty(pos), SpawnReason.EVENT, null, null);
        if (mob instanceof MagmaCubeEntity cube) cube.setSize(4, true);
        mob.setCustomName(Text.literal(t.name()).formatted(t.color(), Formatting.BOLD));
        mob.setCustomNameVisible(true);
        mob.setPersistent();
        mob.addCommandTag(BOSS_TAG);
        mob.addCommandTag(RAID_MOB_TAG);
        mob.addCommandTag("survierpg_boss_" + t.id());
        setMaxHealth(mob, RaidConfig.get().bossVieBase * s.threat().vieBoss);
        w.spawnEntity(mob);
    }

    private static void setMaxHealth(LivingEntity e, double hp) {
        var a = e.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (a == null) return;
        a.setBaseValue(Math.max(10, hp));
        e.setHealth(e.getMaxHealth());
    }

    private static BossType type(Entity e) {
        for (BossType t : BOSSES) if (e.getCommandTags().contains("survierpg_boss_" + t.id())) return t;
        return BOSSES.get(0);
    }

    /** Chaque seconde : barre de vie, mise à l'échelle selon les joueurs présents, attaques spéciales. */
    public static void tick(ServerWorld w, RaidState s, long now) {
        List<MobEntity> bosses = new ArrayList<>();
        for (Entity e : w.iterateEntities()) if (e instanceof MobEntity m && m.isAlive() && m.getCommandTags().contains(BOSS_TAG)) bosses.add(m);
        BARS.entrySet().removeIf(en -> {
            boolean alive = bosses.stream().anyMatch(b -> b.getUuid().equals(en.getKey()));
            if (!alive) en.getValue().clearPlayers();
            return !alive;
        });
        RaidConfig.Data c = RaidConfig.get();
        for (MobEntity boss : bosses) {
            BossType t = type(boss);
            List<ServerPlayerEntity> near = w.getPlayers(p -> p.isAlive() && !p.isSpectator() && p.squaredDistanceTo(boss) < 40 * 40);
            ServerBossBar bar = BARS.computeIfAbsent(boss.getUuid(), u -> new ServerBossBar(boss.getDisplayName(), BossBar.Color.RED, BossBar.Style.NOTCHED_10));
            bar.setPercent(boss.getHealth() / boss.getMaxHealth());
            for (ServerPlayerEntity p : new ArrayList<>(bar.getPlayers())) if (!near.contains(p)) bar.removePlayer(p);
            for (ServerPlayerEntity p : near) bar.addPlayer(p);
            if (near.isEmpty()) continue;
            // Vie mise à l'échelle la première fois que des joueurs approchent.
            if (!boss.getCommandTags().contains(SCALED_TAG) && near.stream().anyMatch(p -> p.squaredDistanceTo(boss) < 24 * 24)) {
                boss.addCommandTag(SCALED_TAG);
                double factor = 1 + c.bossVieParJoueur / 100.0 * (near.size() - 1);
                setMaxHealth(boss, c.bossVieBase * s.threat().vieBoss * factor);
                for (ServerPlayerEntity p : near) Msg.send(p, Text.literal(t.name() + " se réveille !").formatted(t.color(), Formatting.BOLD));
            }
            long next = NEXT_ATTACK.getOrDefault(boss.getUuid(), now + c.bossAttaqueSecondes * 1000L);
            NEXT_ATTACK.putIfAbsent(boss.getUuid(), next);
            if (now < next || boss.getTarget() == null) continue;
            NEXT_ATTACK.put(boss.getUuid(), now + c.bossAttaqueSecondes * 1000L);
            switch (w.random.nextInt(3)) {
                case 0 -> shockwave(w, boss);
                case 1 -> summon(w, boss, t);
                default -> ignite(w, boss);
            }
        }
    }

    /** Onde de choc : dégâts et recul autour du boss. */
    private static void shockwave(ServerWorld w, MobEntity boss) {
        w.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1f, 0.7f);
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI / 12;
            w.spawnParticles(ParticleTypes.EXPLOSION, boss.getX() + Math.cos(a) * 4, boss.getY() + 0.5, boss.getZ() + Math.sin(a) * 4, 1, 0, 0, 0, 0);
        }
        for (ServerPlayerEntity p : w.getPlayers(p -> p.squaredDistanceTo(boss) < 36 && !p.isSpectator())) {
            p.damage(w.getDamageSources().mobAttack(boss), 6f);
            Vec3d push = p.getPos().subtract(boss.getPos()).normalize().multiply(1.4);
            p.addVelocity(push.x, 0.5, push.z);
            p.velocityModified = true;
        }
    }

    /** Appel : deux ou trois serviteurs à côté du boss. */
    private static void summon(ServerWorld w, MobEntity boss, BossType t) {
        w.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.HOSTILE, 1f, 0.8f);
        int n = 2 + w.random.nextInt(2);
        for (int i = 0; i < n; i++) {
            MobEntity m = t.minion().create(w);
            if (m == null) continue;
            BlockPos at = boss.getBlockPos().add(w.random.nextInt(5) - 2, 0, w.random.nextInt(5) - 2);
            m.refreshPositionAndAngles(at, 0, 0);
            m.initialize(w, w.getLocalDifficulty(at), SpawnReason.MOB_SUMMONED, null, null);
            if (m instanceof MagmaCubeEntity cube) cube.setSize(1, true);
            m.addCommandTag(RAID_MOB_TAG);
            w.spawnEntity(m);
            w.spawnParticles(ParticleTypes.LARGE_SMOKE, m.getX(), m.getY() + 1, m.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
        }
    }

    /** Embrasement : les joueurs proches prennent feu et sont ralentis. */
    private static void ignite(ServerWorld w, MobEntity boss) {
        w.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1f, 0.6f);
        w.spawnParticles(ParticleTypes.FLAME, boss.getX(), boss.getY() + 1, boss.getZ(), 80, 3, 1, 3, 0.05);
        for (ServerPlayerEntity p : w.getPlayers(p -> p.squaredDistanceTo(boss) < 64 && !p.isSpectator())) {
            p.setOnFireFor(4);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1));
        }
    }

    /** Mort d'un mini-boss : une clé de gardien (et du butin de palier 2), marqués « butin du raid ». */
    public static void onDeath(LivingEntity e, DamageSource source) {
        if (!(e.getWorld() instanceof ServerWorld w) || !e.getCommandTags().contains(BOSS_TAG)) return;
        BossType t = type(e);
        List<ItemStack> drops = new ArrayList<>(Loot.roll(2, w.random));
        ItemStack key = new ItemStack(RaidItems.CLE_RAID);
        RaidWorld.markLoot(key);
        drops.add(key);
        for (ItemStack s : drops) w.spawnEntity(new ItemEntity(w, e.getX(), e.getY() + 0.5, e.getZ(), s));
        ServerBossBar bar = BARS.remove(e.getUuid());
        if (bar != null) bar.clearPlayers();
        Extraction.broadcastRaid(w.getServer(), Text.literal(t.name() + " est vaincu ! Il laisse une clé de gardien.").formatted(Formatting.GOLD, Formatting.BOLD));
    }

    /** Le joueur est dans une arène où un gardien est encore en vie (la balise de retour y est interdite). */
    public static boolean bossFightAt(ServerPlayerEntity p) {
        RaidState s = RaidState.get(p.getServer());
        for (RaidState.Area a : s.arenas) {
            if (!a.contains(p.getX(), p.getZ())) continue;
            Box box = new Box(a.minX(), p.getY() - 16, a.minZ(), a.maxX() + 1, p.getY() + 16, a.maxZ() + 1);
            if (!p.getServerWorld().getEntitiesByClass(MobEntity.class, box, m -> m.isAlive() && m.getCommandTags().contains(BOSS_TAG)).isEmpty()) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ pièges et renforts

    /** Coffre piégé : une statue (ou un voleur) apparaît derrière le joueur. */
    public static void trap(ServerWorld w, ServerPlayerEntity p, BlockPos chest) {
        Vec3d back = p.getRotationVec(1f).multiply(-3);
        BlockPos at = BlockPos.ofFloored(p.getX() + back.x, p.getY(), p.getZ() + back.z);
        if (!w.getBlockState(at).isAir()) at = chest.up();
        if (w.random.nextBoolean()) fr.serveur.rpg.raid.statue.Statues.spawn(w, at);
        else fr.serveur.rpg.raid.thief.Thieves.spawn(w, at);
        w.playSound(null, chest, SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.HOSTILE, 0.8f, 1.3f);
        p.sendMessage(Text.literal("Un piège ! Quelque chose est apparu derrière toi…").formatted(Formatting.DARK_RED), true);
    }

    /** Pendant l'ouverture du portail d'extraction : quelques statues en plus autour de la salle. */
    public static void extraStatues(ServerWorld w, RaidState s, BlockPos center) {
        if (w.random.nextInt(3) != 0) return;
        BlockPos at = center.add(w.random.nextInt(17) - 8, 0, w.random.nextInt(17) - 8);
        if (w.getBlockState(at).isAir() && w.getBlockState(at.up()).isAir()) fr.serveur.rpg.raid.statue.Statues.spawn(w, at);
    }
}
