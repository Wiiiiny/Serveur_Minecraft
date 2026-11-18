package fr.serveur.nightfall;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public class Nightfall implements ModInitializer {
    public static final String MOD_ID = "nightfall";

    /** Items qui comptent comme « lanterne » (data/nightfall/tags/items/lanterns.json). */
    public static final TagKey<Item> LANTERNS = TagKey.of(RegistryKeys.ITEM, id("lanterns"));
    public static final RegistryKey<DamageType> SUNLIGHT = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, id("sunlight"));

    public static final Item SHADOW_HOOD = new ArmorItem(ShadowArmorMaterial.INSTANCE, ArmorItem.Type.HELMET, new Item.Settings());

    private static final int SLOWNESS_DURATION = 60;

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        Registry.register(Registries.ITEM, id("shadow_hood"), SHADOW_HOOD);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.add(SHADOW_HOOD));
        ServerTickEvents.END_WORLD_TICK.register(Nightfall::onWorldTick);
    }

    // ---------------------------------------------------------------------

    public static boolean isAffectedWorld(World world) {
        return world.getRegistryKey() == World.OVERWORLD;
    }

    public static long timeOfDay(World world) {
        return Math.floorMod(world.getTimeOfDay(), 24000L);
    }

    public static boolean isFullNight(World world) {
        long t = timeOfDay(world);
        return t >= NightfallConfig.NIGHT_START && t < NightfallConfig.NIGHT_END;
    }

    public static boolean isDay(World world) {
        return timeOfDay(world) < NightfallConfig.DUSK_START;
    }

    public static boolean holdsLantern(PlayerEntity player) {
        return player.getMainHandStack().isIn(LANTERNS) || player.getOffHandStack().isIn(LANTERNS);
    }

    public static boolean wearsShadowHood(PlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.HEAD).isOf(SHADOW_HOOD);
    }

    // ---------------------------------------------------------------------

    private static void onWorldTick(ServerWorld world) {
        if (!isAffectedWorld(world)) return;
        long gameTime = world.getTime();
        boolean slownessTick = gameTime % 20 == 0;
        boolean sunTick = gameTime % NightfallConfig.SUN_DAMAGE_INTERVAL_TICKS == 0;
        if (!slownessTick && !sunTick) return;

        boolean night = isFullNight(world);
        boolean day = isDay(world);

        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isCreative() || player.isSpectator() || !player.isAlive()) continue;

            if (slownessTick) {
                if (night && !holdsLantern(player)) {
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, SLOWNESS_DURATION,
                            NightfallConfig.NIGHT_SLOWNESS_AMPLIFIER, true, false, true));
                } else {
                    removeNightSlowness(player);
                }
            }

            if (sunTick && day && !wearsShadowHood(player)) {
                float room = player.getHealth() - NightfallConfig.SUN_DAMAGE_MIN_HEALTH;
                if (room > 0) {
                    DamageSource source = new DamageSource(
                            world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(SUNLIGHT));
                    player.damage(source, Math.min(NightfallConfig.SUN_DAMAGE_AMOUNT, room));
                }
            }
        }
    }

    /** Retire uniquement la lenteur posée par le mod (pas celle d'une potion). */
    private static void removeNightSlowness(ServerPlayerEntity player) {
        StatusEffectInstance current = player.getStatusEffect(StatusEffects.SLOWNESS);
        if (current != null && current.isAmbient() && !current.shouldShowParticles()
                && current.getAmplifier() == NightfallConfig.NIGHT_SLOWNESS_AMPLIFIER
                && current.getDuration() <= SLOWNESS_DURATION) {
            player.removeStatusEffect(StatusEffects.SLOWNESS);
        }
    }
}
