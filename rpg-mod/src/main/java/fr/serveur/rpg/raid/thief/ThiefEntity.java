package fr.serveur.rpg.raid.thief;

import fr.serveur.rpg.Msg;
import fr.serveur.rpg.data.PlayerData;
import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.death.DeathSystem;
import fr.serveur.rpg.economy.Bank;
import fr.serveur.rpg.items.RaidItems;
import fr.serveur.rpg.raid.Loot;
import fr.serveur.rpg.raid.RaidWorld;
import fr.serveur.rpg.raid.statue.StatueEntity;
import fr.serveur.rpg.world.TravelBeacon;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Chapardeur : petit monstre de la taille d'une poule. Il s'approche, vole un objet au hasard (jamais l'armure portée,
 * l'objet en main ni les objets liés), puis s'enfuit ; au bout de 60 s il disparaît avec. On le récupère en le tuant,
 * ou en lui donnant des Pièces d'or (clic droit) : chaque pièce donnée augmente l'affinité du joueur avec les voleurs.
 * Affinité 20 : il ne te vole plus et rend l'objet au premier clic ; 50 : il te suit et te prévient des statues ;
 * 100 : il devient ton compagnon (voir {@link Thieves#tickCompanions}).
 */
public class ThiefEntity extends PathAwareEntity {
    public enum Mode { APPROCHE, FUITE, SUIT, COMPAGNON }

    private Mode mode = Mode.APPROCHE;
    private ItemStack stolen = ItemStack.EMPTY;
    private UUID victim, owner;
    private int fleeUntil, warnCooldown;

    public ThiefEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        setEquipmentDropChance(EquipmentSlot.MAINHAND, 0f);
        setPersistent();
        experiencePoints = 1;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 6)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.36)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32);
    }

    public Mode mode() {
        return mode;
    }

    public UUID owner() {
        return owner;
    }

    /** Fait de ce voleur le compagnon de {@code player}. */
    public void bindCompanion(UUID player) {
        mode = Mode.COMPAGNON;
        owner = player;
        removeScoreboardTag(fr.serveur.rpg.raid.RaidMobs.RAID_MOB_TAG);
        addCommandTag(Thieves.COMPANION_TAG);
        setCustomName(Text.literal("Compagnon").formatted(Formatting.GOLD));
    }

    @Override
    protected void initGoals() {
        // Comportement géré dans tick().
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld w) || age % 10 != 0) return;
        switch (mode) {
            case APPROCHE -> approach(w);
            case FUITE -> flee(w);
            case SUIT -> follow(w, false);
            case COMPAGNON -> follow(w, true);
        }
    }

    private void approach(ServerWorld w) {
        if (!RaidWorld.isIn(this)) return; // pas de vol hors du raid (hub)
        ServerPlayerEntity target = w.getPlayers(p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && p.squaredDistanceTo(this) < 24 * 24
                && Thieves.canBeRobbed(p, this)).stream().min(Comparator.comparingDouble(p -> p.squaredDistanceTo(this))).orElse(null);
        if (target == null) {
            getNavigation().stop();
            return;
        }
        int affinity = affinity(target);
        if (affinity >= Thieves.AFFINITE_SUIT) {
            mode = Mode.SUIT;
            owner = target.getUuid();
            return;
        }
        if (squaredDistanceTo(target) < 2.0) {
            steal(w, target);
            return;
        }
        getNavigation().startMovingTo(target, 0.9);
    }

    private void steal(ServerWorld w, ServerPlayerEntity p) {
        var inv = p.getInventory();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inv.main.size(); i++) {
            ItemStack s = inv.main.get(i);
            if (s.isEmpty() || i == inv.selectedSlot || Thieves.isBound(s)) continue;
            slots.add(i);
        }
        if (slots.isEmpty()) return;
        int slot = slots.get(w.random.nextInt(slots.size()));
        stolen = inv.main.get(slot).copy();
        inv.main.set(slot, ItemStack.EMPTY);
        equipStack(EquipmentSlot.MAINHAND, stolen.copy());
        victim = p.getUuid();
        mode = Mode.FUITE;
        fleeUntil = age + 60 * 20;
        w.playSound(null, getBlockPos(), SoundEvents.ENTITY_ALLAY_ITEM_TAKEN, SoundCategory.HOSTILE, 1f, 1.4f);
        Msg.send(p, Text.literal("Un chapardeur t'a volé ").formatted(Formatting.RED)
                .append(Text.literal(stolen.getCount() + "x ").append(stolen.getName()).formatted(Formatting.GOLD))
                .append(Text.literal(" ! Tue-le ou donne-lui des Pièces d'or avant qu'il ne disparaisse (60 s).").formatted(Formatting.RED)));
    }

    private void flee(ServerWorld w) {
        if (age >= fleeUntil) {
            Thieves.logLoss(w.getServer(), victim, stolen);
            ServerPlayerEntity p = victim == null ? null : w.getServer().getPlayerManager().getPlayer(victim);
            if (p != null) Msg.send(p, Text.literal("Le chapardeur a disparu avec ").formatted(Formatting.RED).append(stolen.getName()).append("."));
            w.spawnParticles(ParticleTypes.POOF, getX(), getY() + 0.3, getZ(), 12, 0.2, 0.2, 0.2, 0.02);
            discard();
            return;
        }
        ServerPlayerEntity p = victim == null ? null : w.getServer().getPlayerManager().getPlayer(victim);
        if (p == null || p.getWorld() != w) return;
        Vec3d away = NoPenaltyTargeting.findFrom(this, 16, 7, p.getPos());
        if (away != null) getNavigation().startMovingTo(away.x, away.y, away.z, 1.7);
    }

    private void follow(ServerWorld w, boolean companion) {
        ServerPlayerEntity p = owner == null ? null : w.getServer().getPlayerManager().getPlayer(owner);
        if (p == null || p.getWorld() != w) {
            if (!companion) mode = Mode.APPROCHE;
            return;
        }
        int affinity = affinity(p);
        // Plus l'affinité est haute, plus il suit de près.
        double distance = companion ? 2.5 : Math.max(3, 9 - affinity / 12.0);
        double d2 = squaredDistanceTo(p);
        if (d2 > 24 * 24) {
            refreshPositionAndAngles(p.getX(), p.getY(), p.getZ(), getYaw(), 0);
            getNavigation().stop();
        } else if (d2 > distance * distance) getNavigation().startMovingTo(p, 1.3);
        else getNavigation().stop();
        // Il prévient des statues proches.
        if (--warnCooldown <= 0 && !w.getEntitiesByClass(StatueEntity.class, new Box(p.getBlockPos()).expand(16), s -> s.isAlive()).isEmpty()) {
            warnCooldown = 20;
            w.playSound(null, getBlockPos(), SoundEvents.ENTITY_PARROT_IMITATE_WITHER_SKELETON, SoundCategory.NEUTRAL, 1f, 1.6f);
            p.sendMessage(Text.literal("Ton petit voleur s'agite : une statue est tout près…").formatted(Formatting.LIGHT_PURPLE), true);
        }
        if (companion) {
            // Ramasse les objets au sol autour de lui et les donne à son maître.
            for (ItemEntity item : w.getEntitiesByClass(ItemEntity.class, getBoundingBox().expand(4), i -> i.isAlive() && !i.cannotPickup())) {
                ItemStack s = item.getStack().copy();
                if (p.getInventory().insertStack(s)) item.discard();
                else item.setStack(s);
            }
        }
    }

    private static int affinity(PlayerEntity p) {
        return RpgState.get(p.getServer()).player(p).thiefAffinity;
    }

    // ------------------------------------------------------------------ interactions

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (!(player instanceof ServerPlayerEntity sp) || !(getWorld() instanceof ServerWorld w)) return ActionResult.SUCCESS;
        PlayerData data = RpgState.get(sp.getServer()).player(sp);
        ItemStack held = sp.getStackInHand(hand);
        boolean coins = Bank.isCurrency(held);
        if (!stolen.isEmpty()) {
            long price = ransom(sp);
            if (data.thiefAffinity >= Thieves.AFFINITE_SANS_VOL) {
                giveBack(w, sp, "Le chapardeur te rend l'objet sans rien demander.");
                return ActionResult.SUCCESS;
            }
            if (!coins) {
                sp.sendMessage(Text.literal("Il réclame " + price + " Pièce(s) d'or en main pour rendre l'objet.").formatted(Formatting.GOLD), true);
                return ActionResult.SUCCESS;
            }
            if (held.getCount() < price) {
                sp.sendMessage(Text.literal("Il en veut " + price + ", tu n'en as que " + held.getCount() + " en main.").formatted(Formatting.RED), true);
                return ActionResult.SUCCESS;
            }
            held.decrement((int) price); // ces pièces quittent l'économie (puits d'argent)
            Thieves.addAffinity(sp, (int) price);
            giveBack(w, sp, "Rançon payée (" + price + " pièce" + (price > 1 ? "s" : "") + ") : le chapardeur te rend l'objet.");
            return ActionResult.SUCCESS;
        }
        if (coins && mode != Mode.COMPAGNON) {
            held.decrement(1);
            Thieves.addAffinity(sp, 1);
            w.spawnParticles(ParticleTypes.HEART, getX(), getY() + 0.8, getZ(), 3, 0.2, 0.2, 0.2, 0.01);
            w.playSound(null, getBlockPos(), SoundEvents.ENTITY_ALLAY_AMBIENT_WITH_ITEM, SoundCategory.NEUTRAL, 1f, 1.3f);
            setVelocity(0, 0.35, 0);
            velocityModified = true;
            int a = data.thiefAffinity;
            sp.sendMessage(Text.literal("Le chapardeur accepte la pièce (affinité " + a + ").").formatted(Formatting.LIGHT_PURPLE), true);
            if (a >= Thieves.AFFINITE_COMPAGNON) Thieves.promote(sp, this);
            else if (a >= Thieves.AFFINITE_SUIT) {
                mode = Mode.SUIT;
                owner = sp.getUuid();
            }
            return ActionResult.SUCCESS;
        }
        return super.interactMob(player, hand);
    }

    private long ransom(ServerPlayerEntity p) {
        double value = Loot.value(RpgState.get(p.getServer()), stolen);
        return Math.max(Thieves.RANCON_MIN, Math.round(value * 0.10));
    }

    private void giveBack(ServerWorld w, ServerPlayerEntity p, String message) {
        ItemStack s = stolen;
        stolen = ItemStack.EMPTY;
        equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        if (!p.getInventory().insertStack(s)) p.dropItem(s, false);
        mode = Mode.APPROCHE;
        victim = null;
        w.spawnParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 0.6, getZ(), 6, 0.3, 0.3, 0.3, 0.01);
        w.playSound(null, getBlockPos(), SoundEvents.ENTITY_ALLAY_ITEM_GIVEN, SoundCategory.NEUTRAL, 1f, 1.2f);
        Msg.send(p, Text.literal(message).formatted(Formatting.GREEN));
    }

    @Override
    public void onDeath(DamageSource source) {
        if (!getWorld().isClient && !stolen.isEmpty()) {
            dropStack(stolen);
            stolen = ItemStack.EMPTY;
        }
        if (!getWorld().isClient && mode == Mode.COMPAGNON && owner != null) Thieves.onCompanionDeath(getWorld().getServer(), owner);
        super.onDeath(source);
    }

    // ------------------------------------------------------------------ sons

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_FOX_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_FOX_DEATH;
    }

    @Override
    public float getSoundPitch() {
        return 1.6f;
    }

    // ------------------------------------------------------------------ sauvegarde

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putString("Mode", mode.name());
        if (!stolen.isEmpty()) nbt.put("Stolen", stolen.writeNbt(new NbtCompound()));
        if (victim != null) nbt.putUuid("Victim", victim);
        if (owner != null) nbt.putUuid("Owner", owner);
        nbt.putInt("FleeLeft", Math.max(0, fleeUntil - age));
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        try {
            mode = Mode.valueOf(nbt.getString("Mode"));
        } catch (IllegalArgumentException e) {
            mode = Mode.APPROCHE;
        }
        stolen = nbt.contains("Stolen") ? ItemStack.fromNbt(nbt.getCompound("Stolen")) : ItemStack.EMPTY;
        victim = nbt.containsUuid("Victim") ? nbt.getUuid("Victim") : null;
        owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
        fleeUntil = age + nbt.getInt("FleeLeft");
    }

    /** Objets que le voleur ne prend jamais. */
    static boolean bound(ItemStack s) {
        return DeathSystem.isTear(s) || TravelBeacon.isBeacon(s) || s.isOf(RaidItems.BRISE_STATUE) || s.isOf(RaidItems.BALISE_RETOUR)
                || fr.serveur.rpg.economy.ObscureStone.isPickaxe(s);
    }
}
