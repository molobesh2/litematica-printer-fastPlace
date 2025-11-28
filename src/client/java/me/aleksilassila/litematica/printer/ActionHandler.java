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
    
    // Таймер задержки для точного режима
    private int delayTimer = 0;

    public ActionHandler(MinecraftClient client, ClientPlayerEntity player) {
        this.client = client;
        this.player = player;
    }

    private int tick = 0;

    public void onGameTick() {
        int tickRate = Configs.PRINTING_INTERVAL.getIntegerValue();
        tick = tick % tickRate == tickRate - 1 ? 0 : tick + 1;

        if (tick % tickRate != 0) return;

        boolean accurateMode = Configs.ACCURATE_MODE.getBooleanValue();

        // В точном режиме проверяем таймер задержки
        if (accurateMode) {
            if (delayTimer > 0) {
                delayTimer--;
                return; // Пропускаем тик (ждем синхронизации сервера)
            }
        }

        // В точном режиме строго 1 действие за тик (игнорируем blocksPerTick из конфига)
        int blocksPerTick = accurateMode ? 1 : Configs.BLOCKS_PER_TICK.getIntegerValue();

        float realYaw = player.getYaw();
        float realPitch = player.getPitch();
        
        EntityAccessor entityAccess = (EntityAccessor) player;
        LivingEntityAccessor livingAccess = (LivingEntityAccessor) player;
        
        float realPrevYaw = entityAccess.getPrevYaw();
        float realPrevPitch = entityAccess.getPrevPitch();
        float realHeadYaw = player.headYaw;
        float realPrevHeadYaw = livingAccess.getPrevHeadYaw();
        float realBodyYaw = player.bodyYaw;
        float realPrevBodyYaw = livingAccess.getPrevBodyYaw();

        boolean wasModified = false;

        try {
            for (int i = 0; i < blocksPerTick; i++) {
                Action nextAction = actionQueue.poll();

                if (nextAction != null) {
                    // Логика поворота камеры
                    if (Printer.overrideRotation) {
                        float targetYaw = Printer.targetYaw;
                        float targetPitch = Printer.targetPitch;

                        player.setYaw(targetYaw);
                        player.setPitch(targetPitch);
                        player.setHeadYaw(targetYaw);
                        player.setBodyYaw(targetYaw);

                        entityAccess.setPrevYaw(targetYaw);
                        entityAccess.setPrevPitch(targetPitch);
                        livingAccess.setPrevHeadYaw(targetYaw);
                        livingAccess.setPrevBodyYaw(targetYaw);
                        
                        wasModified = true;
                    } else if (wasModified) {
                        // Если overrideRotation выключился (новая группа блоков), сбрасываем камеру
                        player.setYaw(realYaw);
                        player.setPitch(realPitch);
                        player.setHeadYaw(realHeadYaw);
                        player.setBodyYaw(realBodyYaw);

                        entityAccess.setPrevYaw(realPrevYaw);
                        entityAccess.setPrevPitch(realPrevPitch);
                        livingAccess.setPrevHeadYaw(realPrevHeadYaw);
                        livingAccess.setPrevBodyYaw(realPrevBodyYaw);
                        
                        wasModified = false;
                    }

                    nextAction.send(client, player);
                    
                    // ЕСЛИ ТОЧНЫЙ РЕЖИМ: Ставим таймер на 1 тик.
                    // Это значит, что следующее действие выполнится только через 1 тик.
                    // Результат: [Действие] -> [Пустой тик] -> [Действие]
                    if (accurateMode) {
                        delayTimer = 1; 
                        break; // Выходим из цикла, сделали 1 дело
                    }
                    
                } else {
                    lookAction = null;
                    Printer.overrideRotation = false;
                    break;
                }
            }
        } finally {
            if (wasModified) {
                player.setYaw(realYaw);
                player.setPitch(realPitch);
                player.setHeadYaw(realHeadYaw);
                player.setBodyYaw(realBodyYaw);
                
                entityAccess.setPrevYaw(realPrevYaw);
                entityAccess.setPrevPitch(realPrevPitch);
                livingAccess.setPrevHeadYaw(realPrevHeadYaw);
                livingAccess.setPrevBodyYaw(realPrevBodyYaw);
            }
        }
    }

    public boolean acceptsActions() {
        return actionQueue.isEmpty();
    }

    public void addActions(Action... actions) {
        if (!acceptsActions()) return;
        for (Action action : actions) {
            if (action instanceof PrepareAction) lookAction = (PrepareAction) action;
        }
        actionQueue.addAll(List.of(actions));
    }
}