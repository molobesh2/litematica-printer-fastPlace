package me.aleksilassila.litematica.printer.actions;

import me.aleksilassila.litematica.printer.implementation.PrinterPlacementContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
// import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;

//import net.minecraft.client.input.PlayerInput; // new way
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

        // switch(lookDirection) {
        //     case UP:
        //         this.pitch = -90;
        //         break;
        //     case DOWN:
        //         this.pitch = 90;
        //         break;
        //     case NORTH:
        //         this.yaw = 180;
        //         break;
        //     case SOUTH:
        //         this.yaw = 0;
        //         break;
        //     case WEST:
        //         this.yaw = 90;
        //         break;
        //     case EAST:
        //         this.yaw = 270;
        //         break;
        //     default:
        //         this.modifyYaw = false;
        //         this.modifyPitch = false;
        //         break;
        // }

    }

    public PrepareAction(PrinterPlacementContext context, float yaw, float pitch) {
        this.context = context;

        this.yaw = yaw;
        this.pitch = pitch;
    }

    @Override
    public void send(MinecraftClient client, ClientPlayerEntity player) {
        ItemStack itemStack = context.getStack();
        int slot = context.requiredItemSlot;

        if (itemStack != null && client.interactionManager != null) {
            PlayerInventory inventory = player.getInventory();

            // This thing is straight from MinecraftClient#doItemPick()
            if (player.getAbilities().creativeMode) {
                player.giveItemStack(itemStack);
                client.interactionManager.clickCreativeStack(player.getStackInHand(Hand.MAIN_HAND),
                        36 + inventory.getSlotWithStack(player.getMainHandStack()));;
            } else if (slot != -1) {
                if (PlayerInventory.isValidHotbarIndex(slot)) {
                    inventory.setSelectedSlot(slot);
                } else {
                    client.interactionManager.clickCreativeStack(player.getInventory().getStack(slot), 36 + slot);
                }
            }
        }

        if (modifyPitch || modifyYaw) {
            float yaw = modifyYaw ? this.yaw : player.getYaw();
            float pitch = modifyPitch ? this.pitch : player.getPitch();

            PlayerMoveC2SPacket packet = new PlayerMoveC2SPacket.Full(
                player.getX(), 
                player.getY(), 
                player.getZ(), 
                yaw, 
                pitch, 
                player.isOnGround(), 
                player.horizontalCollision);

            player.networkHandler.sendPacket(packet);
        }

        if (context.shouldSneak) {                                                                                             
            player.input.playerInput = new PlayerInput(player.input.playerInput.forward(), player.input.playerInput.backward(), player.input.playerInput.left(), player.input.playerInput.right(), player.input.playerInput.jump(), true, player.input.playerInput.sprint());
            player.networkHandler.sendPacket(new PlayerInputC2SPacket(player.input.playerInput));
        } else {
            player.input.playerInput = new PlayerInput(player.input.playerInput.forward(), player.input.playerInput.backward(), player.input.playerInput.left(), player.input.playerInput.right(), player.input.playerInput.jump(), false, player.input.playerInput.sprint());
            player.networkHandler.sendPacket(new PlayerInputC2SPacket(player.input.playerInput));
        }
    }

    @Override
    public String toString() {
        return "PrepareAction{" +
                "context=" + context +
                '}';
    }
}
