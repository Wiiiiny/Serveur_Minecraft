package fr.serveur.rpg.data;

import net.minecraft.nbt.NbtCompound;

/** Une quête du jour, générée automatiquement et validée toute seule. */
public final class DailyQuest {
    /** « casser », « tuer » ou « obscure ». */
    public String kind = "";
    /** Bloc ou entité visé (id, #tag, ou « *hostile »). */
    public String target = "";
    /** Clé de traduction du nom affiché (bloc ou créature). */
    public String nameKey = "";
    public boolean variants;
    public int count, progress, xp, coins;
    public boolean done;

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putString("kind", kind);
        n.putString("target", target);
        n.putString("nameKey", nameKey);
        n.putBoolean("variants", variants);
        n.putInt("count", count);
        n.putInt("progress", progress);
        n.putInt("xp", xp);
        n.putInt("coins", coins);
        n.putBoolean("done", done);
        return n;
    }

    public static DailyQuest fromNbt(NbtCompound n) {
        DailyQuest q = new DailyQuest();
        q.kind = n.getString("kind");
        q.target = n.getString("target");
        q.nameKey = n.getString("nameKey");
        q.variants = n.getBoolean("variants");
        q.count = n.getInt("count");
        q.progress = n.getInt("progress");
        q.xp = n.getInt("xp");
        q.coins = n.getInt("coins");
        q.done = n.getBoolean("done");
        return q;
    }
}
