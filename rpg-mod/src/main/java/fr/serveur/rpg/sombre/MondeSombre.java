package fr.serveur.rpg.sombre;

import fr.serveur.rpg.SurvieRpg;
import fr.serveur.rpg.data.PlayerData;
import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.world.DayCycle;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Monde sombre : branche les systèmes (nuit permanente, niveau du personnage, Obscurcie, coffres cachés, raretés,
 * exotiques, boutique de découverte) sur les événements du jeu.
 */
public final class MondeSombre {
    /** Table des raretés envoyée aux joueurs (contours des objets). */
    public static final Identifier RARETES = new Identifier(SurvieRpg.MOD_ID, "raretes");
    private static final Map<UUID, Vec3d> EXPLORE_ANCHOR = new HashMap<>();

    private MondeSombre() {}

    public static void register() {
        CoffresCaches.register();
        ServerTickEvents.END_SERVER_TICK.register(MondeSombre::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendRarities(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            EXPLORE_ANCHOR.remove(handler.player.getUuid());
            fr.serveur.rpg.faction.Territories.forget(handler.player.getUuid());
        });

        // Combats : XP de personnage pour tout monstre, Obscurcie pour les combats de nuit.
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, killer, killed) -> {
            if (!(killer instanceof ServerPlayerEntity p) || !(killed instanceof Monster)) return;
            boolean boss = killed.getMaxHealth() >= 100;
            SombreConfig.Data c = SombreConfig.get();
            Personnage.addXp(p, boss ? c.personnage.xpBoss : c.personnage.xpMonstre, null);
            fr.serveur.rpg.faction.Factions.onKill(p, boss);
            if (DayCycle.isNight(p.getServer().getOverworld())) {
                Obscurcie.addXp(p, boss ? c.obscurcie.xpBossNuit : c.obscurcie.xpMonstreNuit, true);
            }
        });

        // Collecte de ressources (pas avec Toucher de soie : le bloc pourrait être reposé et recassé).
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
            if (!(player instanceof ServerPlayerEntity p) || p.isCreative()) return;
            Integer xp = SombreConfig.get().obscurcie.xpRessources.get(Registries.BLOCK.getId(state.getBlock()).toString());
            if (xp == null || xp <= 0) return;
            if (EnchantmentHelper.getLevel(Enchantments.SILK_TOUCH, p.getMainHandStack()) > 0) return;
            Obscurcie.addXp(p, xp, true);
            if (state.isOf(fr.serveur.rpg.blocks.ModBlocks.PIERRE_OBSCURE)) fr.serveur.rpg.faction.Factions.onPierre(p);
        });
    }

    /** Une part de l'XP de métier (principal ou supplémentaire) va au personnage et à l'Obscurcie. */
    public static void onJobXp(ServerPlayerEntity p, int amount) {
        if (amount <= 0) return;
        SombreConfig.Data c = SombreConfig.get();
        Personnage.addXp(p, amount * c.personnage.partXpMetiers, null);
        Obscurcie.addXp(p, amount * c.obscurcie.partXpMetiers, false);
    }

    public static void onJobLevel(ServerPlayerEntity p) {
        Personnage.addXp(p, SombreConfig.get().personnage.xpNiveauMetier, "niveau de métier");
        fr.serveur.rpg.faction.Factions.onJobLevel(p);
    }

    /** Quête terminée : XP de personnage et d'Obscurcie. */
    public static void onQuest(ServerPlayerEntity p) {
        SombreConfig.Data c = SombreConfig.get();
        Personnage.addXp(p, c.personnage.xpQuete, "quête");
        Obscurcie.addXp(p, c.obscurcie.xpQuete, false);
        fr.serveur.rpg.faction.Factions.onQuest(p);
    }

    private static void tick(MinecraftServer server) {
        int t = server.getTicks();
        if (t % 10 == 5) {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) Exotiques.sparkleHolder(p);
        }
        if (t % 40 == 0) scanAll(server);
        if (t % 200 == 100) explore(server);
    }

    /** Toutes les 2 s : duplicatas d'exotiques, effets des exotiques, découvertes. */
    private static void scanAll(MinecraftServer server) {
        RpgState state = RpgState.get(server);
        Set<String> seen = new HashSet<>();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator()) continue;
            Exotiques.scan(p, p.getInventory(), "inventaire", seen);
            Exotiques.scan(p, p.getEnderChestInventory(), "coffre de l'Ender", seen);
            Exotiques.applyEffects(p);
            PlayerData d = state.player(p);
            Decouvertes.scan(p, d, state);
        }
    }

    /** Exploration nocturne : XP d'Obscurcie tous les N blocs parcourus à la surface du monde, la nuit. */
    private static void explore(MinecraftServer server) {
        SombreConfig.Obscurcie c = SombreConfig.get().obscurcie;
        boolean night = DayCycle.isNight(server.getOverworld());
        RpgState state = RpgState.get(server);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.getWorld().getRegistryKey() != World.OVERWORLD || p.isCreative() || p.isSpectator() || p.isFallFlying() || p.hasVehicle()) {
                EXPLORE_ANCHOR.remove(p.getUuid());
                continue;
            }
            Vec3d anchor = EXPLORE_ANCHOR.get(p.getUuid());
            Vec3d here = p.getPos();
            if (anchor == null) {
                EXPLORE_ANCHOR.put(p.getUuid(), here);
                continue;
            }
            double dx = here.x - anchor.x, dz = here.z - anchor.z;
            if (dx * dx + dz * dz < (double) c.explorationBlocs * c.explorationBlocs) continue;
            EXPLORE_ANCHOR.put(p.getUuid(), here);
            if (!night) continue;
            PlayerData d = state.player(p);
            d.rollDailyCaps();
            if (d.exploreXpToday >= c.explorationPlafondJour) continue;
            int xp = Math.min(c.xpExploration, c.explorationPlafondJour - d.exploreXpToday);
            d.exploreXpToday += xp;
            Obscurcie.addXp(p, xp, true);
        }
    }

    // ------------------------------------------------------------------ réseau

    public static void sendRarities(ServerPlayerEntity p) {
        if (!ServerPlayNetworking.canSend(p, RARETES)) return;
        NbtCompound n = new NbtCompound();
        SombreConfig.objets().raretes.forEach(n::putString);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeNbt(n);
        ServerPlayNetworking.send(p, RARETES, buf);
    }

    /** Après /gm recharger : config relue, table renvoyée à tous. */
    public static void reload(MinecraftServer server) {
        SombreConfig.load();
        Rarete.resolve();
        fr.serveur.rpg.faction.FactionConfig.load();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) sendRarities(p);
    }
}
