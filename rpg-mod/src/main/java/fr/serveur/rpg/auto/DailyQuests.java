package fr.serveur.rpg.auto;

import fr.serveur.rpg.Msg;
import fr.serveur.rpg.blocks.ModBlocks;
import fr.serveur.rpg.data.DailyQuest;
import fr.serveur.rpg.data.PlayerData;
import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.economy.Bank;
import fr.serveur.rpg.jobs.Job;
import fr.serveur.rpg.jobs.JobConfig;
import fr.serveur.rpg.jobs.Progression;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.Monster;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Quêtes du jour : générées automatiquement pour chaque joueur (selon son métier et son niveau),
 * suivies toutes seules et récompensées dès qu'elles sont finies (XP de métier + pièces).
 * Elles se renouvellent chaque jour à minuit (heure du serveur).
 */
public final class DailyQuests {
    private static final Random RNG = Random.create();
    private static final String[][] KILL_TARGETS = {
            {"minecraft:zombie", "entity.minecraft.zombie"}, {"minecraft:skeleton", "entity.minecraft.skeleton"},
            {"minecraft:spider", "entity.minecraft.spider"}, {"minecraft:creeper", "entity.minecraft.creeper"},
            {"*hostile", "quest.survierpg.hostile"}};

    private DailyQuests() {}

    public static String today() {
        return LocalDate.now().toString();
    }

    /** Génère les quêtes du jour si la date a changé. */
    public static List<DailyQuest> ensure(ServerPlayerEntity player) {
        RpgState state = RpgState.get(player.getServer());
        PlayerData data = state.player(player);
        if (!today().equals(data.dailyDate)) {
            data.dailyDate = today();
            data.daily.clear();
            data.daily.addAll(generate(data));
            state.markDirty();
        }
        return data.daily;
    }

    /** Remplace les quêtes de métier pas encore finies (après un changement de métier). */
    public static void regenerateJobQuests(ServerPlayerEntity player) {
        RpgState state = RpgState.get(player.getServer());
        PlayerData data = state.player(player);
        ensure(player);
        List<DailyQuest> fresh = generate(data);
        for (int i = 0; i < data.daily.size(); i++) {
            DailyQuest q = data.daily.get(i);
            if (!q.done && !"obscure".equals(q.kind) && i < fresh.size()) data.daily.set(i, fresh.get(i));
        }
        state.markDirty();
    }

    private static List<DailyQuest> generate(PlayerData data) {
        int total = Math.max(1, AutoConfig.get().quetesParJour);
        double mult = AutoConfig.get().multiplicateurRecompenses;
        List<DailyQuest> out = new ArrayList<>();
        int jobQuests = data.job == null ? 0 : total - 1;

        if (data.job == Job.AVENTURIER) {
            List<String[]> targets = new ArrayList<>(List.of(KILL_TARGETS));
            Collections.shuffle(targets, new java.util.Random(RNG.nextLong()));
            for (int i = 0; i < jobQuests && i < targets.size(); i++) {
                DailyQuest q = new DailyQuest();
                q.kind = "tuer";
                q.target = targets.get(i)[0];
                q.nameKey = targets.get(i)[1];
                q.count = 5 + RNG.nextInt(8);
                q.xp = q.count * 6;
                q.coins = (int) Math.max(2, Math.round(q.count * 1.2 * mult));
                out.add(q);
            }
        } else if (data.job != null) {
            int level = data.current().level;
            // On tire d'abord un palier débloqué, puis un bloc de ce palier : les minerais sortent aussi souvent que la pierre.
            List<JobConfig.Palier> paliers = new ArrayList<>();
            for (JobConfig.Palier p : JobConfig.data().metiers.getOrDefault(data.job.name(), List.of())) {
                if (p.niveau <= level && p.xp > 0 && p.blocs.stream().anyMatch(b -> firstBlock(b) != null)) paliers.add(p);
            }
            Collections.shuffle(paliers, new java.util.Random(RNG.nextLong()));
            List<String> used = new ArrayList<>();
            for (int k = 0; k < jobQuests && !paliers.isEmpty(); k++) {
                JobConfig.Palier p = paliers.get(k % paliers.size());
                // Seulement les 3 premiers blocs du palier : les plus courants (pierre, chêne, blé, charbon...).
                List<String> choices = p.blocs.stream().filter(b -> firstBlock(b) != null && !used.contains(b)).limit(3).toList();
                if (choices.isEmpty()) continue;
                String entry = choices.get(RNG.nextInt(choices.size()));
                used.add(entry);
                int xp = p.xp;
                DailyQuest q = new DailyQuest();
                q.kind = "casser";
                q.target = entry;
                q.nameKey = firstBlock(entry).getTranslationKey();
                q.variants = entry.startsWith("#");
                q.count = (int) Math.max(3, Math.round(48.0 / xp));
                q.xp = q.count * xp * 2;
                q.coins = (int) Math.max(2, Math.round(q.count * xp / 6.0 * mult));
                out.add(q);
            }
        }

        DailyQuest obscure = new DailyQuest();
        obscure.kind = "obscure";
        obscure.target = "survierpg:pierre_obscure";
        obscure.nameKey = ModBlocks.PIERRE_OBSCURE.getTranslationKey();
        obscure.count = 8 + RNG.nextInt(9);
        obscure.xp = 0;
        obscure.coins = (int) Math.max(2, Math.round(obscure.count / 2.0 * mult));
        out.add(obscure);
        return out;
    }

    /** Icône affichée dans l'interface pour une quête. */
    public static net.minecraft.item.ItemStack iconFor(DailyQuest q) {
        switch (q.kind) {
            case "obscure":
                return new net.minecraft.item.ItemStack(ModBlocks.PIERRE_OBSCURE_ITEM);
            case "casser": {
                Block b = firstBlock(q.target);
                net.minecraft.item.Item item = b == null ? net.minecraft.item.Items.AIR : b.asItem();
                return new net.minecraft.item.ItemStack(item == net.minecraft.item.Items.AIR ? net.minecraft.item.Items.PAPER : item);
            }
            case "tuer": {
                if ("*hostile".equals(q.target)) return new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_SWORD);
                var type = EntityType.get(q.target).orElse(null);
                var egg = type == null ? null : net.minecraft.item.SpawnEggItem.forEntity(type);
                return new net.minecraft.item.ItemStack(egg == null ? net.minecraft.item.Items.BONE : egg);
            }
            default:
                return new net.minecraft.item.ItemStack(net.minecraft.item.Items.PAPER);
        }
    }

    private static Block firstBlock(String entry) {
        try {
            if (entry.startsWith("#")) {
                for (RegistryEntry<Block> e : Registries.BLOCK.iterateEntries(TagKey.of(RegistryKeys.BLOCK, new Identifier(entry.substring(1))))) {
                    return e.value();
                }
                return null;
            }
            Identifier id = new Identifier(entry);
            return Registries.BLOCK.containsId(id) ? Registries.BLOCK.get(id) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean matchesBlock(String target, Block block) {
        if (target.startsWith("#")) {
            return block.getDefaultState().isIn(TagKey.of(RegistryKeys.BLOCK, new Identifier(target.substring(1))));
        }
        return Registries.BLOCK.getId(block).toString().equals(target);
    }

    // ------------------------------------------------------------------ progression

    public static void onHarvest(ServerPlayerEntity player, Block block) {
        for (DailyQuest q : ensure(player)) {
            if (!q.done && "casser".equals(q.kind) && matchesBlock(q.target, block)) advance(player, q);
        }
    }

    public static void onObscure(ServerPlayerEntity player) {
        for (DailyQuest q : ensure(player)) if (!q.done && "obscure".equals(q.kind)) advance(player, q);
    }

    public static void onKill(ServerPlayerEntity player, Entity killed) {
        String id = EntityType.getId(killed.getType()).toString();
        for (DailyQuest q : ensure(player)) {
            if (q.done || !"tuer".equals(q.kind)) continue;
            if ("*hostile".equals(q.target) ? killed instanceof Monster : q.target.equals(id)) advance(player, q);
        }
    }

    private static void advance(ServerPlayerEntity player, DailyQuest q) {
        RpgState state = RpgState.get(player.getServer());
        q.progress++;
        state.markDirty();
        if (q.progress < q.count) return;
        q.done = true;
        PlayerData data = state.player(player);
        int prestige = data.current() == null ? 0 : data.current().prestige;
        long coins = Math.round(q.coins * (1 + state.prestigeBonusPercent * prestige / 100.0));
        data.money += coins;
        if (q.xp > 0 && data.job != null) Progression.addXp(player, q.xp, true);
        player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.7f, 1.2f);
        Msg.send(player, Text.literal("Quête du jour terminée ! ").formatted(Formatting.GOLD)
                .append(Text.literal("+" + Bank.format(coins) + (q.xp > 0 ? ", +" + q.xp + " XP" : "")).formatted(Formatting.YELLOW)));
    }
}
