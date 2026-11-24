package fr.serveur.rpg.data;

import fr.serveur.rpg.jobs.Job;
import fr.serveur.rpg.jobs.SideJob;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Tout ce qui concerne un joueur et doit survivre aux redémarrages. */
public final class PlayerData {
    public String name = "?";
    /** Métier actif (null = pas encore de métier). */
    public Job job;
    /** Progression conservée par métier (si le GM fait changer de métier puis revenir). */
    public final Map<Job, JobProgress> progress = new EnumMap<>(Job.class);
    public final List<Quest> quests = new ArrayList<>();
    /** Solde bancaire, en Pièces d'or. */
    public long money;
    /** La Pioche des ténèbres de départ a-t-elle été donnée ? */
    public boolean starterGiven;
    /** Messages à afficher à la prochaine connexion (ventes faites hors ligne...). */
    public final List<String> pendingMessages = new ArrayList<>();
    /** Objets mis de côté à la mort (rendus à la réapparition). */
    public final List<NbtCompound> keptItems = new ArrayList<>();
    /** Inventaire complet gardé lors d'une mort de nuit (rendu à la réapparition). */
    public NbtList keptInventory;
    public int keptXpLevel, keptXpTotal;
    public float keptXpProgress;
    /** Mort de jour : attend la nuit (ou une Larme de vie) pour réapparaître. */
    public boolean awaitingRevive;
    // Statistiques (classements)
    public int deaths, pvpKills, mobKills;
    /** Quêtes du jour (date au format AAAA-MM-JJ). */
    public String dailyDate = "";
    public final List<DailyQuest> daily = new ArrayList<>();
    /** Métier supplémentaire (null = aucun) et progression conservée pour chacun. */
    public SideJob sideJob;
    public final Map<SideJob, JobProgress> sideProgress = new EnumMap<>(SideJob.class);
    /** Le premier choix du métier supplémentaire est gratuit. */
    public boolean sideChosenOnce;
    /** Voyageur : la balise est rechargée à partir de cet instant (millisecondes). */
    public long beaconReadyAt;
    /** Plafonds journaliers (XP de pose, primes du Pacificateur) : remis à zéro chaque jour. */
    public String capDate = "";
    public int placeXpToday, bountyToday;
    /** Économie : gains du jour par source (avant rendement dégressif), virements du jour, centimes en attente. */
    public final Map<String, Double> earnedToday = new java.util.HashMap<>();
    public long transferredToday;
    public double moneyFraction;
    /** Compte gelé (détection d'anomalie ou GM) : plus aucune dépense ni virement. */
    public boolean frozen;
    /** Première et dernière connexion (millisecondes) : virements bloqués les 24 premières heures, joueurs actifs. */
    public long firstSeen, lastSeen;
    /** Raid : cycle dans lequel le joueur est entré ; affinité avec les voleurs ; compagnon (actif, fin de la pénalité). */
    public int raidCycle, thiefAffinity;
    public boolean companionDisabled;
    public long companionCooldownUntil;
    /** Objets à rendre à la prochaine connexion (annonce de l'HDV expirée...). */
    public final List<NbtCompound> mailbox = new ArrayList<>();
    /** Niveau du personnage (indépendant des métiers) ; Obscurcie (métier ouvert à tous, sans niveau maximum). */
    public int baseLevel = 1, baseXp;
    public double baseXpFrac;
    public int obsLevel = 1, obsXp;
    public double obsXpFrac;
    /** XP d'exploration nocturne gagnée aujourd'hui (plafonnée). */
    public int exploreXpToday;
    /** Objets déjà ramassés au moins une fois (identifiants) : la boutique ne vend que ceux-là. */
    public final java.util.Set<String> discovered = new java.util.HashSet<>();
    /** Lore personnel du personnage, écrit par le joueur ; lisible par les autres s'il est public. */
    public String lore = "";
    public boolean lorePublic;
    public int chestsOpened;

    public static final class JobProgress {
        public int level = 1;
        public int xp = 0;
        public int prestige = 0;
    }

    public static final class Quest {
        public int id;
        public String text;
        public boolean done;
    }

    public JobProgress current() {
        return job == null ? null : progress.computeIfAbsent(job, j -> new JobProgress());
    }

    /** Progression du métier supplémentaire actif (null si aucun). */
    public JobProgress side() {
        return sideJob == null ? null : sideProgress.computeIfAbsent(sideJob, j -> new JobProgress());
    }

    /** Niveau dans ce métier supplémentaire s'il est actif, sinon 0. */
    public int sideLevel(SideJob j) {
        return sideJob == j ? side().level : 0;
    }

    /** Remet les plafonds journaliers à zéro si la date a changé. */
    public void rollDailyCaps() {
        String today = java.time.LocalDate.now().toString();
        if (!today.equals(capDate)) {
            capDate = today;
            placeXpToday = 0;
            bountyToday = 0;
            earnedToday.clear();
            transferredToday = 0;
            exploreXpToday = 0;
        }
    }

    private static NbtCompound progressToNbt(JobProgress p) {
        NbtCompound c = new NbtCompound();
        c.putInt("level", p.level);
        c.putInt("xp", p.xp);
        c.putInt("prestige", p.prestige);
        return c;
    }

    private static JobProgress progressFromNbt(NbtCompound c) {
        JobProgress p = new JobProgress();
        p.level = Math.max(1, c.getInt("level"));
        p.xp = c.getInt("xp");
        p.prestige = c.getInt("prestige");
        return p;
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("name", name);
        if (job != null) nbt.putString("job", job.name());
        NbtCompound prog = new NbtCompound();
        progress.forEach((j, p) -> {
            NbtCompound c = new NbtCompound();
            c.putInt("level", p.level);
            c.putInt("xp", p.xp);
            c.putInt("prestige", p.prestige);
            prog.put(j.name(), c);
        });
        nbt.put("progress", prog);
        NbtList q = new NbtList();
        for (Quest quest : quests) {
            NbtCompound c = new NbtCompound();
            c.putInt("id", quest.id);
            c.putString("text", quest.text);
            c.putBoolean("done", quest.done);
            q.add(c);
        }
        nbt.put("quests", q);
        nbt.putLong("money", money);
        nbt.putBoolean("starterGiven", starterGiven);
        NbtList msgs = new NbtList();
        pendingMessages.forEach(m -> msgs.add(NbtString.of(m)));
        nbt.put("pendingMessages", msgs);
        NbtList kept = new NbtList();
        kept.addAll(keptItems);
        nbt.put("keptItems", kept);
        if (keptInventory != null) {
            nbt.put("keptInventory", keptInventory);
            nbt.putInt("keptXpLevel", keptXpLevel);
            nbt.putInt("keptXpTotal", keptXpTotal);
            nbt.putFloat("keptXpProgress", keptXpProgress);
        }
        nbt.putBoolean("awaitingRevive", awaitingRevive);
        nbt.putInt("deaths", deaths);
        nbt.putInt("pvpKills", pvpKills);
        nbt.putInt("mobKills", mobKills);
        nbt.putString("dailyDate", dailyDate);
        NbtList dq = new NbtList();
        daily.forEach(x -> dq.add(x.toNbt()));
        nbt.put("daily", dq);
        if (sideJob != null) nbt.putString("sideJob", sideJob.name());
        NbtCompound side = new NbtCompound();
        sideProgress.forEach((j, p) -> side.put(j.name(), progressToNbt(p)));
        nbt.put("sideProgress", side);
        nbt.putBoolean("sideChosenOnce", sideChosenOnce);
        nbt.putLong("beaconReadyAt", beaconReadyAt);
        nbt.putString("capDate", capDate);
        nbt.putInt("placeXpToday", placeXpToday);
        nbt.putInt("bountyToday", bountyToday);
        NbtCompound earned = new NbtCompound();
        earnedToday.forEach(earned::putDouble);
        nbt.put("earnedToday", earned);
        nbt.putLong("transferredToday", transferredToday);
        nbt.putInt("raidCycle", raidCycle);
        nbt.putInt("thiefAffinity", thiefAffinity);
        nbt.putBoolean("companionDisabled", companionDisabled);
        nbt.putLong("companionCooldownUntil", companionCooldownUntil);
        nbt.putDouble("moneyFraction", moneyFraction);
        nbt.putBoolean("frozen", frozen);
        nbt.putLong("firstSeen", firstSeen);
        nbt.putLong("lastSeen", lastSeen);
        NbtList mail = new NbtList();
        mail.addAll(mailbox);
        nbt.put("mailbox", mail);
        nbt.putInt("baseLevel", baseLevel);
        nbt.putInt("baseXp", baseXp);
        nbt.putDouble("baseXpFrac", baseXpFrac);
        nbt.putInt("obsLevel", obsLevel);
        nbt.putInt("obsXp", obsXp);
        nbt.putDouble("obsXpFrac", obsXpFrac);
        nbt.putInt("exploreXpToday", exploreXpToday);
        NbtList disc = new NbtList();
        discovered.forEach(x -> disc.add(NbtString.of(x)));
        nbt.put("discovered", disc);
        nbt.putString("lore", lore);
        nbt.putBoolean("lorePublic", lorePublic);
        nbt.putInt("chestsOpened", chestsOpened);
        return nbt;
    }

    public static PlayerData fromNbt(NbtCompound nbt) {
        PlayerData d = new PlayerData();
        d.name = nbt.getString("name");
        if (nbt.contains("job")) d.job = Job.parse(nbt.getString("job"));
        NbtCompound prog = nbt.getCompound("progress");
        for (String key : prog.getKeys()) {
            Job j = Job.parse(key);
            if (j == null) continue;
            NbtCompound c = prog.getCompound(key);
            JobProgress p = new JobProgress();
            p.level = Math.max(1, c.getInt("level"));
            p.xp = c.getInt("xp");
            p.prestige = c.getInt("prestige");
            d.progress.put(j, p);
        }
        NbtList q = nbt.getList("quests", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < q.size(); i++) {
            NbtCompound c = q.getCompound(i);
            Quest quest = new Quest();
            quest.id = c.getInt("id");
            quest.text = c.getString("text");
            quest.done = c.getBoolean("done");
            d.quests.add(quest);
        }
        d.money = nbt.getLong("money");
        d.starterGiven = nbt.getBoolean("starterGiven");
        NbtList msgs = nbt.getList("pendingMessages", NbtElement.STRING_TYPE);
        for (int i = 0; i < msgs.size(); i++) d.pendingMessages.add(msgs.getString(i));
        NbtList kept = nbt.getList("keptItems", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < kept.size(); i++) d.keptItems.add(kept.getCompound(i));
        if (nbt.contains("keptInventory")) {
            d.keptInventory = nbt.getList("keptInventory", NbtElement.COMPOUND_TYPE);
            d.keptXpLevel = nbt.getInt("keptXpLevel");
            d.keptXpTotal = nbt.getInt("keptXpTotal");
            d.keptXpProgress = nbt.getFloat("keptXpProgress");
        }
        d.awaitingRevive = nbt.getBoolean("awaitingRevive");
        d.deaths = nbt.getInt("deaths");
        d.pvpKills = nbt.getInt("pvpKills");
        d.mobKills = nbt.getInt("mobKills");
        d.dailyDate = nbt.getString("dailyDate");
        NbtList dq = nbt.getList("daily", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < dq.size(); i++) d.daily.add(DailyQuest.fromNbt(dq.getCompound(i)));
        if (nbt.contains("sideJob")) d.sideJob = SideJob.parse(nbt.getString("sideJob"));
        NbtCompound side = nbt.getCompound("sideProgress");
        for (String key : side.getKeys()) {
            SideJob j = SideJob.parse(key);
            if (j != null) d.sideProgress.put(j, progressFromNbt(side.getCompound(key)));
        }
        d.sideChosenOnce = nbt.getBoolean("sideChosenOnce") || d.sideJob != null;
        d.beaconReadyAt = nbt.getLong("beaconReadyAt");
        d.capDate = nbt.getString("capDate");
        d.placeXpToday = nbt.getInt("placeXpToday");
        d.bountyToday = nbt.getInt("bountyToday");
        NbtCompound earned = nbt.getCompound("earnedToday");
        for (String k : earned.getKeys()) d.earnedToday.put(k, earned.getDouble(k));
        d.transferredToday = nbt.getLong("transferredToday");
        d.raidCycle = nbt.getInt("raidCycle");
        d.thiefAffinity = nbt.getInt("thiefAffinity");
        d.companionDisabled = nbt.getBoolean("companionDisabled");
        d.companionCooldownUntil = nbt.getLong("companionCooldownUntil");
        d.moneyFraction = nbt.getDouble("moneyFraction");
        d.frozen = nbt.getBoolean("frozen");
        // Joueurs d'avant la mise à jour : considérés comme anciens (pas de délai de 24 h sur les virements).
        d.firstSeen = nbt.contains("firstSeen") ? nbt.getLong("firstSeen") : 1L;
        d.lastSeen = nbt.getLong("lastSeen");
        NbtList mail = nbt.getList("mailbox", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < mail.size(); i++) d.mailbox.add(mail.getCompound(i));
        d.baseLevel = Math.max(1, nbt.getInt("baseLevel"));
        d.baseXp = nbt.getInt("baseXp");
        d.baseXpFrac = nbt.getDouble("baseXpFrac");
        d.obsLevel = Math.max(1, nbt.getInt("obsLevel"));
        d.obsXp = nbt.getInt("obsXp");
        d.obsXpFrac = nbt.getDouble("obsXpFrac");
        d.exploreXpToday = nbt.getInt("exploreXpToday");
        NbtList disc = nbt.getList("discovered", NbtElement.STRING_TYPE);
        for (int i = 0; i < disc.size(); i++) d.discovered.add(disc.getString(i));
        d.lore = nbt.getString("lore");
        d.lorePublic = nbt.getBoolean("lorePublic");
        d.chestsOpened = nbt.getInt("chestsOpened");
        return d;
    }
}
