package fr.serveur.rpg.auto;

import fr.serveur.rpg.data.PlayerData;
import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.economy.Bank;
import fr.serveur.rpg.items.ModItems;
import fr.serveur.rpg.jobs.Job;
import fr.serveur.rpg.tools.OfficialTools;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Forge automatique : le joueur fabrique lui-même ses outils officiels (onglet Forge, touche B).
 * Coût = matériaux de la recette vanilla + pièces (en banque) + niveau minimum.
 * L'outil en bois est gratuit pour que chacun puisse démarrer sans le GM.
 */
public final class Forge {
    public record Requirement(String id, int count, ItemStack icon) {}

    private Forge() {}

    public static AutoConfig.ForgeTier tier(ModItems.Tier tier) {
        return AutoConfig.get().forge.getOrDefault(tier.id, new AutoConfig.ForgeTier());
    }

    public static List<Requirement> requirements(Job job, ModItems.Tier tier) {
        AutoConfig.ForgeTier t = tier(tier);
        List<Requirement> out = new ArrayList<>();
        if (t.matiere != null && !t.matiere.isBlank()) {
            int head = (job == Job.MINEUR || job == Job.BUCHERON) ? 3 : 2;
            out.add(new Requirement(t.matiere, head, icon(t.matiere)));
            out.add(new Requirement("minecraft:stick", job == Job.AVENTURIER ? 1 : 2, new ItemStack(Items.STICK)));
        }
        if (t.extra != null) t.extra.forEach((id, n) -> out.add(new Requirement(id, n, icon(id))));
        return out;
    }

    private static ItemStack icon(String id) {
        try {
            if (id.startsWith("#")) {
                for (RegistryEntry<Item> e : Registries.ITEM.iterateEntries(TagKey.of(RegistryKeys.ITEM, new Identifier(id.substring(1))))) {
                    return new ItemStack(e.value());
                }
                return ItemStack.EMPTY;
            }
            return new ItemStack(Registries.ITEM.get(new Identifier(id)));
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    private static boolean matches(ItemStack s, String id) {
        if (s.isEmpty() || s.getSubNbt(OfficialTools.NBT_KEY) != null) return false;
        if (id.startsWith("#")) return s.isIn(TagKey.of(RegistryKeys.ITEM, new Identifier(id.substring(1))));
        return Registries.ITEM.getId(s.getItem()).toString().equals(id);
    }

    public static int count(ServerPlayerEntity player, String id) {
        int n = 0;
        for (ItemStack s : player.getInventory().main) if (matches(s, id)) n += s.getCount();
        return n;
    }

    /** @return null si l'outil a été forgé, sinon la raison. */
    public static String forge(ServerPlayerEntity player, ModItems.Tier tier) {
        RpgState state = RpgState.get(player.getServer());
        PlayerData data = state.player(player);
        if (data.job == null) return "Choisis d'abord un métier.";
        AutoConfig.ForgeTier t = tier(tier);
        if (data.current().level < t.niveau) return "Niveau " + t.niveau + " requis pour forger cet outil.";
        if (data.money < t.pieces) return "Il te faut " + Bank.format(t.pieces) + " en banque.";
        if (data.frozen) return "Ton compte est gelé : contacte un GM.";
        List<Requirement> reqs = requirements(data.job, tier);
        for (Requirement r : reqs) {
            if (count(player, r.id()) < r.count()) return "Matériaux insuffisants : " + r.count() + " × " + r.icon().getName().getString() + ".";
        }
        PlayerInventory inv = player.getInventory();
        for (Requirement r : reqs) {
            int left = r.count();
            for (int i = 0; i < inv.main.size() && left > 0; i++) {
                ItemStack s = inv.main.get(i);
                if (!matches(s, r.id())) continue;
                int take = Math.min(left, s.getCount());
                s.decrement(take);
                left -= take;
            }
        }
        fr.serveur.rpg.economy.Economy.spend(player.getServer(), player.getUuid(),
                fr.serveur.rpg.economy.Economy.Source.FORGE, t.pieces, tier.id);
        state.markDirty();
        ItemStack tool = OfficialTools.create(ModItems.OFFICIAL.get(data.job).get(tier), data.job, state);
        if (!inv.insertStack(tool)) player.dropItem(tool, false);
        player.playSound(SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.6f, 1.1f);
        return null;
    }
}
