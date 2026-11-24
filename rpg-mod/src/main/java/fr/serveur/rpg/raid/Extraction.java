package fr.serveur.rpg.raid;

import fr.serveur.rpg.Msg;
import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.economy.Bank;
import fr.serveur.rpg.economy.Economy;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
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

/**
 * Sortie du raid :
 * - avec une balise de retour ({@link ReturnBeacon}) : retour au hub avec le butin, sans bonus ;
 * - par l'extraction finale : 2 clés de gardien sur le socle (pierre de magnétite) ouvrent la salle du trésor et
 *   lancent le portail. Il met 45 s à s'ouvrir (vagues de monstres), puis reste ouvert 60 s : qui y entre sort avec
 *   un bonus proportionnel à la valeur de son butin et au niveau de menace.
 */
public final class Extraction {
    private Extraction() {}

    /** Clic sur le socle avec une clé. @return vrai si l'action a été traitée. */
    public static boolean useKey(ServerPlayerEntity p, ItemStack held, BlockPos clicked) {
        RaidState s = RaidState.get(p.getServer());
        if (s.pedestal == null || !s.pedestal.equals(clicked)) return false;
        int need = RaidConfig.get().clesRequises;
        if (s.portalOpensAt > 0) {
            p.sendMessage(Text.literal("Le portail est déjà en route.").formatted(Formatting.GOLD), true);
            return true;
        }
        if (!held.isOf(fr.serveur.rpg.items.RaidItems.CLE_RAID)) {
            p.sendMessage(Text.literal("Le socle attend des clés de gardien (" + s.keysInserted + " / " + need + ").").formatted(Formatting.GOLD), true);
            return true;
        }
        held.decrement(1);
        s.keysInserted++;
        s.markDirty();
        ServerWorld w = p.getServerWorld();
        w.playSound(null, clicked, SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.BLOCKS, 1f, 0.8f);
        broadcastRaid(p.getServer(), Text.literal(p.getGameProfile().getName() + " pose une clé sur le socle (" + s.keysInserted + " / " + need + ").")
                .formatted(Formatting.GOLD));
        if (s.keysInserted >= need) open(w, s);
        return true;
    }

    private static void open(ServerWorld w, RaidState s) {
        long now = System.currentTimeMillis();
        RaidConfig.Data c = RaidConfig.get();
        s.portalOpensAt = now + c.extractionOuvertureSecondes * 1000L;
        s.portalClosesAt = s.portalOpensAt + c.extractionOuverteSecondes * 1000L;
        s.markDirty();
        if (s.treasure != null) Loot.fill(w, s.treasure, 3, w.random);
        broadcastRaid(w.getServer(), Text.literal("Le trésor est ouvert ! Le portail d'extraction s'ouvre dans " + c.extractionOuvertureSecondes
                + " s : tenez bon !").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
    }

    /** Chaque seconde : vagues pendant l'ouverture, portail ouvert, fermeture. */
    public static void tick(ServerWorld w, RaidState s, long now) {
        if (s.portalOpensAt <= 0 || s.portalCenter == null) return;
        BlockPos c = s.portalCenter;
        if (now < s.portalOpensAt) {
            long left = (s.portalOpensAt - now) / 1000;
            if (left % 10 == 0) wave(w, s, c);
            w.spawnParticles(ParticleTypes.REVERSE_PORTAL, c.getX() + 0.5, c.getY() + 1.5, c.getZ() + 0.5, 20, 0.6, 1.2, 0.6, 0.05);
            return;
        }
        if (now < s.portalClosesAt) {
            w.spawnParticles(ParticleTypes.PORTAL, c.getX() + 0.5, c.getY() + 1.5, c.getZ() + 0.5, 80, 0.8, 1.4, 0.8, 0.4);
            w.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, c.getX() + 0.5, c.getY() + 0.2, c.getZ() + 0.5, 12, 1.2, 0.1, 1.2, 0.01);
            for (ServerPlayerEntity p : new java.util.ArrayList<>(w.getPlayers())) { // extract() fait sortir le joueur du monde
                if (p.squaredDistanceTo(c.getX() + 0.5, c.getY() + 1, c.getZ() + 0.5) <= 4.0 && p.isAlive() && !p.isSpectator()) {
                    extract(p, true);
                }
            }
            return;
        }
        s.portalOpensAt = s.portalClosesAt = 0;
        s.keysInserted = 0;
        s.markDirty();
        broadcastRaid(w.getServer(), Text.literal("Le portail d'extraction s'est refermé.").formatted(Formatting.DARK_PURPLE));
    }

    private static void wave(ServerWorld w, RaidState s, BlockPos c) {
        EntityType<?>[] types = {EntityType.WITHER_SKELETON, EntityType.BLAZE, EntityType.MAGMA_CUBE, EntityType.PIGLIN_BRUTE};
        int count = (int) Math.round(2 * s.threat().monstres);
        for (int i = 0; i < count; i++) {
            BlockPos at = c.add(w.random.nextInt(11) - 5, 1, w.random.nextInt(11) - 5);
            if (!w.getBlockState(at).isAir() || !w.getBlockState(at.up()).isAir()) continue;
            var e = types[w.random.nextInt(types.length)].create(w);
            if (!(e instanceof MobEntity mob)) continue;
            mob.refreshPositionAndAngles(at, w.random.nextFloat() * 360, 0);
            mob.initialize(w, w.getLocalDifficulty(at), SpawnReason.EVENT, null, null);
            mob.addCommandTag(RaidMobs.RAID_MOB_TAG);
            w.spawnEntity(mob);
        }
        RaidMobs.extraStatues(w, s, c);
    }

    /**
     * Fait sortir un joueur : le butin du raid devient le sien (marque retirée) ; avec {@code bonus}, il reçoit
     * un pourcentage de sa valeur, multiplié par le niveau de menace.
     */
    public static void extract(ServerPlayerEntity p, boolean bonus) {
        RpgState rs = RpgState.get(p.getServer());
        double value = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (!RaidWorld.isLoot(st)) continue;
            value += Loot.value(rs, st);
            RaidWorld.unmarkLoot(st);
        }
        ReturnBeacon.cancel(p);
        RaidWorld.sendToHub(p);
        p.playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 1.2f);
        long paid = 0;
        if (bonus && value > 0) {
            RaidState s = RaidState.get(p.getServer());
            double amount = value * RaidConfig.get().extractionBonusPourcentValeur / 100.0 * s.threat().bonusExtraction;
            paid = Economy.earn(p.getServer(), p.getUuid(), Economy.Source.RAID, amount, "extraction (butin " + Math.round(value) + ")");
        }
        Msg.send(p, Text.literal(bonus ? "Extraction réussie ! " : "La balise te ramène au hub. ").formatted(Formatting.GREEN)
                .append(Text.literal("Butin d'une valeur de " + Math.round(value) + " pièces" + (paid > 0 ? ", bonus +" + Bank.format(paid) : "") + ".")
                        .formatted(Formatting.GOLD)));
    }

    static void broadcastRaid(net.minecraft.server.MinecraftServer server, Text text) {
        ServerWorld w = RaidWorld.get(server);
        if (w == null) return;
        for (ServerPlayerEntity p : w.getPlayers()) Msg.send(p, text);
    }
}
