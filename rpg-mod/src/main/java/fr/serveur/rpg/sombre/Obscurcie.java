package fr.serveur.rpg.sombre;

import fr.serveur.rpg.Msg;
import fr.serveur.rpg.data.PlayerData;
import fr.serveur.rpg.data.RpgState;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Métier d'Obscurcie : ouvert à tous dès le début, sans niveau maximum, distinct du niveau du personnage.
 * XP : collecte de ressources, combats de nuit, quêtes, exploration nocturne, utilisation d'objets (Éclat, détecteur,
 * coffres cachés) et une part de l'XP des métiers.
 * Paliers : tous les 5 niveaux, une chance sur deux d'obtenir un Éclat de Lumière (un seul tirage par palier) ;
 * tous les 10 niveaux, un détecteur de coffres cachés (2 minutes une fois activé, puis il disparaît).
 */
public final class Obscurcie {
    public static final Formatting COLOR = Formatting.DARK_PURPLE;

    private Obscurcie() {}

    public static int xpNeeded(int level) {
        SombreConfig.Obscurcie c = SombreConfig.get().obscurcie;
        return Math.max(1, c.xpBase + c.xpParNiveau * (level - 1));
    }

    /** Prochain niveau multiple de {@code step} strictement au-dessus de {@code level}. */
    public static int nextMilestone(int level, int step) {
        step = Math.max(1, step);
        return (level / step + 1) * step;
    }

    public static void addXp(ServerPlayerEntity p, double amount, boolean show) {
        if (amount <= 0) return;
        RpgState state = RpgState.get(p.getServer());
        PlayerData d = state.player(p);
        d.obsXpFrac += amount;
        int whole = (int) Math.floor(d.obsXpFrac + 1e-9);
        state.markDirty();
        if (whole <= 0) return;
        d.obsXpFrac = Math.max(0, d.obsXpFrac - whole);
        d.obsXp += whole;
        while (d.obsXp >= xpNeeded(d.obsLevel)) {
            d.obsXp -= xpNeeded(d.obsLevel);
            d.obsLevel++;
            levelUp(p, d.obsLevel);
        }
        if (show) {
            p.sendMessage(Text.literal("+" + whole + " Obscurcie").formatted(COLOR)
                    .append(Text.literal("  niv. " + d.obsLevel + " (" + d.obsXp + "/" + xpNeeded(d.obsLevel) + ")").formatted(Formatting.GRAY)), true);
        }
    }

    private static void levelUp(ServerPlayerEntity p, int level) {
        SombreConfig.Obscurcie c = SombreConfig.get().obscurcie;
        p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("Obscurcie " + level).formatted(COLOR, Formatting.BOLD)));
        p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("L'ombre te reconnaît").formatted(Formatting.GRAY)));
        p.playSound(SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK, SoundCategory.PLAYERS, 0.5f, 1.6f);
        Msg.send(p, Text.literal("Tu passes ").append(Text.literal("Obscurcie niveau " + level).formatted(COLOR)).append(" !"));
        String name = p.getGameProfile().getName();
        if (level % Math.max(1, c.palierEclat) == 0) {
            if (p.getRandom().nextDouble() < c.chanceEclat) {
                give(p, new ItemStack(SombreItems.ECLAT_LUMIERE));
                Msg.send(p, Text.literal("✦ Un Éclat de Lumière se forme dans ta main.").formatted(Formatting.YELLOW));
                Journal.write(p.getServer(), "recompenses", "eclat_obscurcie", "joueur", name, "niveau", level);
            } else {
                Msg.send(p, Text.literal("La lumière se refuse à toi pour ce palier.").formatted(Formatting.GRAY));
            }
        }
        if (level % Math.max(1, c.palierDetecteur) == 0) {
            give(p, new ItemStack(SombreItems.DETECTEUR));
            Msg.send(p, Text.literal("✦ Tu reçois un Détecteur d'ombre : active-le (clic droit) pour 2 minutes de traque des coffres cachés.")
                    .formatted(Formatting.LIGHT_PURPLE));
            Journal.write(p.getServer(), "recompenses", "detecteur_obscurcie", "joueur", name, "niveau", level);
        }
    }

    static void give(ServerPlayerEntity p, ItemStack stack) {
        if (!p.getInventory().insertStack(stack)) p.dropItem(stack, false);
    }

    /** GM : fixe le niveau (XP remise à zéro, sans distribuer les récompenses des paliers). */
    public static void set(PlayerData d, int level) {
        d.obsLevel = Math.max(1, level);
        d.obsXp = 0;
        d.obsXpFrac = 0;
    }
}
