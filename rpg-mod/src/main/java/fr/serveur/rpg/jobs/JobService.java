package fr.serveur.rpg.jobs;

import fr.serveur.rpg.Msg;
import fr.serveur.rpg.auto.AutoConfig;
import fr.serveur.rpg.auto.DailyQuests;
import fr.serveur.rpg.data.PlayerData;
import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.economy.Bank;
import fr.serveur.rpg.items.ModItems;
import fr.serveur.rpg.tools.OfficialTools;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Choix et changement de métier, sans intervention du GM :
 * le premier choix est gratuit, les changements suivants coûtent des pièces (réglable).
 * Le joueur reçoit automatiquement l'outil officiel en bois de son nouveau métier.
 */
public final class JobService {
    private JobService() {}

    /** @return null si c'est fait, sinon la raison du refus. */
    public static String choose(ServerPlayerEntity player, Job job) {
        RpgState state = RpgState.get(player.getServer());
        PlayerData data = state.player(player);
        if (data.job == job) return "Tu es déjà " + job.displayName + ".";
        boolean first = data.job == null;
        int price = AutoConfig.get().prixChangementMetier;
        if (!first) {
            if (data.money < price) return "Changer de métier coûte " + Bank.format(price) + " (en banque).";
            String e = fr.serveur.rpg.economy.Economy.spend(player.getServer(), player.getUuid(),
                    fr.serveur.rpg.economy.Economy.Source.CHANGEMENT_METIER, price, "vers " + job.displayName);
            if (e != null) return e;
        }
        data.job = job;
        data.current();
        state.markDirty();
        giveStarterTool(player, job);
        DailyQuests.regenerateJobQuests(player);
        Msg.send(player, Text.literal("Tu es maintenant ").append(Text.literal(job.displayName).formatted(job.color))
                .append(first ? " ! Ton outil en bois est dans ton inventaire." : " (−" + Bank.format(price) + ")."));
        return null;
    }

    /** Donne l'outil officiel en bois du métier si le joueur n'a aucun outil de ce métier sur lui. */
    public static void giveStarterTool(ServerPlayerEntity player, Job job) {
        RpgState state = RpgState.get(player.getServer());
        for (ItemStack s : player.getInventory().main) {
            if (OfficialTools.jobOf(s, state) == job) return;
        }
        ItemStack tool = OfficialTools.create(ModItems.OFFICIAL.get(job).get(ModItems.Tier.BOIS), job, state);
        if (!player.getInventory().insertStack(tool)) player.dropItem(tool, false);
    }

    public static Text describeError(String error) {
        return Text.literal(error).formatted(Formatting.RED);
    }
}
