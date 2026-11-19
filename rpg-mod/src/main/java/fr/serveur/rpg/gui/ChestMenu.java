package fr.serveur.rpg.gui;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Menu « coffre » en lecture seule, utilisable avec un client vanilla :
 * aucun objet ne peut être pris, chaque clic est transmis à {@link Handler}.
 */
public class ChestMenu extends GenericContainerScreenHandler {
    public interface Handler {
        void onClick(ServerPlayerEntity player, int slot, SlotActionType type, int button);
    }

    private final int menuSize;
    private final Handler handler;

    private ChestMenu(int syncId, PlayerInventory playerInv, SimpleInventory inv, int rows, Handler handler) {
        super(typeFor(rows), syncId, playerInv, inv, rows);
        this.menuSize = rows * 9;
        this.handler = handler;
    }

    private static ScreenHandlerType<GenericContainerScreenHandler> typeFor(int rows) {
        return switch (rows) {
            case 1 -> ScreenHandlerType.GENERIC_9X1;
            case 2 -> ScreenHandlerType.GENERIC_9X2;
            case 3 -> ScreenHandlerType.GENERIC_9X3;
            case 4 -> ScreenHandlerType.GENERIC_9X4;
            case 5 -> ScreenHandlerType.GENERIC_9X5;
            default -> ScreenHandlerType.GENERIC_9X6;
        };
    }

    public static void open(ServerPlayerEntity player, Text title, SimpleInventory inv, Handler handler) {
        int rows = Math.max(1, Math.min(6, inv.size() / 9));
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, p) -> new ChestMenu(syncId, playerInv, inv, rows, handler), title));
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        // On n'appelle jamais super : rien ne bouge, ni dans le menu ni dans l'inventaire du joueur.
        if (player instanceof ServerPlayerEntity sp) {
            if (slotIndex >= 0 && slotIndex < menuSize && actionType != SlotActionType.QUICK_CRAFT) {
                handler.onClick(sp, slotIndex, actionType, button);
            }
            if (sp.currentScreenHandler == this) syncState();
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }
}
