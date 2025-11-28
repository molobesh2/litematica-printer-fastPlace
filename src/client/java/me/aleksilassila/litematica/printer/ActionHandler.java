package me.aleksilassila.litematica.printer;

import me.aleksilassila.litematica.printer.actions.Action;
import me.aleksilassila.litematica.printer.actions.PrepareAction;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.mixin.EntityAccessor;
import me.aleksilassila.litematica.printer.mixin.LivingEntityAccessor;
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
                // Используем Аксессоры, так как поля protected
                EntityAccessor entityAccess = (EntityAccessor) player;
                LivingEntityAccessor livingAccess = (LivingEntityAccessor) player;

                // 1. Сохраняем реальное состояние
                float realYaw = player.getYaw();
                float realPitch = player.getPitch();
                float realPrevYaw = entityAccess.getPrevYaw();
                float realPrevPitch = entityAccess.getPrevPitch();
                
                float realHeadYaw = player.headYaw;
                float realPrevHeadYaw = livingAccess.getPrevHeadYaw();
                
                float realBodyYaw = player.bodyYaw;
                float realPrevBodyYaw = livingAccess.getPrevBodyYaw();

                boolean applyRotation = Printer.overrideRotation;

                if (applyRotation) {
                    float targetYaw = Printer.targetYaw;
                    float targetPitch = Printer.targetPitch;

                    // Устанавливаем текущие углы
                    player.setYaw(targetYaw);
                    player.setPitch(targetPitch);
                    player.setHeadYaw(targetYaw);
                    player.setBodyYaw(targetYaw);

                    // Убиваем интерполяцию через Аксессоры
                    entityAccess.setPrevYaw(targetYaw);
                    entityAccess.setPrevPitch(targetPitch);
                    livingAccess.setPrevHeadYaw(targetYaw);
                    livingAccess.setPrevBodyYaw(targetYaw);
                }

                try {
                    // Выполняем действие с подмененными углами
                    nextAction.send(client, player);
                } finally {
                    // Возвращаем все назад
                    if (applyRotation) {
                        player.setYaw(realYaw);
                        player.setPitch(realPitch);
                        
                        entityAccess.setPrevYaw(realPrevYaw);
                        entityAccess.setPrevPitch(realPrevPitch);
                        
                        player.setHeadYaw(realHeadYaw);
                        livingAccess.setPrevHeadYaw(realPrevHeadYaw);
                        
                        player.setBodyYaw(realBodyYaw);
                        livingAccess.setPrevBodyYaw(realPrevBodyYaw);
                    }
                }

            } else {
                lookAction = null;
                Printer.overrideRotation = false;
                break;
            }
        }
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