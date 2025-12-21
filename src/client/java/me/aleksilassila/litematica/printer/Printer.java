package me.aleksilassila.litematica.printer;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import me.aleksilassila.litematica.printer.actions.Action;
import me.aleksilassila.litematica.printer.actions.PrepareAction;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.config.Hotkeys;
import me.aleksilassila.litematica.printer.guides.Guide;
import me.aleksilassila.litematica.printer.guides.Guides;
import me.aleksilassila.litematica.printer.mixin.EntityAccessor;
import net.minecraft.block.*; // Импортируем классы блоков
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Printer {
    public static final Logger logger = LogManager.getLogger(PrinterReference.MOD_ID);
    
    public static boolean overrideRotation = false;
    public static float targetYaw = 0f;
    public static float targetPitch = 0f;
    public static boolean isPlacing = false; 

    @Nonnull
    public final ClientPlayerEntity player;
    public final ActionHandler actionHandler;
    private final Guides interactionGuides = new Guides();
    
    private Direction lastHorizontalFacing = null;
    private int rotationCooldown = 0;

    public Printer(@Nonnull MinecraftClient client, @Nonnull ClientPlayerEntity player) {
        this.player = player;
        this.actionHandler = new ActionHandler(client, player);
    }

    public boolean onGameTick() {
        if (Hotkeys.TOGGLE_ACCURATE_MODE.getKeybind().isPressed()) {
            Configs.ACCURATE_MODE.setBooleanValue(!Configs.ACCURATE_MODE.getBooleanValue());
            MinecraftClient.getInstance().inGameHud.setOverlayMessage(
                net.minecraft.text.Text.of("Accurate Mode: " + (Configs.ACCURATE_MODE.getBooleanValue() ? "ON" : "OFF")),
                false
            );
        }

        WorldSchematic worldSchematic = SchematicWorldHandler.getSchematicWorld();
        if (!actionHandler.acceptsActions()) return false;
        if (worldSchematic == null) return false;
        if (!Configs.PRINT_MODE.getBooleanValue() && !Hotkeys.PRINT.getKeybind().isPressed()) return false;

        PlayerAbilities abilities = player.getAbilities();
        if (!abilities.allowModifyWorld) return false;

        Direction currentFacing = player.getHorizontalFacing();
        if (lastHorizontalFacing != null && currentFacing != lastHorizontalFacing) {
             rotationCooldown = 4;
        }
        lastHorizontalFacing = currentFacing;
        if (rotationCooldown > 0) {
            rotationCooldown--;
            return false;
        }

        boolean accurateMode = Configs.ACCURATE_MODE.getBooleanValue();
        int blocksPerTick;

        if (accurateMode) {
            blocksPerTick = 1;
            if (player.age % 2 != 0) return false;
        } else {
            blocksPerTick = Configs.BLOCKS_PER_TICK.getIntegerValue();
        }

        List<BlockPos> rawPositions = getReachablePositions();
        List<PlacementTask> tasks = new ArrayList<>();

        boolean restrictRotation = Configs.RESTRICT_ROTATION.getBooleanValue();
        
        for (BlockPos pos : rawPositions) {
            SchematicBlockState state = new SchematicBlockState(player.getWorld(), worldSchematic, pos);
            if (state.targetState.equals(state.currentState) || state.targetState.isAir()) {
                continue;
            }

            BlockState targetState = state.targetState;

            // Фикс для наблюдателей
            if (targetState.isOf(Blocks.OBSERVER) && targetState.contains(Properties.FACING)) {
                targetState = targetState.with(Properties.FACING, targetState.get(Properties.FACING).getOpposite());
            }

            // === УМНАЯ ФИЛЬТРАЦИЯ (RESTRICT ROTATION) ===
            // Ограничиваем только "капризные" блоки, которые зависят от взгляда игрока
            if (restrictRotation && isStrictDirectionalBlock(targetState.getBlock())) {
                if (!shouldPlaceWithCurrentFacing(targetState, currentFacing)) {
                    continue; // Пропускаем блок, если стоим неудобно
                }
            }
            // Остальные блоки (ступеньки, воронки, блоки без направления) проходят без проверки
            // ============================================

            tasks.add(new PlacementTask(pos, state, targetState));
        }

        if (tasks.isEmpty()) return false;

        tasks.sort(Comparator
            .<PlacementTask, Boolean>comparing(task -> !isMatchingFacing(task.targetState, player.getHorizontalFacing()))
            .thenComparingInt(task -> getDirectionId(task.targetState))
            .thenComparingDouble(task -> player.squaredDistanceTo(Vec3d.ofCenter(task.pos)))
        );

        int blocksFoundThisTick = 0;
        float initialYaw = player.getYaw();
        float initialPitch = player.getPitch();

        findBlock:
        for (PlacementTask task : tasks) {
            Guide[] guides = interactionGuides.getInteractionGuides(task.originalState);

            Vec3d rotation = calculateLookAt(task.pos);
            float lookYaw = (float) rotation.x;
            float lookPitch = (float) rotation.y;
            applyRotation(lookYaw, lookPitch);

            try {
                for (Guide guide : guides) {
                    if (guide.canExecute(player) && Configs.INTERACT_BLOCKS.getBooleanValue()) {
                        printDebug("Executing {} for {}", guide, task.originalState);
                        
                        List<Action> actions = new ArrayList<>(guide.execute(player));
                        
                        if (!actions.isEmpty() && !(actions.get(0) instanceof PrepareAction)) {
                            actions.add(0, new Action() {
                                @Override
                                public void send(MinecraftClient client, ClientPlayerEntity player) {
                                    player.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(
                                        player.getX(), player.getY(), player.getZ(),
                                        lookYaw, lookPitch,
                                        player.isOnGround(), player.horizontalCollision
                                    ));
                                    Printer.overrideRotation = true;
                                    Printer.targetYaw = lookYaw;
                                    Printer.targetPitch = lookPitch;
                                }
                            });
                        }

                        actionHandler.addActions(actions.toArray(Action[]::new));
                        
                        blocksFoundThisTick++;
                        if (blocksFoundThisTick >= blocksPerTick) break findBlock;
                        break; 
                    }
                    
                    if (guide.skipOtherGuides()) break;
                }
            } finally {
                applyRotation(initialYaw, initialPitch);
            }
        }

        return blocksFoundThisTick > 0;
    }
    
    // === НОВЫЙ МЕТОД: Список блоков, требующих строгой ориентации на игрока ===
    private boolean isStrictDirectionalBlock(Block block) {
        return block instanceof PistonBlock ||          // Поршни (Липкий и Обычный)
               block instanceof ObserverBlock ||        // Наблюдатель
               block instanceof DispenserBlock ||       // Раздатчик и Выбрасыватель
               block instanceof AbstractFurnaceBlock || // Печи, Плавильни, Коптильни
               block instanceof ChestBlock ||           // Сундуки
               block instanceof EnderChestBlock ||      // Эндер-сундук
               block instanceof BarrelBlock ||          // Бочка
               block instanceof CommandBlock ||         // Командные блоки
               block instanceof BeehiveBlock ||         // Ульи
               block instanceof CampfireBlock ||        // Костры
               block instanceof LecternBlock ||         // Кафедра
               block instanceof StonecutterBlock ||     // Камнерез
               block instanceof LoomBlock ||            // Ткацкий станок
               block instanceof CarvedPumpkinBlock ||   // Тыквы / Джеки
               // Дополнительные проверки для блоков, наследующих поведение
               (block != null && block.getClass().getSimpleName().contains("ShulkerBoxBlock")); // Шалкеры
    }
    // ==========================================================================

    private boolean shouldPlaceWithCurrentFacing(BlockState state, Direction currentFacing) {
        if (state.contains(Properties.HORIZONTAL_FACING)) {
            return state.get(Properties.HORIZONTAL_FACING) == currentFacing.getOpposite();
        }
        if (state.contains(Properties.FACING)) {
            Direction targetDir = state.get(Properties.FACING);
            // Вертикальные игнорируем (разрешаем всегда)
            if (targetDir.getAxis().isVertical()) return true;
            return targetDir == currentFacing.getOpposite();
        }
        if (state.contains(Properties.AXIS)) {
            Direction.Axis targetAxis = state.get(Properties.AXIS);
            if (targetAxis.isVertical()) return true;
            return targetAxis == currentFacing.getAxis();
        }
        return true;
    }

    private Vec3d calculateLookAt(BlockPos pos) {
        Vec3d eyePos = player.getEyePos();
        Vec3d targetCenter = Vec3d.ofCenter(pos);
        double d = targetCenter.x - eyePos.x;
        double e = targetCenter.y - eyePos.y;
        double f = targetCenter.z - eyePos.z;
        double g = Math.sqrt(d * d + f * f);
        float pitch = MathHelper.wrapDegrees((float)(-(MathHelper.atan2(e, g) * 57.2957763671875)));
        float yaw = MathHelper.wrapDegrees((float)(MathHelper.atan2(f, d) * 57.2957763671875) - 90.0F);
        return new Vec3d(yaw, pitch, 0);
    }

    private void applyRotation(float yaw, float pitch) {
        player.setYaw(yaw);
        player.setPitch(pitch);
        ((EntityAccessor) player).setPrevYaw(yaw);
        ((EntityAccessor) player).setPrevPitch(pitch);
    }

    private boolean isMatchingFacing(BlockState state, Direction playerFacing) {
        if (state.contains(Properties.HORIZONTAL_FACING)) return state.get(Properties.HORIZONTAL_FACING) == playerFacing.getOpposite();
        if (state.contains(Properties.FACING)) {
             Direction dir = state.get(Properties.FACING);
             return dir.getAxis().isVertical() || dir == playerFacing.getOpposite();
        }
        if (state.contains(Properties.AXIS)) return state.get(Properties.AXIS) == playerFacing.getAxis();
        return true; 
    }

    private int getDirectionId(BlockState state) {
        if (state.contains(Properties.HORIZONTAL_FACING)) return state.get(Properties.HORIZONTAL_FACING).ordinal();
        if (state.contains(Properties.FACING)) return state.get(Properties.FACING).ordinal();
        if (state.contains(Properties.AXIS)) return state.get(Properties.AXIS).ordinal();
        return -1;
    }

    private record PlacementTask(BlockPos pos, SchematicBlockState originalState, BlockState targetState) {}

    private List<BlockPos> getReachablePositions() {
        int maxReach = (int) Math.ceil(Configs.PRINTING_RANGE.getDoubleValue());
        double maxReachSquared = MathHelper.square(Configs.PRINTING_RANGE.getDoubleValue());
        ArrayList<BlockPos> positions = new ArrayList<>();

        for (int y = -maxReach; y < maxReach + 1; y++) {
            for (int x = -maxReach; x < maxReach + 1; x++) {
                for (int z = -maxReach; z < maxReach + 1; z++) {
                    BlockPos blockPos = player.getBlockPos().north(x).west(z).up(y);
                    if (!DataManager.getRenderLayerRange().isPositionWithinRange(blockPos)) continue;
                    if (this.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(blockPos)) > maxReachSquared) continue;
                    positions.add(blockPos);
                }
            }
        }
        return positions;
    }

    public static void printDebug(String key, Object... args) {
        if (Configs.PRINT_DEBUG.getBooleanValue()) {
            logger.info(key, args);
        }
    }
}