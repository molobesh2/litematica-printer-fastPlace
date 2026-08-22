package me.aleksilassila.litematica.printer;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import me.aleksilassila.litematica.printer.actions.Action;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.config.Hotkeys;
import me.aleksilassila.litematica.printer.guides.Guide;
import me.aleksilassila.litematica.printer.guides.Guides;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class Printer {
    public static final Logger logger = LogManager.getLogger(PrinterReference.MOD_ID);
    private static final java.util.Map<BlockPos, Long> PENDING_BLOCKS =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static final float RESTRICT_ROTATION_TOLERANCE = 15.0f;

    private boolean hasPrintedBatch = false;
    private int lastBatchTick = 0;

    @Nonnull
    public final LocalPlayer player;
    public final ActionHandler actionHandler;
    private final Guides interactionGuides = new Guides();

    public Printer(@Nonnull Minecraft client, @Nonnull LocalPlayer player) {
        this.player = player;
        this.actionHandler = new ActionHandler(client, player);
    }

    public boolean onGameTick() {
        long now = System.currentTimeMillis();
        int interval = Math.max(1, Configs.PRINTING_INTERVAL.getIntegerValue());

        //было нужно для 1.21.11 без задержки создание очереди обгоняло установку блоков
        // it was necessary for 1.21.11 to create a queue without delay, overtaking the installation of blocks
        long pendingTimeout = interval * 20L;

        PENDING_BLOCKS.entrySet().removeIf(entry -> now - entry.getValue() > pendingTimeout);

        WorldSchematic worldSchematic = SchematicWorldHandler.getSchematicWorld();
        if (!actionHandler.acceptsActions()) {
            return false;
        }
        if (worldSchematic == null) {
            return false;
        }
        if (!Configs.PRINT_MODE.getBooleanValue() && !Hotkeys.PRINT.getKeybind().isPressed()) {
            return false;
        }
        Abilities abilities = player.getAbilities();
        if (!abilities.mayBuild) {
            return false;
        }

        int gameTick = player.tickCount;
        if (hasPrintedBatch && gameTick - lastBatchTick < interval) {
            return false;
        }

        int blocksPlaced = 0;
        int maxBlocks = Math.max(1, Configs.BLOCKS_PER_TICK.getIntegerValue());
        boolean restrictRotation = Configs.RESTRICT_ROTATION.getBooleanValue();

        List<BlockPos> positions = getReachablePositions();
        List<Action> batch = new ArrayList<>();

        findBlock:
        for (BlockPos position : positions) {
            if (blocksPlaced >= maxBlocks) break;
            if (PENDING_BLOCKS.containsKey(position)) continue;

            SchematicBlockState state = new SchematicBlockState(
                    player.level(), worldSchematic, position);
            if (state.targetState.equals(state.currentState) || state.targetState.isAir()) {
                continue;
            }

            if (restrictRotation && isStrictDirectionalBlock(state.targetState.getBlock())) {
                if (!shouldPlaceWithCurrentFacing(state.targetState)) {
                    continue;
                }
            }

            Guide[] guides = interactionGuides.getInteractionGuides(state);
            for (Guide guide : guides) {
                if (guide.canExecute(player) && Configs.INTERACT_BLOCKS.getBooleanValue()) {
                    printDebug("Executing {} for {}", guide, state);
                    List<Action> actions = guide.execute(player);
                    if (actions == null || actions.isEmpty()) continue;
                    batch.addAll(actions);
                    PENDING_BLOCKS.put(position, now);
                    blocksPlaced++;
                    continue findBlock;
                }
                if (guide.skipOtherGuides()) {
                    continue findBlock;
                }
            }
        }

        if (batch.isEmpty()) {
            return false;
        }

        hasPrintedBatch = true;
        lastBatchTick = gameTick;
        
        // printDebug("BATCH: tick={}, last={}, interval={}, blocks={}, pending={}",
        //         gameTick, lastBatchTick, interval, blocksPlaced, PENDING_BLOCKS.size());

        actionHandler.addActions(batch.toArray(new Action[0]));
        return true;
    }

    private List<BlockPos> getReachablePositions() {
        int maxReach = (int) Math.ceil(Configs.PRINTING_RANGE.getDoubleValue());
        double maxReachSquared = Mth.square(Configs.PRINTING_RANGE.getDoubleValue());

        ArrayList<BlockPos> positions = new ArrayList<>();

        for (int y = -maxReach; y < maxReach + 1; y++) {
            for (int x = -maxReach; x < maxReach + 1; x++) {
                for (int z = -maxReach; z < maxReach + 1; z++) {
                    BlockPos blockPos = player.blockPosition().north(x).west(z).above(y);

                    if (!DataManager.getRenderLayerRange().isPositionWithinRange(blockPos)) {
                        continue;
                    }
                    if (this.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(blockPos)) > maxReachSquared) {
                        continue;
                    }

                    positions.add(blockPos);
                }
            }
        }

        return positions.stream()
                .filter(p ->
                {
                    Vec3 vec = Vec3.atCenterOf(p);
                    return this.player.position().distanceToSqr(vec) > 1
                            && this.player.getEyePosition().distanceToSqr(vec) > 1;
                })
                .sorted((a, b) ->
                {
                    double aDistance = this.player.position().distanceToSqr(Vec3.atCenterOf(a));
                    double bDistance = this.player.position().distanceToSqr(Vec3.atCenterOf(b));
                    return Double.compare(aDistance, bDistance);
                }).toList();
    }

    public static void printDebug(String key, Object... args) {
        if (Configs.PRINT_DEBUG.getBooleanValue()) {
            logger.info(key, args);
        }
    }
    //shiten crutch 
    private boolean isStrictDirectionalBlock(net.minecraft.world.level.block.Block block) {
        return block instanceof net.minecraft.world.level.block.piston.PistonBaseBlock ||
                block instanceof net.minecraft.world.level.block.ObserverBlock ||
                block instanceof net.minecraft.world.level.block.DispenserBlock ||
                block instanceof net.minecraft.world.level.block.HopperBlock ||
                block instanceof net.minecraft.world.level.block.AbstractFurnaceBlock ||
                block instanceof net.minecraft.world.level.block.ShulkerBoxBlock ||
                block instanceof net.minecraft.world.level.block.BarrelBlock ||
                block instanceof net.minecraft.world.level.block.ChestBlock;
    }

    private boolean shouldPlaceWithCurrentFacing(
            net.minecraft.world.level.block.state.BlockState state) {
        Direction targetFacing = null;
        net.minecraft.world.level.block.Block block = state.getBlock();

        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            targetFacing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        } else if (state.hasProperty(BlockStateProperties.FACING)) {
            targetFacing = state.getValue(BlockStateProperties.FACING);
        } else {
            return true;
        }

        if (targetFacing.getAxis().isVertical()) {
            return true;
        }

        // The observer - by sight, other - facing the player
        Direction requiredLook;
        if (block instanceof net.minecraft.world.level.block.ObserverBlock) {
            requiredLook = targetFacing;
        } else {
            requiredLook = targetFacing.getOpposite();
        }

        float targetYaw = requiredLook.toYRot();
        float currentYaw = player.getYRot();
        float diff = Math.abs(Mth.wrapDegrees(currentYaw - targetYaw));

        return diff <= RESTRICT_ROTATION_TOLERANCE;
    }
}