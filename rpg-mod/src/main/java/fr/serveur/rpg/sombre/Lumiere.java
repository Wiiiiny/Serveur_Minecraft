package fr.serveur.rpg.sombre;

import fr.serveur.rpg.Msg;
import fr.serveur.rpg.data.RpgState;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

/**
 * Nuit permanente : le monde reste figé à minuit. Seul un Éclat de Lumière appelle le jour :
 * annonce mondiale, préparation (60 s par défaut), puis jour pendant la « Durée du jour » réglée par le GM,
 * le soleil traversant le ciel. Ensuite la nuit retombe et aucun autre Éclat ne peut servir avant
 * la « Durée de la nuit » (nuit minimale entre deux jours). Un seul jour à la fois : un Éclat utilisé
 * pendant une préparation, un jour ou la nuit minimale est refusé (et n'est pas consommé).
 */
public final class Lumiere {
    /** Minuit (lune au zénith) et lever du soleil, en heure du jeu. */
    private static final long MIDNIGHT = 18000, SUNRISE = 0, DAY_SPAN = 12000;

    public enum Phase { CYCLE, NUIT, PREPARATION, JOUR }

    private Lumiere() {}

    /** Vrai si la nuit permanente pilote l'heure du monde (sinon, cycle jour/nuit classique de DayCycle). */
    public static boolean controls(MinecraftServer server) {
        return SombreState.get(server).nuitPermanente;
    }

    public static Phase phase(MinecraftServer server) {
        SombreState s = SombreState.get(server);
        if (!s.nuitPermanente) return Phase.CYCLE;
        long now = System.currentTimeMillis();
        if (s.prepUntil > now) return Phase.PREPARATION;
        if (s.dayUntil > now) return Phase.JOUR;
        return Phase.NUIT;
    }

    /** Secondes restantes dans la phase en cours (préparation ou jour), sinon 0. */
    public static long secondsLeft(MinecraftServer server) {
        SombreState s = SombreState.get(server);
        long now = System.currentTimeMillis();
        long until = s.prepUntil > now ? s.prepUntil : s.dayUntil;
        return Math.max(0, (until - now + 999) / 1000);
    }

    /** Secondes avant qu'un nouvel Éclat puisse servir (0 = maintenant, s'il fait nuit). */
    public static long secondsToNextEclat(MinecraftServer server) {
        SombreState s = SombreState.get(server);
        long now = System.currentTimeMillis();
        if (s.prepUntil > now || s.dayUntil > now) return secondsLeft(server);
        return Math.max(0, (s.nextEclatAt - now + 999) / 1000);
    }

    /** Appelé à chaque tick par DayCycle quand la nuit permanente est active : fixe l'heure du monde. */
    public static void tick(MinecraftServer server) {
        SombreState s = SombreState.get(server);
        RpgState rs = RpgState.get(server);
        long now = System.currentTimeMillis();
        ServerWorld ow = server.getOverworld();
        long time = ow.getTimeOfDay();
        long dayBase = time - Math.floorMod(time, 24000L);

        // Fin de la préparation : le jour se lève.
        if (s.prepUntil != 0 && now >= s.prepUntil) {
            s.prepUntil = 0;
            s.dayStart = now;
            s.dayUntil = now + (long) (Math.max(0.5, rs.dayMinutes) * 60_000);
            s.markDirty();
            Journal.write(server, "lumiere", "jour", "par", s.eclatBy, "minutes", rs.dayMinutes);
        }
        // Fin du jour : la nuit retombe, nuit minimale avant le prochain Éclat.
        if (s.dayUntil != 0 && now >= s.dayUntil) {
            s.dayUntil = 0;
            s.dayStart = 0;
            s.nextEclatAt = now + (long) (Math.max(0, rs.nightMinutes) * 60_000);
            s.markDirty();
            Journal.write(server, "lumiere", "nuit");
        }

        long target;
        if (s.dayUntil > now && s.dayStart > 0) {
            double f = Math.min(1, (now - s.dayStart) / (double) Math.max(1, s.dayUntil - s.dayStart));
            target = dayBase + SUNRISE + (long) (f * DAY_SPAN);
        } else {
            target = dayBase + MIDNIGHT;
        }
        if (target != time) {
            for (ServerWorld w : server.getWorlds()) {
                if (w == ow || !w.getDimension().hasFixedTime()) w.setTimeOfDay(target);
            }
        }

        if (server.getTicks() % 20 == 0) countdowns(server, s, now);
    }

    private static void countdowns(MinecraftServer server, SombreState s, long now) {
        if (s.prepUntil > now) {
            long left = (s.prepUntil - now + 999) / 1000;
            if (left == 30 || left <= 10) {
                Text t = Text.literal("☀ Le jour se lève dans " + left + " s").formatted(Formatting.GOLD);
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    p.sendMessage(t, true);
                    if (left <= 5) p.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.MASTER, 0.5f, 1.4f);
                }
            }
        } else if (s.dayUntil > now) {
            long left = (s.dayUntil - now + 999) / 1000;
            if (left == SombreConfig.get().lumiere.avertissementFinSecondes) {
                Msg.broadcast(server, Text.literal("La lumière faiblit : la nuit retombe dans " + fr.serveur.rpg.Msg.duration(left) + ".")
                        .formatted(Formatting.DARK_PURPLE));
            }
        }
    }

    /** Un joueur active un Éclat de Lumière. @return null si c'est parti (l'Éclat est consommé), sinon la raison du refus. */
    public static String use(ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        SombreState s = SombreState.get(server);
        if (!s.nuitPermanente) return "Le cycle normal du jour est actif : l'Éclat ne répond pas.";
        if (p.getWorld().getRegistryKey() != World.OVERWORLD) return "L'Éclat ne s'éveille que sous le ciel du monde.";
        long now = System.currentTimeMillis();
        if (s.prepUntil > now) return "Un Éclat s'éveille déjà : le jour se lève dans " + secondsLeft(server) + " s.";
        if (s.dayUntil > now) return "Il fait déjà jour (encore " + fr.serveur.rpg.Msg.duration(secondsLeft(server)) + ").";
        if (s.nextEclatAt > now) return "La nuit doit encore durer " + fr.serveur.rpg.Msg.duration(secondsToNextEclat(server))
                + " avant qu'un Éclat puisse la percer.";
        start(server, p.getGameProfile().getName());
        Obscurcie.addXp(p, SombreConfig.get().obscurcie.xpEclat, true);
        fr.serveur.rpg.faction.Factions.onEclat(p);
        return null;
    }

    /** Lance la préparation du jour (Éclat ou GM). */
    public static void start(MinecraftServer server, String by) {
        SombreState s = SombreState.get(server);
        int prep = Math.max(0, SombreConfig.get().lumiere.preparationSecondes);
        s.prepUntil = System.currentTimeMillis() + prep * 1000L;
        s.eclatBy = by;
        s.markDirty();
        Journal.write(server, "lumiere", "eclat", "par", by, "preparation", prep);
        Text title = Text.literal("ÉCLAT DE LUMIÈRE").formatted(Formatting.YELLOW, Formatting.BOLD);
        Text sub = Text.literal(by + " appelle le jour — " + prep + " s").formatted(Formatting.GOLD);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 70, 20));
            p.networkHandler.sendPacket(new TitleS2CPacket(title));
            p.networkHandler.sendPacket(new SubtitleS2CPacket(sub));
            p.playSound(SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.MASTER, 1f, 1.2f);
        }
        Msg.broadcast(server, Text.literal("☀ " + by + " a brisé un Éclat de Lumière : le jour se lèvera dans " + prep
                + " secondes. Le soleil brûle — couvrez-vous, et rentrez avant la nuit.").formatted(Formatting.GOLD));
    }

    /** GM : jour immédiat (sans préparation) ou fin du jour. */
    public static void forceDay(MinecraftServer server, String by) {
        SombreState s = SombreState.get(server);
        s.prepUntil = System.currentTimeMillis();
        s.eclatBy = by;
        s.markDirty();
    }

    public static void forceNight(MinecraftServer server) {
        SombreState s = SombreState.get(server);
        long now = System.currentTimeMillis();
        s.prepUntil = 0;
        if (s.dayUntil > now) s.dayUntil = now;
        s.markDirty();
    }
}
