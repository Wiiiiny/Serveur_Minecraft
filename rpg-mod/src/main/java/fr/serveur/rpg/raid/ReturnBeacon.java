package fr.serveur.rpg.raid;

import fr.serveur.rpg.items.RaidItems;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Balise de retour : canalisation de 10 s, immobile, interrompue par les dégâts ou un mouvement. Utilisable partout
 * dans le raid sauf dans une arène pendant un combat de boss. Elle est consommée à la fin.
 */
public final class ReturnBeacon {
    private record Channel(Vec3d start, long endTick) {}

    private static final Map<UUID, Channel> CHANNELS = new HashMap<>();
    private static long ticks;

    private ReturnBeacon() {}

    public static boolean start(ServerPlayerEntity p, Hand hand) {
        if (!RaidWorld.isIn(p)) {
            p.sendMessage(Text.literal("La balise de retour ne fonctionne que dans la zone de raid.").formatted(Formatting.RED), true);
            return false;
        }
        if (CHANNELS.containsKey(p.getUuid())) return false;
        if (RaidMobs.bossFightAt(p)) {
            p.sendMessage(Text.literal("Impossible d'utiliser la balise pendant le combat contre le gardien.").formatted(Formatting.RED), true);
            return false;
        }
        int seconds = Math.max(1, RaidConfig.get().baliseCanalisationSecondes);
        CHANNELS.put(p.getUuid(), new Channel(p.getPos(), ticks + seconds * 20L));
        p.playSound(SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1f, 0.9f);
        p.sendMessage(Text.literal("La balise s'allume… ne bouge plus (" + seconds + " s).").formatted(Formatting.GOLD), true);
        return true;
    }

    public static boolean isChanneling(ServerPlayerEntity p) {
        return CHANNELS.containsKey(p.getUuid());
    }

    public static void cancel(ServerPlayerEntity p) {
        CHANNELS.remove(p.getUuid());
    }

    /** Dégâts reçus : la canalisation s'arrête. */
    public static void onDamage(ServerPlayerEntity p) {
        if (CHANNELS.remove(p.getUuid()) != null) {
            p.sendMessage(Text.literal("Balise interrompue : tu as été blessé.").formatted(Formatting.RED), true);
        }
    }

    public static void tick(net.minecraft.server.MinecraftServer server) {
        ticks++;
        if (CHANNELS.isEmpty()) return;
        Iterator<Map.Entry<UUID, Channel>> it = CHANNELS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Channel> e = it.next();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
            Channel c = e.getValue();
            if (p == null || !p.isAlive() || !RaidWorld.isIn(p)) {
                it.remove();
                continue;
            }
            Vec3d pos = p.getPos();
            if (Math.hypot(pos.x - c.start().x, pos.z - c.start().z) > 0.35 || Math.abs(pos.y - c.start().y) > 0.8) {
                it.remove();
                p.sendMessage(Text.literal("Balise interrompue : tu as bougé.").formatted(Formatting.RED), true);
                continue;
            }
            long left = c.endTick() - ticks;
            if (left > 0) {
                if (left % 5 == 0) p.getServerWorld().spawnParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1, p.getZ(), 8, 0.4, 0.8, 0.4, 0.02);
                if (left % 20 == 0) p.sendMessage(Text.literal("Balise de retour : " + left / 20 + " s…").formatted(Formatting.GOLD), true);
                continue;
            }
            it.remove();
            if (!consume(p)) {
                p.sendMessage(Text.literal("Tu n'as plus de balise de retour.").formatted(Formatting.RED), true);
                continue;
            }
            Extraction.extract(p, false);
        }
    }

    private static boolean consume(ServerPlayerEntity p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(RaidItems.BALISE_RETOUR)) {
                s.decrement(1);
                return true;
            }
        }
        return false;
    }
}
