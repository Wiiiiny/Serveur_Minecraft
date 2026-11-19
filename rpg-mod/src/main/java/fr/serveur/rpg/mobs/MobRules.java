package fr.serveur.rpg.mobs;

import fr.serveur.rpg.Msg;
import fr.serveur.rpg.data.RpgState;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Créatures :
 * - animaux : +5 cœurs ;
 * - zombies, squelettes, araignées, pillards : neutres ; seule la créature attaquée devient
 *   hostile envers son agresseur, jusqu'à sa mort (les autres ne viennent pas l'aider) ;
 * - phantoms : vitesse et cadence d'attaque divisées par deux (voir les mixins Phantom*).
 */
public final class MobRules {
    private static final UUID ANIMAL_HEALTH_ID = UUID.fromString("5e1a7d2c-4b9e-4c1a-9f6e-2d8b3c4a5e60");
    public static final String AGGRO_TAG_PREFIX = "survierpg_aggro_";
    private static final long COMBAT_TAG_MS = 15_000;
    private static final Map<UUID, Long> LAST_PVP = new HashMap<>();

    private MobRules() {}

    public static boolean isNeutralType(Entity e) {
        return e instanceof ZombieEntity || e instanceof AbstractSkeletonEntity || e instanceof SpiderEntity || e instanceof PillagerEntity;
    }

    public static boolean isProvokedBy(Entity mob, PlayerEntity player) {
        return mob.getCommandTags().contains(AGGRO_TAG_PREFIX + player.getUuidAsString());
    }

    public static void register() {
        // +10 points de vie pour les animaux (une seule fois : le modificateur est sauvegardé avec l'animal).
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof AnimalEntity animal)) return;
            EntityAttributeInstance hp = animal.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
            if (hp == null || hp.getModifier(ANIMAL_HEALTH_ID) != null) return;
            hp.addPersistentModifier(new EntityAttributeModifier(ANIMAL_HEALTH_ID, "Survie RPG : +5 coeurs", 10.0,
                    EntityAttributeModifier.Operation.ADDITION));
            animal.setHealth(animal.getHealth() + 10.0f);
        });

        // Une créature neutre attaquée par un joueur devient hostile envers lui (et seulement lui).
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (isNeutralType(entity) && source.getAttacker() instanceof PlayerEntity player && !player.isCreative()) {
                entity.addCommandTag(AGGRO_TAG_PREFIX + player.getUuidAsString());
            }
            if (entity instanceof ServerPlayerEntity victim && source.getAttacker() instanceof ServerPlayerEntity attacker && attacker != victim) {
                long now = System.currentTimeMillis();
                LAST_PVP.put(victim.getUuid(), now);
                LAST_PVP.put(attacker.getUuid(), now);
            }
            return true;
        });

        // Statistique : créatures tuées.
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, killer, killed) -> {
            if (killer instanceof ServerPlayerEntity p && !(killed instanceof PlayerEntity)) {
                RpgState s = RpgState.get(p.getServer());
                s.player(p).mobKills++;
                s.markDirty();
            }
        });

        // Déconnexion en plein combat : le personnage disparaît, et tout le monde le sait.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Long last = LAST_PVP.remove(handler.player.getUuid());
            if (last != null && System.currentTimeMillis() - last < COMBAT_TAG_MS) {
                Msg.broadcast(server, Text.literal(handler.player.getGameProfile().getName() + " a fui en plein combat !").formatted(Formatting.RED));
            }
        });
    }
}
