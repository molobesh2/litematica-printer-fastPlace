package me.aleksilassila.litematica.printer;

import me.aleksilassila.litematica.printer.actions.Action;
import me.aleksilassila.litematica.printer.actions.PrepareAction;
import me.aleksilassila.litematica.printer.config.Configs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class ActionHandler {
    private final Minecraft client;
    private final LocalPlayer player;
    private final Queue<Action> actionQueue = new LinkedList<>();
    public PrepareAction lookAction = null;

    private int lastSendTick = -1;

    public ActionHandler(Minecraft client, LocalPlayer player) {
        this.client = client;
        this.player = player;
    }

    public void onGameTick() {
        if (useRotatePipeline()) {
            sendOneActionPerTick();
        } else {
            drainQueue();
        }
    }

// Конвейер (1 действие за игровой тик) нужен ТОЛЬКО для горизонтальной
// ротации: Prepare и Interact должны уйти в разных тиках, чтобы между
// ними ванильный move-пакет закрепил фейковый yaw на сервере.
// Условия: ROTATE включён, один блок за пакет, интервал больше 1.

// The pipeline (1 action per game tick) is ONLY needed for horizontal
// rotation: Prepare and Interact must leave in different ticks so that the 
// vanilla move package fixes the fake yaw on the server between them.
// Conditions: ROTATE is enabled, one block per batch, the interval is greater than 1.
    private boolean useRotatePipeline() {
        return Configs.ROTATE.getBooleanValue()
                && Configs.BLOCKS_PER_TICK.getIntegerValue() == 1
                && Configs.PRINTING_INTERVAL.getIntegerValue() > 1;
    }

    private void sendOneActionPerTick() {
        int tick = player.tickCount;
        if (tick == lastSendTick) {
            return; 
        }
        Action nextAction = actionQueue.poll();
        if (nextAction != null) {
            lastSendTick = tick;
            if (nextAction instanceof PrepareAction) {
                lookAction = (PrepareAction) nextAction;
            }
            Printer.printDebug("Sending action {}", nextAction);
            // Printer.printDebug("SEND: tick={}, action={}", player.tickCount,
            // nextAction.getClass().getSimpleName());
            nextAction.send(client, player);

        } else {
            lookAction = null;
        }
    }


    // Стабильный режим: весь пакет уходит одним burst-ом.
    // Вертикальная ротация здесь работает,
    // горизонтальная - нет, её контролирует RESTRICT_ROTATION.

    // Stable mode: the entire package goes away in one burst.
    // Vertical rotation works here,
    // horizontal rotation does not, it is controlled by RESTRICT_ROTATION.
    private void drainQueue() {
        Action nextAction;
        while ((nextAction = actionQueue.poll()) != null) {
            if (nextAction instanceof PrepareAction) {
                lookAction = (PrepareAction) nextAction;
            }
            Printer.printDebug("Sending action {}", nextAction);
            // Printer.printDebug("SEND: tick={}, action={}", player.tickCount,
            // nextAction.getClass().getSimpleName());
            nextAction.send(client, player);
        }
        lookAction = null;
    }

    public boolean acceptsActions() {
        return actionQueue.isEmpty();
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