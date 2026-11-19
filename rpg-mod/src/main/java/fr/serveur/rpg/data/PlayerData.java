package fr.serveur.rpg.data;

import fr.serveur.rpg.jobs.Job;
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
    /** Solde bancaire, en poudres de glowstone. */
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
        return d;
    }
}
