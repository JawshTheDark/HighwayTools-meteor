package com.jawsh.highwaytools.trombone;

import com.jawsh.highwaytools.modules.HighwayTools;
import com.jawsh.highwaytools.trombone.handler.ContainerHandler;
import com.jawsh.highwaytools.trombone.handler.InventoryHandler;
import com.jawsh.highwaytools.trombone.task.BlockTask;
import com.jawsh.highwaytools.trombone.task.TaskManager;
import com.jawsh.highwaytools.trombone.task.TaskState;
import com.jawsh.highwaytools.trombone.util.Direction8;
import com.jawsh.highwaytools.trombone.util.TickTimer;
import com.jawsh.highwaytools.trombone.util.WorldUtils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class Pathfinder {
    public static volatile BlockPos goal = null;
    public static MovementState moveState = MovementState.RUNNING;

    public static final TickTimer rubberbandTimer = new TickTimer();

    public static Direction8 startingDirection = Direction8.NORTH;
    public static BlockPos currentBlockPos = new BlockPos(0, -1, 0);
    public static BlockPos startingBlockPos = new BlockPos(0, -1, 0);
    private static final BlockPos targetBlockPos = new BlockPos(0, -1, 0);
    public static int distancePending = 0;
    private static BlockPos lastCommandPos = new BlockPos(0, -1, 0);

    private static boolean sneaking = false;

    public enum MovementState {
        RUNNING, PICKUP, BRIDGE, RESTOCK
    }

    private static HighwayTools m() {
        return HighwayTools.INSTANCE;
    }

    public static void setupPathing() {
        moveState = MovementState.RUNNING;
        startingBlockPos = mc.player.blockPosition();
        currentBlockPos = startingBlockPos;
        lastCommandPos = startingBlockPos;
        startingDirection = Direction8.fromEntity(mc.player);
    }

    /** Sends the configured chat command every N blocks travelled (e.g. a sethome save point). */
    private static void checkIntervalCommand() {
        HighwayTools m = m();
        int interval = m.commandInterval.get();
        if (interval <= 0) return;

        double travelled = WorldUtils.distanceTo(lastCommandPos, currentBlockPos);
        if (travelled < interval) return;

        String command = m.intervalCommand.get().trim();
        if (command.isEmpty()) return;

        int totalDistance = (int) WorldUtils.distanceTo(startingBlockPos, currentBlockPos);
        command = command
            .replace("{x}", String.valueOf(currentBlockPos.getX()))
            .replace("{y}", String.valueOf(currentBlockPos.getY()))
            .replace("{z}", String.valueOf(currentBlockPos.getZ()))
            .replace("{distance}", String.valueOf(totalDistance));

        lastCommandPos = currentBlockPos;
        ChatUtils.sendPlayerMsg(command);
        if (m.debugLevel.get() != IO.DebugLevel.OFF) {
            IO.msg("Sent interval command after " + totalDistance + " blocks: §7" + command);
        }
    }

    public static void updatePathing() {
        switch (moveState) {
            case RUNNING -> {
                setSneak(false);
                goal = currentBlockPos;

                if (WorldUtils.distanceTo(currentBlockPos, targetBlockPos) < 2
                    || (distancePending > 0
                    && WorldUtils.distanceTo(startingDirection.offset(startingBlockPos, distancePending), currentBlockPos) == 0.0)) {
                    IO.disableError("Reached target destination");
                    return;
                }

                BlockPos possiblePos = startingDirection.offset(currentBlockPos);

                if (!isTaskDone(possiblePos.above())
                    || !isTaskDone(possiblePos)
                    || !isTaskDone(possiblePos.below())
                ) {
                    walkFallback();
                    return;
                }

                if (!checkForResidue(possiblePos.above())) {
                    walkFallback();
                    return;
                }

                if (WorldUtils.isReplaceable(possiblePos.below())) {
                    walkFallback();
                    return;
                }

                if (!currentBlockPos.equals(possiblePos)
                    && mc.player.position().distanceTo(WorldUtils.center(currentBlockPos)) < 2
                ) {
                    Statistics.simpleMovingAverageDistance.add(System.currentTimeMillis());
                    InventoryHandler.lastHitVec = null;
                    currentBlockPos = possiblePos;
                    TaskManager.populateTasks();
                    checkIntervalCommand();
                }

                walkFallback();
            }
            case BRIDGE -> {
                goal = null;
                boolean isAboveAir = WorldUtils.isReplaceable(mc.player.blockPosition().below());
                setSneak(isAboveAir);
                if (shouldBridge()) {
                    Vec3 target = WorldUtils.center(currentBlockPos).add(startingDirection.toVec3());
                    moveTo(target);
                } else {
                    if (!isAboveAir) {
                        moveState = MovementState.RUNNING;
                    }
                }
            }
            case PICKUP -> {
                setSneak(false);
                goal = ContainerHandler.getCollectingPosition();
                walkFallback();
            }
            case RESTOCK -> {
                setSneak(false);
                Vec3 target = WorldUtils.center(currentBlockPos);
                if (mc.player.position().distanceTo(target) < 2) {
                    goal = null;
                    moveTo(target);
                } else {
                    goal = currentBlockPos;
                    walkFallback();
                }
            }
        }
    }

    /** Without Baritone, walk straight towards the goal using velocity. */
    private static void walkFallback() {
        if (BaritoneBridge.available()) return;
        BlockPos target = goal;
        if (target == null) return;

        Vec3 center = WorldUtils.center(target);
        double dx = center.x - mc.player.getX();
        double dz = center.z - mc.player.getZ();
        if (dx * dx + dz * dz < 0.01) return;
        if (!mc.player.onGround()) return;

        moveTo(center);
    }

    private static void setSneak(boolean sneak) {
        if (sneak == sneaking) return;
        sneaking = sneak;
        mc.options.keyShift.setDown(sneak);
    }

    public static void releaseSneak() {
        setSneak(false);
    }

    private static boolean checkForResidue(BlockPos pos) {
        if (ContainerHandler.containerTask.taskState != TaskState.DONE) return false;
        for (BlockTask task : TaskManager.tasks.values()) {
            if (task.taskState != TaskState.DONE && TaskManager.isBehindPos(pos, task.blockPos)) return false;
        }
        return true;
    }

    private static boolean isTaskDone(BlockPos pos) {
        BlockTask task = TaskManager.tasks.get(pos);
        if (task == null) return false;
        Block block = WorldUtils.state(pos).getBlock();
        return task.taskState == TaskState.DONE
            && block != Blocks.NETHER_PORTAL
            && block != Blocks.END_PORTAL
            && !WorldUtils.isLiquid(pos);
    }

    public static boolean shouldBridge() {
        if (!m().scaffold.get()) return false;
        if (ContainerHandler.containerTask.taskState != TaskState.DONE) return false;

        BlockPos next = startingDirection.offset(currentBlockPos);
        if (!WorldUtils.isAir(next)) return false;
        if (!WorldUtils.isAir(next.above())) return false;
        if (!WorldUtils.isReplaceable(next.below())) return false;

        for (BlockTask task : TaskManager.tasks.values()) {
            if (task.taskState == TaskState.PENDING_PLACE) return false;
            if ((task.taskState == TaskState.PLACE || task.taskState == TaskState.LIQUID) && !task.sequence.isEmpty()) return false;
        }

        return true;
    }

    private static void moveTo(Vec3 target) {
        double speed = m().moveSpeed.get();
        Vec3 motion = mc.player.getDeltaMovement();
        double motionX = Mth.clamp(target.x - mc.player.getX(), -speed, speed);
        double motionZ = Mth.clamp(target.z - mc.player.getZ(), -speed, speed);
        mc.player.setDeltaMovement(motionX, motion.y, motionZ);
    }

    public static void updateProcess() {
        if (!Trombone.active) {
            Trombone.active = true;
            BaritoneBridge.registerProcess();
        }
    }

    public static void clearProcess() {
        Trombone.active = false;
        goal = null;
        releaseSneak();
    }
}
