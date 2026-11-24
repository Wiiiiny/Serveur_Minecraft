package fr.serveur.rpg.raid;

import fr.serveur.rpg.Msg;
import fr.serveur.rpg.data.PlayerData;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Mort dans le raid : tout l'inventaire (sauf les objets liés à l'âme) reste dans un coffre de corps sur place,
 * que seul le joueur ou son groupe peut ouvrir pendant 10 minutes. Ensuite il disparaît avec son contenu.
 * Le joueur réapparaît au hub, sans attendre la nuit.
 */
public final class RaidDeath {
    private static final Set<UUID> RESPAWN_TO_HUB = new HashSet<>();

    private RaidDeath() {}

    /** Appelé au début de la mort (avant que l'inventaire ne tombe). @return vrai si la mort a eu lieu dans le raid. */
    public static boolean onDeath(ServerPlayerEntity p, PlayerData data, Predicate<ItemStack> soulbound) {
        if (!RaidWorld.isIn(p)) return false;
        ReturnBeacon.cancel(p);
        RaidState s = RaidState.get(p.getServer());
        ServerWorld w = p.getServerWorld();
        PlayerInventory inv = p.getInventory();
        RaidState.Corpse corpse = new RaidState.Corpse();
        corpse.owner = p.getUuid();
        corpse.ownerName = p.getGameProfile().getName();
        corpse.group.addAll(Party.members(p.getUuid()));
        corpse.expires = System.currentTimeMillis() + RaidConfig.get().coffreCorpsMinutes * 60_000L;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st.isEmpty()) continue;
            if (soulbound.test(st)) {
                data.keptItems.add(st.writeNbt(new NbtCompound()));
            } else {
                corpse.items.add(st.writeNbt(new NbtCompound()));
            }
            inv.setStack(i, ItemStack.EMPTY);
        }
        if (!corpse.items.isEmpty()) {
            corpse.pos = freeSpot(w, p.getBlockPos());
            w.setBlockState(corpse.pos, Blocks.CHEST.getDefaultState());
            s.corpses.add(corpse);
            s.markDirty();
        }
        RESPAWN_TO_HUB.add(p.getUuid());
        return true;
    }

    /** Réapparition après une mort dans le raid : direction le hub. @return vrai si c'était le cas. */
    public static boolean onRespawn(ServerPlayerEntity p) {
        if (!RESPAWN_TO_HUB.remove(p.getUuid())) return false;
        RaidWorld.sendToHub(p);
        Msg.send(p, Text.literal("Mort dans la zone de raid. Ton coffre de corps t'attend " + RaidConfig.get().coffreCorpsMinutes
                + " min sur place (toi ou ton groupe).").formatted(Formatting.RED));
        return true;
    }

    private static BlockPos freeSpot(ServerWorld w, BlockPos around) {
        for (int dy = 0; dy <= 3; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = around.add(dx, dy, dz);
                    if (w.getBlockState(p).isAir() && w.isInBuildLimit(p)) return p;
                }
            }
        }
        return around;
    }

    /** Clic sur un coffre du raid : s'il s'agit d'un coffre de corps, on l'ouvre (ou on refuse). @return vrai si c'était un corps. */
    public static boolean open(ServerPlayerEntity p, BlockPos pos) {
        RaidState s = RaidState.get(p.getServer());
        RaidState.Corpse corpse = null;
        for (RaidState.Corpse c : s.corpses) if (c.pos.equals(pos)) corpse = c;
        if (corpse == null) return false;
        if (!corpse.owner.equals(p.getUuid()) && !corpse.group.contains(p.getUuid()) && !p.hasPermissionLevel(2)) {
            p.sendMessage(Text.literal("Le coffre de corps de " + corpse.ownerName + " : réservé à lui et à son groupe.").formatted(Formatting.RED), true);
            return true;
        }
        RaidState.Corpse c = corpse;
        SimpleInventory inv = new SimpleInventory(54);
        for (int i = 0; i < c.items.size() && i < 54; i++) inv.setStack(i, ItemStack.fromNbt(c.items.getCompound(i)));
        inv.addListener(changed -> {
            NbtList items = new NbtList();
            for (int i = 0; i < inv.size(); i++) if (!inv.getStack(i).isEmpty()) items.add(inv.getStack(i).writeNbt(new NbtCompound()));
            c.items = items;
            s.markDirty();
        });
        p.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, playerInv, pl) ->
                GenericContainerScreenHandler.createGeneric9x6(syncId, playerInv, inv), Text.literal("Corps de " + c.ownerName)));
        return true;
    }

    /** Retire les coffres de corps expirés ou vidés. */
    public static void tick(ServerWorld w, RaidState s, long now) {
        Iterator<RaidState.Corpse> it = s.corpses.iterator();
        while (it.hasNext()) {
            RaidState.Corpse c = it.next();
            if (now < c.expires && !c.items.isEmpty()) continue;
            if (w != null && w.getBlockState(c.pos).isOf(Blocks.CHEST)) w.setBlockState(c.pos, Blocks.AIR.getDefaultState());
            it.remove();
            s.markDirty();
        }
    }
}
