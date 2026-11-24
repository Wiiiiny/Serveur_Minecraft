package fr.serveur.rpg.raid;

import fr.serveur.rpg.data.RpgState;
import fr.serveur.rpg.economy.DynamicShop;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

/** Tables de butin du raid (config/survierpg/loot.json) : coffres des paliers 1, 2, 3, butin des mini-boss. */
public final class Loot {
    private Loot() {}

    /** Tire le butin d'un palier (objets déjà marqués « butin du raid »). */
    public static List<ItemStack> roll(int tier, Random random) {
        RaidConfig.LootData data = RaidConfig.loot();
        List<RaidConfig.LootEntry> table = data.paliers.getOrDefault(String.valueOf(tier), List.of());
        int[] range = data.tirages.getOrDefault(String.valueOf(tier), new int[]{3, 5});
        List<ItemStack> out = new ArrayList<>();
        int total = table.stream().mapToInt(e -> Math.max(0, e.poids)).sum();
        if (total <= 0) return out;
        int rolls = range[0] + random.nextInt(Math.max(1, range[1] - range[0] + 1));
        for (int i = 0; i < rolls; i++) {
            int pick = random.nextInt(total);
            for (RaidConfig.LootEntry e : table) {
                pick -= Math.max(0, e.poids);
                if (pick >= 0) continue;
                Item item = Registries.ITEM.get(Identifier.tryParse(e.objet));
                if (item == Items.AIR) break;
                ItemStack s = new ItemStack(item, e.min + random.nextInt(Math.max(1, e.max - e.min + 1)));
                if (e.enchante) s = EnchantmentHelper.enchant(random, s, 10 + random.nextInt(21), false);
                RaidWorld.markLoot(s);
                out.add(s);
                break;
            }
        }
        return out;
    }

    /** Remplit un coffre (vidé avant) avec le butin d'un palier, dans des cases au hasard. */
    public static void fill(ServerWorld world, BlockPos pos, int tier, Random random) {
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof Inventory inv)) return;
        inv.clear();
        for (ItemStack s : roll(tier, random)) {
            for (int tries = 0; tries < 20; tries++) {
                int slot = random.nextInt(inv.size());
                if (inv.getStack(slot).isEmpty()) {
                    inv.setStack(slot, s);
                    break;
                }
            }
        }
        be.markDirty();
    }

    /** Valeur (pièces) d'un objet pour le bonus d'extraction : prix de la boutique, sinon loot.json, sinon 1. */
    public static double value(RpgState state, ItemStack s) {
        double ref = DynamicShop.referenceUnitPrice(state, stripped(s));
        if (ref > 0) return ref * s.getCount();
        Integer v = RaidConfig.loot().valeurs.get(Registries.ITEM.getId(s.getItem()).toString());
        return (v == null ? 1 : v) * s.getCount();
    }

    private static ItemStack stripped(ItemStack s) {
        ItemStack c = s.copy();
        RaidWorld.unmarkLoot(c);
        return c;
    }
}
