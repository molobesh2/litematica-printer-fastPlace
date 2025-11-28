package me.aleksilassila.litematica.printer;

import me.aleksilassila.litematica.printer.actions.Action;
import me.aleksilassila.litematica.printer.actions.PrepareAction;
import me.aleksilassila.litematica.printer.config.Configs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class ActionHandler {
    private final MinecraftClient client;
    private final ClientPlayerEntity player;
    private final Queue<Action> actionQueue = new LinkedList<>();
    public PrepareAction lookAction = null;

    public ActionHandler(MinecraftClient client, ClientPlayerEntity player) {
        this.client = client;
        this.player = player;
    }

    private int tick = 0;

    public void onGameTick() {
        int tickRate = Configs.PRINTING_INTERVAL.getIntegerValue();
        tick = tick % tickRate == tickRate - 1 ? 0 : tick + 1;

        if (tick % tickRate != 0) {
            return;
        }

        int blocksPerTick = Configs.BLOCKS_PER_TICK.getIntegerValue();

        for (int i = 0; i < blocksPerTick; i++) {
            Action nextAction = actionQueue.poll();

            if (nextAction != null) {
                Printer.printDebug("Sending action {}", nextAction);

                // 1. Включаем режим "обмана" для геттеров игрока
                Printer.isPlacing = true;

                // 2. Выполняем действие. 
                // Внутри этого вызова Minecraft спросит player.getYaw(), 
                // попадет в наш Миксин и получит Printer.targetYaw.
                nextAction.send(client, player);

                // 3. Выключаем режим. Рендер кадра (который будет позже) получит реальный угол.
                Printer.isPlacing = false;

            } else {
                lookAction = null;
                Printer.overrideRotation = false;
                break;
            }
        }

        if (lookAction != null) {
            Printer.overrideRotation = true;
            Printer.targetYaw = lookAction.yaw; 
            Printer.targetPitch = lookAction.pitch;
        } else {
            Printer.overrideRotation = false;
        }

        // Printer.isPrinting = !acceptsActions() || Configs.PRINT_MODE.getBooleanValue();

    }

        public boolean acceptsActions() {
            return actionQueue.size() < Configs.BLOCKS_PER_TICK.getIntegerValue() * 2;
        }

    public void addActions(Action... actions) {
        if (!acceptsActions()) {
            return;
        }

        for (Action action : actions) {
            if (action instanceof PrepareAction) {
                lookAction = (PrepareAction) action;
            }
        }

        actionQueue.addAll(List.of(actions));
    }
}