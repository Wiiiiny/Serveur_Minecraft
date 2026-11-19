package fr.serveur.rpg.jobs;

import fr.serveur.rpg.Msg;
import fr.serveur.rpg.data.PlayerData;
import fr.serveur.rpg.data.RpgState;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** XP de métier, montée de niveau et prestige. */
public final class Progression {
    private Progression() {}

    /** Ajoute de l'XP au métier actif. {@code silent} = pas de message dans la barre d'action. */
    public static void addXp(ServerPlayerEntity player, int amount, boolean silent) {
        if (amount <= 0) return;
        RpgState state = RpgState.get(player.getServer());
        PlayerData data = state.player(player);
        PlayerData.JobProgress p = data.current();
        if (p == null) return;

        if (p.level >= Job.MAX_LEVEL) {
            if (!silent) player.sendMessage(Text.literal("Niveau max atteint — tape /prestige").formatted(Formatting.GOLD), true);
            return;
        }
        p.xp += amount;
        while (p.level < Job.MAX_LEVEL && p.xp >= JobConfig.xpNeeded(p.level)) {
            p.xp -= JobConfig.xpNeeded(p.level);
            p.level++;
            onLevelUp(player, data.job, p.level);
        }
        if (p.level >= Job.MAX_LEVEL) p.xp = 0;
        state.markDirty();

        if (!silent) {
            String bar = p.level >= Job.MAX_LEVEL ? "MAX" : p.xp + "/" + JobConfig.xpNeeded(p.level);
            player.sendMessage(Text.literal("+" + amount + " XP ").formatted(Formatting.GREEN)
                    .append(Text.literal(data.job.displayName + " niv. " + p.level + " (" + bar + ")").formatted(data.job.color)), true);
        }
    }

    private static void onLevelUp(ServerPlayerEntity player, Job job, int level) {
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("Niveau " + level).formatted(job.color, Formatting.BOLD)));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(job.displayName).formatted(Formatting.GRAY)));
        player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1f, 1f);
        Msg.send(player, Text.literal("Tu passes ").append(Text.literal(job.displayName + " niveau " + level).formatted(job.color))
                .append(level >= Job.MAX_LEVEL ? Text.literal(" ! Le prestige est disponible : /prestige").formatted(Formatting.GOLD) : Text.literal(" !")));
    }

    /** Remet le métier au niveau 1 et ajoute un prestige. */
    public static boolean prestige(ServerPlayerEntity player) {
        RpgState state = RpgState.get(player.getServer());
        PlayerData data = state.player(player);
        PlayerData.JobProgress p = data.current();
        if (p == null || p.level < Job.MAX_LEVEL) return false;
        p.level = 1;
        p.xp = 0;
        p.prestige++;
        state.markDirty();
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("Prestige " + p.prestige).formatted(Formatting.GOLD, Formatting.BOLD)));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(data.job.displayName + " — retour au niveau 1").formatted(Formatting.GRAY)));
        player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1f, 1f);
        Msg.broadcast(player.getServer(), Text.literal(player.getGameProfile().getName() + " atteint le prestige " + p.prestige
                + " en " + data.job.displayName + " !").formatted(Formatting.GOLD));
        return true;
    }
}
