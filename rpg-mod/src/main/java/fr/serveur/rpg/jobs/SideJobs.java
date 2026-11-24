package fr.serveur.rpg.jobs;

import fr.serveur.rpg.Msg;
import fr.serveur.rpg.data.PlayerData;
import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.economy.Bank;
import fr.serveur.rpg.mining.HarvestRules;
import fr.serveur.rpg.world.TravelBeacon;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Métier supplémentaire : choix (le premier est gratuit, puis payant), XP et niveaux,
 * plafond journalier de l'XP de pose. Les règles propres à chaque métier sont dans {@link SideJobRules}.
 */
public final class SideJobs {
    private SideJobs() {}

    /** @return null si c'est fait, sinon la raison du refus. */
    public static String choose(ServerPlayerEntity player, SideJob job) {
        RpgState state = RpgState.get(player.getServer());
        PlayerData data = state.player(player);
        if (data.sideJob == job) return "Tu es déjà " + job.displayName + ".";
        boolean free = !data.sideChosenOnce;
        int price = SideJobConfig.data().prixChangement;
        if (!free) {
            if (data.money < price) return "Changer de métier supplémentaire coûte " + Bank.format(price) + " (en banque).";
            String e = fr.serveur.rpg.economy.Economy.spend(player.getServer(), player.getUuid(),
                    fr.serveur.rpg.economy.Economy.Source.CHANGEMENT_METIER, price, "vers " + job.displayName);
            if (e != null) return e;
        }
        apply(player, data, job);
        data.sideChosenOnce = true;
        state.markDirty();
        Msg.send(player, Text.literal("Ton métier supplémentaire est maintenant ").append(Text.literal(job.displayName).formatted(job.color))
                .append(free ? " !" : " (−" + Bank.format(price) + ").")
                .append(job == SideJob.VOYAGEUR ? Text.literal(" Ta balise est dans ton inventaire.").formatted(Formatting.AQUA) : Text.empty()));
        return null;
    }

    /**
     * Change le métier supplémentaire (joueur ou GM, sans paiement). {@code target} peut être null si le joueur
     * est hors ligne : la balise sera alors donnée ou reprise à sa prochaine connexion.
     */
    public static void apply(ServerPlayerEntity target, PlayerData data, SideJob job) {
        data.sideJob = job;
        if (job != null) data.side();
        if (target != null) TravelBeacon.syncInventory(target, data);
    }

    public static boolean is(ServerPlayerEntity player, SideJob job) {
        return RpgState.get(player.getServer()).player(player).sideJob == job;
    }

    /** Niveau du joueur dans ce métier s'il l'exerce, sinon 0. */
    public static int level(ServerPlayerEntity player, SideJob job) {
        return RpgState.get(player.getServer()).player(player).sideLevel(job);
    }

    /** Le joueur échappe aux règles des métiers (créatif, spectateur, /gm bypass). */
    public static boolean exempt(ServerPlayerEntity player) {
        return HarvestRules.isExempt(player);
    }

    /** XP gagnée par une action du métier supplémentaire : le métier paie aussi quelques pièces. */
    public static void addActionXp(ServerPlayerEntity player, SideJob job, int amount) {
        if (amount <= 0 || !is(player, job)) return;
        long coins = fr.serveur.rpg.economy.Economy.payJob(player, amount);
        addXp(player, job, amount, false, coins);
    }

    /** Ajoute de l'XP au métier supplémentaire, s'il est bien celui que le joueur exerce. */
    public static void addXp(ServerPlayerEntity player, SideJob job, int amount, boolean silent) {
        addXp(player, job, amount, silent, 0);
    }

    private static void addXp(ServerPlayerEntity player, SideJob job, int amount, boolean silent, long coins) {
        if (amount <= 0) return;
        RpgState state = RpgState.get(player.getServer());
        PlayerData data = state.player(player);
        if (data.sideJob != job) return;
        PlayerData.JobProgress p = data.side();
        fr.serveur.rpg.sombre.MondeSombre.onJobXp(player, amount);
        if (p.level >= Job.MAX_LEVEL) {
            if (!silent && coins > 0) player.sendMessage(Text.literal(job.displayName + " niv. MAX").formatted(job.color)
                    .append(fr.serveur.rpg.economy.Economy.paySuffix(coins)), true);
            return;
        }
        p.xp += amount;
        while (p.level < Job.MAX_LEVEL && p.xp >= JobConfig.xpNeeded(p.level)) {
            p.xp -= JobConfig.xpNeeded(p.level);
            p.level++;
            onLevelUp(player, job, p.level);
            fr.serveur.rpg.sombre.MondeSombre.onJobLevel(player);
        }
        if (p.level >= Job.MAX_LEVEL) p.xp = 0;
        state.markDirty();
        if (!silent) player.sendMessage(xpLine(job, p, amount).copy().append(fr.serveur.rpg.economy.Economy.paySuffix(coins)), true);
    }

    /** « +3 XP Ingénieur niv. 4 (120/170) », pour la barre d'action. */
    public static Text xpLine(SideJob job, PlayerData.JobProgress p, int amount) {
        String bar = p.level >= Job.MAX_LEVEL ? "MAX" : p.xp + "/" + JobConfig.xpNeeded(p.level);
        return Text.literal("+" + amount + " XP ").formatted(Formatting.GREEN)
                .append(Text.literal(job.displayName + " niv. " + p.level + " (" + bar + ")").formatted(job.color));
    }

    private static void onLevelUp(ServerPlayerEntity player, SideJob job, int level) {
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("Niveau " + level).formatted(job.color, Formatting.BOLD)));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(job.displayName).formatted(Formatting.GRAY)));
        player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1f, 1.2f);
        Msg.send(player, Text.literal("Tu passes ").append(Text.literal(job.displayName + " niveau " + level).formatted(job.color)).append(" !"));
    }

    /** Règles du métier, lignes affichées au survol dans le menu B (calculées depuis la config). */
    public static java.util.List<Text> describe(SideJob job) {
        SideJobConfig.Data c = SideJobConfig.data();
        java.util.List<Text> lines = new java.util.ArrayList<>();
        switch (job) {
            case INGENIEUR, CONSTRUCTEUR -> {
                lines.add(Text.literal(job == SideJob.INGENIEUR ? "Lui seul pose ces blocs :" : "Lui seul pose ces blocs de prestige :"));
                for (JobConfig.Palier p : job == SideJob.INGENIEUR ? c.ingenieur : c.constructeur) {
                    var line = Text.literal("Niv. " + p.niveau + " : ");
                    // Noms des objets (« Poudre de redstone »), sans doublon (torche murale = torche).
                    java.util.Set<net.minecraft.item.Item> items = new java.util.LinkedHashSet<>();
                    for (String s : p.blocs) {
                        net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(s);
                        if (s.startsWith("#") || id == null || !net.minecraft.registry.Registries.BLOCK.containsId(id)) continue;
                        net.minecraft.item.Item item = net.minecraft.registry.Registries.BLOCK.get(id).asItem();
                        if (item != net.minecraft.item.Items.AIR) items.add(item);
                    }
                    int shown = 0;
                    for (net.minecraft.item.Item item : items) {
                        if (shown == 3) break;
                        if (shown > 0) line.append(", ");
                        line.append(item.getName());
                        shown++;
                    }
                    if (items.size() > shown) line.append(" et " + (items.size() - shown) + " autres");
                    lines.add(line);
                }
                if (job == SideJob.CONSTRUCTEUR) lines.add(Text.literal("Tout autre bloc posé : +" + c.constructeurXpBlocOrdinaire + " XP"));
                lines.add(Text.literal("XP de pose : " + c.plafondXpPoseJour + " max par jour"));
            }
            case ENCHANTEUR -> {
                lines.add(Text.literal("Lui seul enchante (table, livres sur l'enclume)."));
                lines.add(Text.literal("Table : ligne 2 au niv. " + c.enchanteurNiveauLigne2 + ", ligne 3 au niv. " + c.enchanteurNiveauLigne3));
                lines.add(Text.literal("XP : enchanter, livres trouvés dans les coffres (+" + c.enchanteurXpLivreTrouve + "),"));
                lines.add(Text.literal("troc avec les villageois (+" + c.enchanteurXpTroc + "), enclume (+" + c.enchanteurXpEnclume + ")"));
            }
            case VOYAGEUR -> {
                lines.add(Text.literal("Balise : " + c.voyageurCanalisationSecondes + " s immobile pour franchir la limite du monde,"));
                lines.add(Text.literal("et autant pour revenir. Les autres joueurs sont bloqués à la limite."));
                lines.add(Text.literal("Recharge : " + fmt(c.voyageurRechargeMinutes) + " min, −" + fmt(c.voyageurRechargeMoinsParNiveau) + " min par niveau (min. 5)"));
                lines.add(Text.literal("XP : chaque saut (+" + c.voyageurXpSaut + "), 1 XP tous les " + c.voyageurBlocsParXp + " blocs au-delà"));
            }
            case PACIFICATEUR -> {
                lines.add(Text.literal("Monstre tué de jour : XP et prime de " + c.pacificateurPrime + " pièce (+1 tous les "
                        + c.pacificateurPrimePlusTousLesNiveaux + " niv.)"));
                lines.add(Text.literal("Pendant un événement : prime ×" + fmt(c.pacificateurMultiplicateurEvenement)
                        + " · " + c.pacificateurPlafondPrimesJour + " pièces max par jour"));
                lines.add(Text.literal("Monstre tué de nuit : pas de prime, amende de " + c.pacificateurAmendeNuit + " pièces"));
            }
            case ALCHIMISTE -> {
                lines.add(Text.literal("Lui seul utilise l'alambic."));
                lines.add(Text.literal("Durée niv. " + c.alchimisteNiveauDuree + " · Puissance niv. " + c.alchimisteNiveauPuissance
                        + " · Jetables niv. " + c.alchimisteNiveauJetable + " · Persistantes niv. " + c.alchimisteNiveauPersistante));
                lines.add(Text.literal("XP : chaque potion brassée (plus pour les jetables et persistantes)"));
            }
            case PECHEUR -> {
                lines.add(Text.literal("XP : poissons, trésors (+" + c.pecheurXpTresor + "), animaux chassés"));
                lines.add(Text.literal("Appât +1 aux niv. " + join(c.pecheurNiveauxAppat) + " · Chance de la mer +1 aux niv. " + join(c.pecheurNiveauxChance)));
            }
        }
        return lines;
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v).replace('.', ',');
    }

    private static String join(java.util.List<Integer> l) {
        return l == null || l.isEmpty() ? "—" : String.join(", ", l.stream().map(String::valueOf).toList());
    }

    /**
     * Applique le plafond journalier de l'XP gagnée en posant des blocs.
     * @return l'XP réellement accordée (0 si le plafond est atteint).
     */
    public static int capPlaceXp(ServerPlayerEntity player, PlayerData data, int amount) {
        data.rollDailyCaps();
        int cap = SideJobConfig.data().plafondXpPoseJour;
        int allowed = Math.max(0, cap - data.placeXpToday);
        int give = Math.min(amount, allowed);
        data.placeXpToday += give;
        if (give < amount && allowed > 0) {
            Msg.send(player, Text.literal("Plafond du jour atteint : poser des blocs ne rapporte plus d'XP avant demain.").formatted(Formatting.GRAY));
        }
        return give;
    }
}
