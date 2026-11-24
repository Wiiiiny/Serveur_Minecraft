package fr.serveur.rpg.faction;

import fr.serveur.rpg.Msg;
import fr.serveur.rpg.economy.Bank;
import fr.serveur.rpg.world.DayCycle;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Guerres entre factions : seul le chef déclare (annonce mondiale, début 5 minutes après). Attaques permises le jour
 * (jour invoqué par un Éclat), ou la nuit si les deux chefs l'acceptent. Fin par capitulation (proposée par un chef,
 * acceptée par l'autre) ou par le Cœur de faction brisé. Le vaincu perd de l'XP (au plus un niveau), des points de
 * classement et son territoire le plus récent (jamais la base) ; paix obligatoire de 3 jours. Pas de guerre automatique :
 * les incidents (attaques, tentatives de casse) sont signalés aux chefs et journalisés.
 */
public final class Wars {
    private static final Map<Integer, Boolean> LAST_OPEN = new HashMap<>();
    private static final Map<String, Long> LAST_INCIDENT = new HashMap<>();
    private static final Map<Integer, Long> LAST_HEART_ALERT = new HashMap<>();

    private Wars() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 == 15) tick(server);
            if (server.getTicks() % 1200 == 30) Factions.tickVotes(server);
        });
    }

    public static boolean attackOpen(MinecraftServer server, FactionState.War w) {
        return w.active && !w.over && (!DayCycle.isNight(server.getOverworld()) || (w.nightA && w.nightD));
    }

    public static boolean canAttack(MinecraftServer server, Faction a, Faction b) {
        if (a == null || b == null || a.id == b.id) return false;
        FactionState.War w = FactionState.get(server).warBetween(a.id, b.id);
        return w != null && attackOpen(server, w);
    }

    private static void title(MinecraftServer server, Faction f, Text title, Text sub) {
        for (UUID u : f.members.keySet()) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(u);
            if (p == null) continue;
            p.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 70, 20));
            p.networkHandler.sendPacket(new TitleS2CPacket(title));
            p.networkHandler.sendPacket(new SubtitleS2CPacket(sub));
            p.playSound(SoundEvents.EVENT_RAID_HORN.value(), SoundCategory.MASTER, 0.7f, 1f);
        }
    }

    // ------------------------------------------------------------------ déclaration

    public static String declare(ServerPlayerEntity p, int targetId) {
        MinecraftServer server = p.getServer();
        FactionState s = FactionState.get(server);
        Faction f = s.of(p.getUuid());
        if (f == null || !Factions.isChief(f, p.getUuid())) return "Seul le chef déclare la guerre.";
        Faction t = s.factions.get(targetId);
        if (t == null || t.id == f.id) return "Faction inconnue.";
        if (s.warOf(f.id) != null) return "Ta faction est déjà en guerre.";
        if (s.warOf(t.id) != null) return "« " + t.name + " » est déjà en guerre.";
        long now = System.currentTimeMillis();
        if (f.peaceUntil > now) return "Paix obligatoire encore " + Msg.duration((f.peaceUntil - now) / 1000) + ".";
        if (t.peaceUntil > now) return "« " + t.name + " » est en paix obligatoire encore " + Msg.duration((t.peaceUntil - now) / 1000) + ".";
        int delay = FactionConfig.get().delaiGuerreMinutes;
        FactionState.War w = new FactionState.War();
        w.id = s.nextWarId++;
        w.attacker = f.id;
        w.defender = t.id;
        w.declared = now;
        w.starts = now + delay * 60_000L;
        s.wars.add(w);
        s.markDirty();
        f.log("Guerre déclarée à « " + t.name + " » par " + p.getGameProfile().getName() + ".");
        t.log("« " + f.name + " » nous déclare la guerre.");
        Factions.journal(server, f, "guerre_declaree", "contre", t.name, "par", p.getGameProfile().getName());
        Msg.broadcast(server, Text.literal("⚔ « " + f.name + " » déclare la guerre à « " + t.name + " » ! Les combats commencent dans "
                + delay + " minutes — de jour, ou de nuit si les deux chefs l'acceptent.").formatted(Formatting.RED, Formatting.BOLD));
        Text sub = Text.literal("Début dans " + delay + " minutes").formatted(Formatting.GRAY);
        title(server, f, Text.literal("GUERRE contre " + t.name).formatted(Formatting.RED, Formatting.BOLD), sub);
        title(server, t, Text.literal("GUERRE contre " + f.name).formatted(Formatting.RED, Formatting.BOLD), sub);
        return null;
    }

    private static void tick(MinecraftServer server) {
        FactionState s = FactionState.get(server);
        long now = System.currentTimeMillis();
        for (FactionState.War w : s.wars) {
            if (w.over) continue;
            Faction a = s.factions.get(w.attacker), d = s.factions.get(w.defender);
            if (a == null || d == null) {
                w.over = true;
                s.markDirty();
                continue;
            }
            if (!w.active && now >= w.starts) {
                w.active = true;
                s.markDirty();
                Msg.broadcast(server, Text.literal("⚔ La guerre entre « " + a.name + " » et « " + d.name + " » commence.").formatted(Formatting.RED));
            }
            if (!w.active) continue;
            boolean open = attackOpen(server, w);
            Boolean before = LAST_OPEN.put(w.id, open);
            if (before == null || before != open) {
                Text t = open ? Text.literal("⚔ Les attaques sont ouvertes : territoires et Cœur de faction ennemis sont vulnérables.").formatted(Formatting.RED)
                        : Text.literal("☾ La nuit suspend les combats. Les deux chefs peuvent accepter les combats de nuit (onglet Guerre).").formatted(Formatting.GRAY);
                Factions.tell(server, a, t);
                Factions.tell(server, d, t);
            }
        }
    }

    // ------------------------------------------------------------------ nuit, capitulation

    public static String setNight(ServerPlayerEntity p, boolean on) {
        MinecraftServer server = p.getServer();
        FactionState s = FactionState.get(server);
        Faction f = s.of(p.getUuid());
        if (f == null || !Factions.isChief(f, p.getUuid())) return "Seul le chef décide des combats de nuit.";
        FactionState.War w = s.warOf(f.id);
        if (w == null) return "Ta faction n'est pas en guerre.";
        if (w.attacker == f.id) w.nightA = on;
        else w.nightD = on;
        s.markDirty();
        Faction o = s.factions.get(w.other(f.id));
        Text t = Text.literal("Combats de nuit : « " + f.name + " » " + (on ? "accepte" : "refuse") + ". "
                + (w.nightA && w.nightD ? "Les deux chefs sont d'accord : on se bat aussi la nuit." : "Il faut l'accord des deux chefs.")).formatted(Formatting.GOLD);
        Factions.tell(server, f, t);
        if (o != null) Factions.tell(server, o, t);
        return null;
    }

    public static String offerCapitulation(ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        FactionState s = FactionState.get(server);
        Faction f = s.of(p.getUuid());
        if (f == null || !Factions.isChief(f, p.getUuid())) return "Seul le chef propose une capitulation.";
        FactionState.War w = s.warOf(f.id);
        if (w == null) return "Ta faction n'est pas en guerre.";
        w.offer = f.id;
        s.markDirty();
        Faction o = s.factions.get(w.other(f.id));
        f.log("Capitulation proposée par " + p.getGameProfile().getName() + ".");
        if (o != null) {
            o.log("« " + f.name + " » propose de capituler.");
            Factions.tell(server, o, Text.literal("« " + f.name + " » propose de capituler. Votre chef peut l'accepter (menu B › Faction › Guerre).").formatted(Formatting.GOLD));
        }
        Factions.tell(server, f, Text.literal("Capitulation proposée : le chef adverse doit l'accepter.").formatted(Formatting.GRAY));
        return null;
    }

    public static String answerCapitulation(ServerPlayerEntity p, boolean accept) {
        MinecraftServer server = p.getServer();
        FactionState s = FactionState.get(server);
        Faction f = s.of(p.getUuid());
        if (f == null || !Factions.isChief(f, p.getUuid())) return "Seul le chef accepte une capitulation.";
        FactionState.War w = s.warOf(f.id);
        if (w == null || w.offer == 0 || w.offer == f.id) return "Aucune capitulation à accepter.";
        Faction loser = s.factions.get(w.offer);
        if (!accept) {
            w.offer = 0;
            s.markDirty();
            if (loser != null) Factions.tell(server, loser, Text.literal("« " + f.name + " » refuse votre capitulation : la guerre continue.").formatted(Formatting.RED));
            return null;
        }
        if (loser == null) return "Faction inconnue.";
        end(server, w, f, loser, "capitulation de « " + loser.name + " »");
        return null;
    }

    // ------------------------------------------------------------------ Cœur de faction

    public static void hitHeart(ServerPlayerEntity p, Faction def) {
        MinecraftServer server = p.getServer();
        Faction att = Factions.of(p);
        if (att != null && att.id == def.id) {
            p.sendMessage(Text.literal("Le Cœur de ta faction (clic droit : coffre commun).").formatted(Formatting.GRAY), true);
            return;
        }
        if (!canAttack(server, att, def)) {
            p.sendMessage(Text.literal("Le Cœur ne se frappe que pendant une guerre, aux heures d'attaque.").formatted(Formatting.RED), true);
            return;
        }
        if (Territories.recentlyHit(p, 8)) return;
        FactionConfig.Data c = FactionConfig.get();
        def.heartHp = Math.max(0, def.heartHp - 1);
        def.lastHeartHit = System.currentTimeMillis();
        FactionState.get(server).markDirty();
        ServerWorld w = p.getServerWorld();
        w.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, def.heart.getX() + 0.5, def.heart.getY() + 0.8, def.heart.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.1);
        w.playSound(null, def.heart, SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.BLOCKS, 0.7f, 0.6f + 0.8f * def.heartHp / c.coeurPointsDeVie);
        p.sendMessage(Text.literal("Cœur de « " + def.name + " » : " + def.heartHp + " / " + c.coeurPointsDeVie).formatted(Formatting.RED), true);
        long now = System.currentTimeMillis();
        Long last = LAST_HEART_ALERT.get(def.id);
        if (last == null || now - last > 20_000) {
            LAST_HEART_ALERT.put(def.id, now);
            Factions.tell(server, def, Text.literal("⚠ Votre Cœur de faction est attaqué par « " + att.name + " » ! (" + def.heartHp + " / "
                    + c.coeurPointsDeVie + ")").formatted(Formatting.RED, Formatting.BOLD));
        }
        if (def.heartHp <= 0) {
            FactionState.War war = FactionState.get(server).warBetween(att.id, def.id);
            w.playSound(null, def.heart, SoundEvents.ENTITY_WITHER_DEATH, SoundCategory.BLOCKS, 1f, 1.2f);
            w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, def.heart.getX() + 0.5, def.heart.getY() + 0.5, def.heart.getZ() + 0.5, 1, 0, 0, 0, 0);
            if (war != null) end(server, war, att, def, "Cœur de « " + def.name + " » brisé par " + p.getGameProfile().getName());
        }
    }

    // ------------------------------------------------------------------ fin de guerre

    public static void end(MinecraftServer server, FactionState.War w, Faction winner, Faction loser, String reason) {
        FactionState s = FactionState.get(server);
        FactionConfig.Data c = FactionConfig.get();
        long now = System.currentTimeMillis();
        w.over = true;
        w.active = false;
        w.ended = now;
        w.winner = winner.id;
        w.reason = reason;
        LAST_OPEN.remove(w.id);

        winner.warsWon++;
        winner.points += c.pointsVictoire;
        Factions.addXp(server, winner, c.xpGuerreGagnee, "victoire");
        loser.warsLost++;
        loser.points = Math.max(0, loser.points - c.pointsDefaite);
        int lostLevels = Factions.loseXp(loser, c.perteXpDefaite);
        loser.heartHp = c.coeurPointsDeVie;

        Faction.Territory moved = loser.territories.stream().filter(t -> t.id != loser.baseTerritory)
                .max(Comparator.comparingLong(t -> t.created)).orElse(null);
        if (moved != null) {
            loser.territories.remove(moved);
            moved.created = now;
            winner.territories.add(moved);
        }
        long peace = now + (long) (c.paixJours * 86_400_000L);
        winner.peaceUntil = peace;
        loser.peaceUntil = peace;
        s.markDirty();

        String terr = moved == null ? "aucun territoire à céder" : "territoire " + moved.center.getX() + " / " + moved.center.getZ() + " cédé";
        winner.log("Victoire contre « " + loser.name + " » (" + reason + ") : " + terr + ", +" + c.pointsVictoire + " points.");
        loser.log("Défaite contre « " + winner.name + " » (" + reason + ") : " + terr + ", −" + Math.round(c.perteXpDefaite * 100) + " % d'XP"
                + (lostLevels > 0 ? " (un niveau perdu)" : "") + ", −" + c.pointsDefaite + " points.");
        Factions.journal(server, winner, "guerre_fin", "vainqueur", winner.name, "vaincu", loser.name, "raison", reason,
                "territoire", moved == null ? "" : moved.center.toShortString(), "niveauxPerdus", lostLevels);
        Msg.broadcast(server, Text.literal("⚔ Fin de la guerre : « " + winner.name + " » l'emporte sur « " + loser.name + " » (" + reason + "). "
                + terr + ". Paix obligatoire de " + Msg.duration((peace - now) / 1000) + ".").formatted(Formatting.GOLD, Formatting.BOLD));
        title(server, winner, Text.literal("VICTOIRE").formatted(Formatting.GOLD, Formatting.BOLD), Text.literal(reason).formatted(Formatting.GRAY));
        title(server, loser, Text.literal("DÉFAITE").formatted(Formatting.DARK_RED, Formatting.BOLD), Text.literal(reason).formatted(Formatting.GRAY));
    }

    /** GM : fin sans vainqueur ni pertes. */
    public static void gmEnd(MinecraftServer server, FactionState.War w) {
        w.over = true;
        w.active = false;
        w.ended = System.currentTimeMillis();
        w.reason = "arrêtée par le GM";
        LAST_OPEN.remove(w.id);
        FactionState.get(server).markDirty();
        FactionState s = FactionState.get(server);
        Faction a = s.factions.get(w.attacker), d = s.factions.get(w.defender);
        if (a != null && d != null) Msg.broadcast(server, Text.literal("⚔ La guerre entre « " + a.name + " » et « " + d.name + " » est terminée.").formatted(Formatting.GRAY));
    }

    // ------------------------------------------------------------------ incidents

    /** Incident entre deux factions (hors guerre) : signalé aux chefs et officiers connectés, journalisé. Pas de guerre automatique. */
    public static void incident(MinecraftServer server, Faction victim, Faction offender, String what) {
        if (victim == null || offender == null || victim.id == offender.id) return;
        String key = Math.min(victim.id, offender.id) + "-" + Math.max(victim.id, offender.id);
        long now = System.currentTimeMillis();
        Long last = LAST_INCIDENT.get(key);
        if (last != null && now - last < FactionConfig.get().incidentIntervalleMinutes * 60_000L) return;
        LAST_INCIDENT.put(key, now);
        victim.log("Incident avec « " + offender.name + " » : " + what + ".");
        offender.log("Incident avec « " + victim.name + " » : " + what + ".");
        FactionState.get(server).markDirty();
        fr.serveur.rpg.sombre.Journal.write(server, "factions", "incident", "victime", victim.name, "auteur", offender.name, "detail", what);
        Text t = Text.literal("⚠ Incident entre « " + victim.name + " » et « " + offender.name + " » : " + what
                + ". Seul le chef peut déclarer la guerre.").formatted(Formatting.GOLD);
        for (Faction f : new Faction[]{victim, offender}) {
            for (Faction.Member m : f.members.values()) {
                if (m.rank != Faction.Rank.CHEF && m.rank != Faction.Rank.OFFICIER) continue;
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(m.id);
                if (p != null) Msg.send(p, t);
            }
        }
    }

    /** Résumé lisible d'une guerre pour l'interface. */
    public static String status(MinecraftServer server, FactionState.War w) {
        if (w.over) return "terminée";
        if (!w.active) return "commence dans " + Msg.duration(Math.max(0, (w.starts - System.currentTimeMillis()) / 1000));
        return attackOpen(server, w) ? "attaques ouvertes" : "combats suspendus (nuit)";
    }

    static String money(long v) {
        return Bank.format(v);
    }
}
