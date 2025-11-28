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
        
        // --- НАЧАЛО ИЗМЕНЕНИЙ ---

        int blocksPerTick = Configs.BLOCKS_PER_TICK.getIntegerValue();

        for (int i = 0; i < blocksPerTick; i++) {
            Action nextAction = actionQueue.poll();

            if (nextAction != null) {
                Printer.printDebug("Sending action {} ({}/{})", nextAction, i + 1, blocksPerTick);
                nextAction.send(client, player);
            } else {
                // Очередь пуста, прерываем цикл
                lookAction = null;
                break;
            }
        }
        
        // --- КОНЕЦ ИЗМЕНЕНИЙ ---
    }

        public boolean acceptsActions() {
            // Позволяем добавлять новые действия, если в очереди меньше, 
            // чем, например, двойное количество блоков, устанавливаемых за раз.
            // Это создает буфер и сглаживает паузы.
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