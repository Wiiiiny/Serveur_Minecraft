package fr.serveur.rpg.client;

import fr.serveur.rpg.jobs.Job;
import fr.serveur.rpg.jobs.SideJob;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Côté client : la ligne « Mineur · Ingénieur » à afficher sous le pseudo de chaque joueur. */
public final class NameTagCache {
    private static final Map<UUID, Text> TAGS = new HashMap<>();

    private NameTagCache() {}

    public static Text get(UUID player) {
        return TAGS.get(player);
    }

    static void update(NbtCompound nbt) {
        TAGS.clear();
        NbtList list = nbt.getList("tags", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            Job job = Job.parse(c.getString("j"));
            SideJob side = SideJob.parse(c.getString("s"));
            if (job == null && side == null) continue;
            MutableText t = Text.empty();
            if (job != null) t.append(Text.literal(job.displayName).formatted(job.color));
            if (job != null && side != null) t.append(Text.literal(" · ").formatted(Formatting.GRAY));
            if (side != null) t.append(Text.literal(side.displayName).formatted(side.color));
            TAGS.put(c.getUuid("u"), t);
        }
    }

    static void clear() {
        TAGS.clear();
    }
}
