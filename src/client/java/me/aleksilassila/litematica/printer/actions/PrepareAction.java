package me.aleksilassila.litematica.printer.actions;

import me.aleksilassila.litematica.printer.Printer;
import me.aleksilassila.litematica.printer.implementation.PrinterPlacementContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Direction;

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
    public void send(MinecraftClient client, ClientPlayerEntity player) {
        ItemStack itemStack = context.getStack();
        int slot = context.requiredItemSlot;

        if (itemStack != null && client.interactionManager != null) {
            PlayerInventory inventory = player.getInventory();
            if (player.getAbilities().creativeMode) {
                player.giveItemStack(itemStack);
                client.interactionManager.clickCreativeStack(player.getStackInHand(Hand.MAIN_HAND),
                        36 + inventory.getSlotWithStack(player.getMainHandStack()));
            } else if (slot != -1) {
                if (PlayerInventory.isValidHotbarIndex(slot)) {
                    inventory.setSelectedSlot(slot);
                } else {
                    client.interactionManager.clickCreativeStack(player.getInventory().getStack(slot), 36 + slot);
                }
            }
        }

        if (modifyPitch || modifyYaw) {
            float targetYaw = modifyYaw ? this.yaw : player.getYaw();
            float targetPitch = modifyPitch ? this.pitch : player.getPitch();

            // 1. Отправляем пакет СЕРВЕРУ
            PlayerMoveC2SPacket packet = new PlayerMoveC2SPacket.Full(
                player.getX(), 
                player.getY(), 
                player.getZ(), 
                targetYaw, 
                targetPitch, 
                player.isOnGround(), 
                player.horizontalCollision
            );
            player.networkHandler.sendPacket(packet);

            // 2. Передаем данные в Printer (для ActionHandler)
            Printer.overrideRotation = true;
            Printer.targetYaw = targetYaw;
            Printer.targetPitch = targetPitch;
        } else {
            Printer.overrideRotation = false;
        }

        boolean sneaking = context.shouldSneak;
        player.setSneaking(sneaking);
        
        PlayerInput currentInput = player.input.playerInput;
        player.input.playerInput = new PlayerInput(
            currentInput.forward(), 
            currentInput.backward(), 
            currentInput.left(), 
            currentInput.right(), 
            currentInput.jump(), 
            sneaking, 
            currentInput.sprint()
        );
        player.networkHandler.sendPacket(new PlayerInputC2SPacket(player.input.playerInput));
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