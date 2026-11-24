package fr.serveur.rpg.sombre;

import fr.serveur.rpg.Msg;
import fr.serveur.rpg.data.PlayerData;
import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.economy.Bank;
import fr.serveur.rpg.economy.Economy;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Niveau de base du personnage, indépendant des métiers et de l'Obscurcie (pas de classe : progression libre).
 * XP : monstres, quêtes, coffres cachés, découvertes, niveaux de métier et une part de l'XP de métier.
 * Mort : perte d'une part de l'XP du niveau en cours, jamais d'un niveau.
 */
public final class Personnage {
    private Personnage() {}

    public static int xpNeeded(int level) {
        SombreConfig.Personnage c = SombreConfig.get().personnage;
        return Math.max(1, c.xpBase + c.xpParNiveau * (level - 1));
    }

    public static int maxLevel() {
        return Math.max(1, SombreConfig.get().personnage.niveauMax);
    }

    /** Ajoute de l'XP (les fractions s'accumulent). {@code raison} non nulle : petit message dans le chat. */
    public static void addXp(ServerPlayerEntity p, double amount, String raison) {
        if (amount <= 0) return;
        RpgState state = RpgState.get(p.getServer());
        PlayerData d = state.player(p);
        int max = maxLevel();
        if (d.baseLevel >= max) {
            d.baseXp = 0;
            return;
        }
        d.baseXpFrac += amount;
        int whole = (int) Math.floor(d.baseXpFrac + 1e-9);
        state.markDirty();
        if (whole <= 0) return;
        d.baseXpFrac = Math.max(0, d.baseXpFrac - whole);
        d.baseXp += whole;
        while (d.baseLevel < max && d.baseXp >= xpNeeded(d.baseLevel)) {
            d.baseXp -= xpNeeded(d.baseLevel);
            d.baseLevel++;
            levelUp(p, d.baseLevel);
        }
        if (d.baseLevel >= max) d.baseXp = 0;
        if (raison != null) {
            Msg.send(p, Text.literal("+" + whole + " XP de personnage").formatted(Formatting.AQUA)
                    .append(Text.literal(" (" + raison + ")").formatted(Formatting.GRAY)));
        }
    }

    private static void levelUp(ServerPlayerEntity p, int level) {
        p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("Niveau " + level).formatted(Formatting.AQUA, Formatting.BOLD)));
        p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("Ton personnage grandit").formatted(Formatting.GRAY)));
        p.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1f, 0.8f);
        long coins = Economy.earn(p.getServer(), p.getUuid(), Economy.Source.NIVEAU,
                (double) SombreConfig.get().personnage.piecesParNiveau * level, "niveau " + level);
        Msg.send(p, Text.literal("Ton personnage passe au ").append(Text.literal("niveau " + level).formatted(Formatting.AQUA))
                .append(coins > 0 ? Text.literal(" · +" + Bank.format(coins)).formatted(Formatting.GOLD) : Text.literal(" !")));
        if (level % 10 == 0) {
            Msg.broadcast(p.getServer(), Text.literal(p.getGameProfile().getName() + " atteint le niveau " + level + " !").formatted(Formatting.AQUA));
        }
        Journal.write(p.getServer(), "recompenses", "niveau_personnage", "joueur", p.getGameProfile().getName(), "niveau", level, "pieces", coins);
    }

    /** Mort : perte de {@code pertePourcentMort} % de l'XP du niveau en cours (sans redescendre de niveau). */
    public static void onDeath(ServerPlayerEntity p, PlayerData d) {
        double pct = SombreConfig.get().personnage.pertePourcentMort;
        int lost = (int) Math.floor(d.baseXp * pct / 100.0);
        if (lost <= 0) return;
        d.baseXp -= lost;
        d.baseXpFrac = 0;
        Msg.send(p, Text.literal("La mort t'a coûté " + lost + " XP de personnage (niveau " + d.baseLevel + " conservé).").formatted(Formatting.GRAY));
    }

    /** GM : fixe le niveau (XP remise à zéro). */
    public static void set(PlayerData d, int level) {
        d.baseLevel = Math.max(1, Math.min(maxLevel(), level));
        d.baseXp = 0;
        d.baseXpFrac = 0;
    }
}
