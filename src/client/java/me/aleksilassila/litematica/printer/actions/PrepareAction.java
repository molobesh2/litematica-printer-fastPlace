package me.aleksilassila.litematica.printer.actions;

import fi.dy.masa.litematica.util.InventoryUtils;
import me.aleksilassila.litematica.printer.Printer;
import me.aleksilassila.litematica.printer.implementation.PrinterPlacementContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.client.player.Input;
import net.minecraft.core.Direction;

public class PrepareAction extends Action {
    public final PrinterPlacementContext context;
    public boolean modifyYaw = true;
    public boolean modifyPitch = true;
    public float yaw = 0;
    public float pitch = 0;

    public PrepareAction(PrinterPlacementContext context) {
        this.context = context;
        Direction lookDirection = context.lookDirection;

        if (lookDirection != null && lookDirection.getAxis().isHorizontal()) {
            this.yaw = lookDirection.getPositiveHorizontalDegrees();
        } else {
            this.modifyYaw = false;
        }

        if (lookDirection == Direction.UP) {
            this.pitch = -90;
        } else if (lookDirection == Direction.DOWN) {
            this.pitch = 90;
        } else if (lookDirection != null) {
            this.pitch = 0;
        } else {
            this.modifyPitch = false;
        }
    }

    public PrepareAction(PrinterPlacementContext context, float yaw, float pitch) {
        this.context = context;
        this.yaw = yaw;
        this.pitch = pitch;
        this.modifyYaw = true;
        this.modifyPitch = true;
    }

    @Override
    public void send(Minecraft client, LocalPlayer player) {

        ItemStack itemStack = this.context.getStack();
        int slot = this.context.requiredItemSlot;

        if (itemStack != null && !itemStack.isEmpty() && client.getNetworkHandler() != null) {
            
            Inventory inventory = player.getInventory();
            if (player.getAbilities().creativeMode) {
                this.addPickBlock(inventory, itemStack);
                
                client.interactionManager.clickCreativeStack(player.getStackInHand(InteractionHand.MAIN_HAND),
                        36 + inventory.getSlotWithStack(player.getMainHandStack()));
            } else if (slot != -1) {
                if (Inventory.isValidHotbarIndex(slot)) {
                    inventory.setSelectedSlot(slot);
                } else {
                    InventoryUtils.setPickedItemToHand(slot, itemStack, client);
                }
            }
        }

        if (modifyPitch || modifyYaw) {
            float targetYaw = modifyYaw ? this.yaw : player.getYaw();
            float targetPitch = modifyPitch ? this.pitch : player.getPitch();

            ServerboundMovePlayerPacket packet = new ServerboundMovePlayerPacket.Full(
                player.getX(), player.getY(), player.getZ(), 
                targetYaw, targetPitch, 
                player.isOnGround(), player.horizontalCollision
            );
            player.networkHandler.sendPacket(packet);

            Printer.overrideRotation = true;
            Printer.targetYaw = targetYaw;
            Printer.targetPitch = targetPitch;
        } else {
            Printer.overrideRotation = false;
        }

        boolean sneaking = context.shouldSneak;
        player.setSneaking(sneaking);
        
        Input currentInput = player.input.playerInput;
        player.input.playerInput = new Input(
            currentInput.forward(), 
            currentInput.backward(), 
            currentInput.left(), 
            currentInput.right(), 
            currentInput.jump(), 
            sneaking, 
            currentInput.sprint()
        );
        player.networkHandler.sendPacket(new ServerboundPlayerInputPacket(player.input.playerInput));
    }

private void addPickBlock(Inventory inv, ItemStack stack) {
    int slot = inv.getSlotWithStack(stack);
    if (Inventory.isValidHotbarIndex(slot)) { 
        inv.removeStack(slot);
    } else if (slot == -1) {
        inv.removeStack(inv.getSwappableHotbarSlot());
        int selectedSlotIndex = inv.getSelectedSlot(); 
        if (!inv.getStack(selectedSlotIndex).isEmpty()) {
            int empty = inv.getEmptySlot();
            if (empty != -1) {
                inv.setStack(empty, inv.getStack(selectedSlotIndex));
            }
        }
        inv.setStack(selectedSlotIndex, stack);
    } else {
        inv.swapSlotWithHotbar(slot); // method_7365
    }
}

    @Override
    public String toString() {
        return "PrepareAction{" +
                "yaw=" + yaw +
                ", pitch=" + pitch +
                ", context=" + context +
                '}';
    }
}