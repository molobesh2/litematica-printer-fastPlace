package me.aleksilassila.litematica.printer.actions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
//import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket; //old way

import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket; //new way 1
import net.minecraft.client.player.Input;
//import net.minecraft.client.input.Input;

import net.minecraft.client.player.Input;

public class ReleaseShiftAction extends Action {
    @Override
    public void send(Minecraft client, LocalPlayer player) {
        player.input.playerInput = new Input(player.input.playerInput.forward(), player.input.playerInput.backward(), player.input.playerInput.left(), player.input.playerInput.right(), player.input.playerInput.jump(), false, player.input.playerInput.sprint());
        player.networkHandler.sendPacket(new ServerboundPlayerInputPacket(player.input.playerInput));
    }
}

