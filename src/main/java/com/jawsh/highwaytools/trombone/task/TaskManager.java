package com.jawsh.highwaytools.trombone.task;

import com.jawsh.highwaytools.modules.HighwayTools;
import com.jawsh.highwaytools.trombone.IO;
import com.jawsh.highwaytools.trombone.Pathfinder;
import com.jawsh.highwaytools.trombone.blueprint.BlueprintGenerator;
import com.jawsh.highwaytools.trombone.blueprint.BlueprintTask;
import com.jawsh.highwaytools.trombone.handler.ContainerHandler;
import com.jawsh.highwaytools.trombone.handler.InventoryHandler;
import com.jawsh.highwaytools.trombone.interaction.Place;
import com.jawsh.highwaytools.trombone.util.Coords;
import com.jawsh.highwaytools.trombone.util.InvHelper;
import com.jawsh.highwaytools.trombone.util.WorldUtils;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.Names;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class TaskManager {
    public static final Map<BlockPos, BlockTask> tasks = new ConcurrentHashMap<>();
    public static final List<BlockTask> sortedTasks = new ArrayList<>();
    public static BlockTask lastTask = null;

    private static HighwayTools m() {
        return HighwayTools.INSTANCE;
    }

    public static void populateTasks() {
        BlueprintGenerator.generateBluePrint();

        /* Generate tasks based on the blueprint */
        for (Map.Entry<BlockPos, BlueprintTask> entry : BlueprintGenerator.blueprint.entrySet()) {
            generateTask(entry.getKey(), entry.getValue());
        }

        /* Remove old tasks */
        double maxReach = m().maxReach.get();
        for (Map.Entry<BlockPos, BlockTask> entry : tasks.entrySet()) {
            BlockTask task = entry.getValue();
            if (task.taskState != TaskState.DONE) continue;
            if (WorldUtils.distanceTo(Pathfinder.currentBlockPos, entry.getKey()) <= maxReach + 2) continue;

            if (task.toRemove) {
                if (System.currentTimeMillis() - task.timestamp > 1000L) tasks.remove(entry.getKey());
            } else {
                task.toRemove = true;
                task.timestamp = System.currentTimeMillis();
            }
        }
    }

    private static void generateTask(BlockPos blockPos, BlueprintTask blueprintTask) {
        HighwayTools m = m();
        BlockState currentState = WorldUtils.state(blockPos);
        Vec3 eyePos = mc.player.getEyePosition(1f);
        double maxReach = m.maxReach.get();

        /* start padding */
        if (startPadding(blockPos)) return;

        /* out of reach */
        if (eyePos.distanceTo(WorldUtils.center(blockPos)) >= maxReach + 1) return;

        /* do not override container task */
        if (ContainerHandler.containerTask.blockPos.equals(blockPos)) return;

        /* ignored blocks */
        if (shouldBeIgnored(blockPos, currentState)) {
            addTask(new BlockTask(blockPos, TaskState.DONE, currentState.getBlock()), blueprintTask);
            return;
        }

        /* is in desired state */
        if (currentState.getBlock() == blueprintTask.targetBlock()) {
            addTask(new BlockTask(blockPos, TaskState.DONE, currentState.getBlock()), blueprintTask);
            return;
        }

        /* is liquid */
        if (WorldUtils.isLiquid(currentState)) {
            BlockTask blockTask = new BlockTask(blockPos, TaskState.LIQUID, blueprintTask.targetBlock());
            blockTask.updateTask();
            if (!blockTask.sequence.isEmpty()) addTask(blockTask, blueprintTask);
            return;
        }

        /* to place */
        if (currentState.canBeReplaced() && blueprintTask.targetBlock() != Blocks.AIR) {
            /* support not needed */
            if (blueprintTask.isSupport() && WorldUtils.state(blockPos.above()).getBlock() == m.material.get()) {
                addTask(new BlockTask(blockPos, TaskState.DONE, currentState.getBlock()), blueprintTask);
                return;
            }

            /* is blocked by entity */
            if (!WorldUtils.noEntityCollision(blockPos)) {
                addTask(new BlockTask(blockPos, TaskState.DONE, currentState.getBlock()), blueprintTask);
                return;
            }

            BlockTask blockTask = new BlockTask(blockPos, TaskState.PLACE, blueprintTask.targetBlock());
            blockTask.updateTask();

            if (!blockTask.sequence.isEmpty()) {
                addTask(blockTask, blueprintTask);
            } else {
                blockTask.updateState(TaskState.IMPOSSIBLE_PLACE);
                addTask(blockTask, blueprintTask);
            }
            return;
        }

        /* To break */
        /* Is already filled */
        if (blueprintTask.isFiller()) {
            addTask(new BlockTask(blockPos, TaskState.DONE, currentState.getBlock()), blueprintTask);
            return;
        }

        BlockTask blockTask = new BlockTask(blockPos, TaskState.BREAK, blueprintTask.targetBlock());
        blockTask.updateTask();

        if (blockTask.eyeDistance < maxReach) {
            addTask(blockTask, blueprintTask);
        }
    }

    public static void runTasks() {
        HighwayTools m = m();
        BlockTask containerTask = ContainerHandler.containerTask;

        /* Finish the container task first */
        if (containerTask.taskState != TaskState.DONE) {
            containerTask.updateTask();
            if (containerTask.stuckTicks > containerTask.taskState.stuckTimeout) {
                if (containerTask.taskState == TaskState.PICKUP) Pathfinder.moveState = Pathfinder.MovementState.RUNNING;

                IO.warn("Failed container action " + containerTask.taskState.name() + " with "
                    + BuiltInRegistries.ITEM.getKey(containerTask.item) + "@(" + Coords.asString(containerTask.blockPos)
                    + ") stuck for " + containerTask.stuckTicks + " ticks");
                containerTask.updateState(TaskState.DONE);
            } else {
                for (BlockTask task : tasks.values()) {
                    TaskExecutor.doTask(task, true);
                }
                TaskExecutor.doTask(containerTask, false);
            }
            return;
        }

        /* Check tools */
        if (m.storageManagement.get()
            && InvHelper.countItem(stack -> stack.is(ItemTags.PICKAXES)) <= m.saveTools.get()) {
            ContainerHandler.handleRestock(m.tool.get());
            return;
        }

        /* Fulfill basic needs */
        if (m.storageManagement.get()
            && m.manageFood.get()
            && InvHelper.countItem(InvHelper::isFood) <= m.saveFood.get()) {
            ContainerHandler.handleRestock(m.food.get());
            return;
        }

        /* Restock obsidian if needed */
        if (m.storageManagement.get() && ContainerHandler.grindCycles > 0 && m.material.get() == Blocks.OBSIDIAN) {
            ContainerHandler.handleRestock(m.material.get().asItem());
            return;
        }

        /* Actually run the tasks */
        InventoryHandler.waitTicks--;

        /* Only update tasks to check for changed circumstances */
        for (BlockTask task : tasks.values()) {
            TaskExecutor.doTask(task, true);
            task.updateTask();
            if (m.multiBuilding.get()) task.shuffle();
        }

        sortedTasks.clear();
        sortedTasks.addAll(tasks.values());
        sortedTasks.sort(blockTaskComparator());

        for (BlockTask task : sortedTasks) {
            if (!checkStuckTimeout(task)) return;
            if (task.taskState != TaskState.DONE && InventoryHandler.waitTicks > 0) return;

            TaskExecutor.doTask(task, false);
            switch (task.taskState) {
                case DONE, BROKEN, PLACED -> {
                    // continue with next task
                }
                default -> {
                    lastTask = task;
                    return;
                }
            }
        }
    }

    public static void addTask(BlockTask blockTask, BlueprintTask blueprintTask) {
        blockTask.updateTask();
        blockTask.isFiller = blueprintTask.isFiller();
        blockTask.isSupport = blueprintTask.isSupport();

        BlockTask existing = tasks.get(blockTask.blockPos);
        if (existing != null) {
            if (existing.stuckTicks > existing.taskState.stuckTimeout
                || blockTask.taskState == TaskState.LIQUID
                || (existing.taskState != blockTask.taskState
                && (existing.taskState == TaskState.DONE
                || existing.taskState == TaskState.IMPOSSIBLE_PLACE
                || (existing.taskState == TaskState.PLACE
                && !WorldUtils.isPlaceable(existing.blockPos))))) {
                tasks.put(blockTask.blockPos, blockTask);
            }
        } else {
            tasks.put(blockTask.blockPos, blockTask);
        }
    }

    private static boolean checkStuckTimeout(BlockTask blockTask) {
        HighwayTools m = m();
        int timeout = blockTask.taskState.stuckTimeout;

        if (blockTask.stuckTicks < timeout) return true;
        if (blockTask.taskState == TaskState.DONE) return true;

        if (blockTask.taskState == TaskState.PENDING_BREAK) {
            blockTask.updateState(TaskState.BREAK);
            return false;
        }

        if (blockTask.taskState == TaskState.PENDING_PLACE) {
            blockTask.updateState(TaskState.PLACE);
            return false;
        }

        if (m.debugLevel.get() != IO.DebugLevel.OFF) {
            if (!m.anonymizeStats.get()) {
                IO.msg("Stuck while " + blockTask.taskState + "@(" + Coords.asString(blockTask.blockPos) + ") for more than " + timeout + " ticks (" + blockTask.stuckTicks + "), refreshing data.");
            } else {
                IO.msg("Stuck while " + blockTask.taskState + " for more than " + timeout + " ticks (" + blockTask.stuckTicks + "), refreshing data.");
            }
        }

        switch (blockTask.taskState) {
            case PLACE -> {
                if (m.dynamicDelay.get() && Place.extraPlaceDelay < 10 && Pathfinder.moveState != Pathfinder.MovementState.BRIDGE) {
                    Place.extraPlaceDelay += 1;
                }
            }
            case PICKUP -> {
                BlockTask containerTask = ContainerHandler.containerTask;
                IO.msg("Can't pickup " + BuiltInRegistries.ITEM.getKey(containerTask.item) + "@(" + Coords.asString(containerTask.blockPos) + ")");
                blockTask.updateState(TaskState.DONE);
                Pathfinder.moveState = Pathfinder.MovementState.RUNNING;
            }
            default -> blockTask.updateState(TaskState.DONE);
        }
        return false;
    }

    private static boolean startPadding(BlockPos c) {
        return isBehindPos(Pathfinder.startingDirection.offset(Pathfinder.startingBlockPos), c);
    }

    public static boolean isBehindPos(BlockPos origin, BlockPos check) {
        int width = m().width.get();
        BlockPos a = Pathfinder.startingDirection.counterClockwise(2).offset(origin, width);
        BlockPos b = Pathfinder.startingDirection.clockwise(2).offset(origin, width);

        return ((b.getX() - a.getX()) * (check.getZ() - a.getZ()) - (b.getZ() - a.getZ()) * (check.getX() - a.getX())) > 0;
    }

    private static boolean shouldBeIgnored(BlockPos blockPos, BlockState currentState) {
        return m().isIgnored(currentState.getBlock())
            && !BlueprintGenerator.isInsideBlueprintBuild(blockPos)
            && !Pathfinder.startingDirection.offset(Pathfinder.currentBlockPos).equals(blockPos);
    }

    public static void clearTasks() {
        tasks.clear();
        sortedTasks.clear();
        ContainerHandler.containerTask.updateState(TaskState.DONE);
        lastTask = null;
        ContainerHandler.grindCycles = 0;
    }

    private static Comparator<BlockTask> blockTaskComparator() {
        boolean bridge = Pathfinder.moveState == Pathfinder.MovementState.BRIDGE;
        boolean multiBuilding = m().multiBuilding.get();

        return Comparator.<BlockTask>comparingInt(t -> t.taskState.ordinal())
            .thenComparingInt(t -> t.stuckTicks)
            .thenComparingInt(t -> t.isLiquidSource ? 0 : 1)
            .thenComparingDouble(t -> {
                if (bridge) {
                    return t.sequence.isEmpty() ? 69 : t.sequence.size();
                } else {
                    return multiBuilding ? t.shuffle : t.startDistance;
                }
            })
            .thenComparingDouble(t -> t.eyeDistance);
    }

    public static String describe(BlockTask task) {
        return Names.get(task.targetBlock) + " " + task.taskState.name();
    }
}
