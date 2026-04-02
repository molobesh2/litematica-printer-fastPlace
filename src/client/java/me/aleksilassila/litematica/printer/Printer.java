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
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.BlockHitResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Printer {
    public static final Logger logger = LogManager.getLogger(PrinterReference.MOD_ID);
    
    private static final java.util.Map<BlockPos, Long> PENDING_BLOCKS = new java.util.concurrent.ConcurrentHashMap<>();

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
        long now = System.currentTimeMillis();
        PENDING_BLOCKS.entrySet().removeIf(entry -> now - entry.getValue() > 100);

        if (Hotkeys.TOGGLE_ACCURATE_MODE.getKeybind().isPressed()) {
            Configs.ACCURATE_MODE.setBooleanValue(!Configs.ACCURATE_MODE.getBooleanValue());
        }

        WorldSchematic worldSchematic = SchematicWorldHandler.getSchematicWorld();
        if (worldSchematic == null || !actionHandler.acceptsActions()) return false;
        if (!Configs.PRINT_MODE.getBooleanValue() && !Hotkeys.PRINT.getKeybind().isPressed()) return false;

        PlayerAbilities abilities = player.getAbilities();
        if (!abilities.allowModifyWorld) return false;

        // Костыль для поворота (rotation cooldown)
        Direction currentFacing = player.getHorizontalFacing();
        if (lastHorizontalFacing != null && currentFacing != lastHorizontalFacing) {
             rotationCooldown = 3;
        }
        lastHorizontalFacing = currentFacing;
        if (rotationCooldown > 0) {
            rotationCooldown--;
            return false;
        }

        int blocksFoundThisTick = 0;
        int maxBlocks = Configs.BLOCKS_PER_TICK.getIntegerValue();
        if (Configs.ACCURATE_MODE.getBooleanValue()) {
            maxBlocks = 1;
            if (player.age % 2 != 0) return false;
        }

        boolean restrictRotation = Configs.RESTRICT_ROTATION.getBooleanValue();

        List<BlockPos> reachable = getReachablePositions();
        reachable.sort(Comparator.comparingInt(BlockPos::getY).thenComparingDouble(p -> player.squaredDistanceTo(Vec3d.ofCenter(p))));

        float initialYaw = player.getYaw();
        float initialPitch = player.getPitch();

        for (BlockPos pos : reachable) {
            if (blocksFoundThisTick >= maxBlocks) break;
            if (PENDING_BLOCKS.containsKey(pos)) continue;

            SchematicBlockState state = new SchematicBlockState(MinecraftClient.getInstance().world, worldSchematic, pos);
            if (state.targetState.equals(state.currentState) || state.targetState.isAir()) continue;

            BlockState targetState = state.targetState;

            // === ФИКС НАПРАВЛЕНИЯ ВЗГЛЯДА (RESTRICT ROTATION) ===
            if (restrictRotation && isStrictDirectionalBlock(targetState.getBlock())) {
                if (!shouldPlaceWithCurrentFacing(targetState, currentFacing)) {
                    continue; // Пропускаем блок, если игрок стоит не в ту сторону
                }
            }
            // ====================================================

            // AirPlace фикс
            MinecraftClient.getInstance().crosshairTarget = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);

            Guide[] guides = interactionGuides.getInteractionGuides(state);
            for (Guide guide : guides) {
                if (guide.canExecute(player)) {
                    List<Action> actions = new ArrayList<>(guide.execute(player));
                    if (actions.isEmpty()) continue;

                    Vec3d rotation = calculateLookAt(pos);
                    float lookYaw = (float) rotation.x;
                    float lookPitch = (float) rotation.y;

                    if (!(actions.get(0) instanceof PrepareAction)) {
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

                    applyRotation(lookYaw, lookPitch);
                    actionHandler.addActions(actions.toArray(new Action[0]));
                    PENDING_BLOCKS.put(pos, now);
                    blocksFoundThisTick++;
                    applyRotation(initialYaw, initialPitch);
                    break;
                }
            }
        }
        return blocksFoundThisTick > 0;
    }

    // === ЛОГИКА НАПРАВЛЕНИЙ ===
    private boolean isStrictDirectionalBlock(Block block) {
        return block instanceof PistonBlock || 
               block instanceof ObserverBlock || 
               block instanceof DispenserBlock ||
               block instanceof HopperBlock ||
               block instanceof AbstractFurnaceBlock ||
               block instanceof ShulkerBoxBlock ||
               block instanceof BarrelBlock ||
               block instanceof ChestBlock;
    }

    private boolean shouldPlaceWithCurrentFacing(BlockState state, Direction currentFacing) {
        if (state.contains(Properties.HORIZONTAL_FACING)) {
            return state.get(Properties.HORIZONTAL_FACING) == currentFacing.getOpposite();
        }
        if (state.contains(Properties.FACING)) {
            Direction targetDir = state.get(Properties.FACING);
            if (targetDir.getAxis().isVertical()) return true;
            return targetDir == currentFacing.getOpposite();
        }
        return true;
    }
    // ==========================

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

    private List<BlockPos> getReachablePositions() {
        int maxReach = (int) Math.ceil(Configs.PRINTING_RANGE.getDoubleValue());
        double maxReachSquared = MathHelper.square(Configs.PRINTING_RANGE.getDoubleValue());
        List<BlockPos> positions = new ArrayList<>();
        for (int y = -maxReach; y <= maxReach; y++) {
            for (int x = -maxReach; x <= maxReach; x++) {
                for (int z = -maxReach; z <= maxReach; z++) {
                    BlockPos blockPos = player.getBlockPos().add(x, y, z);
                    if (!DataManager.getRenderLayerRange().isPositionWithinRange(blockPos)) continue;
                    if (player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(blockPos)) > maxReachSquared) continue;
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