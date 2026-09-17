package com.jawsh.highwaytools.trombone.handler;

import com.jawsh.highwaytools.modules.HighwayTools;
import com.jawsh.highwaytools.trombone.IO;
import com.jawsh.highwaytools.trombone.blueprint.BlueprintTask;
import com.jawsh.highwaytools.trombone.task.BlockTask;
import com.jawsh.highwaytools.trombone.task.TaskManager;
import com.jawsh.highwaytools.trombone.task.TaskState;
import com.jawsh.highwaytools.trombone.util.Coords;
import com.jawsh.highwaytools.trombone.util.WorldUtils;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class LiquidHandler {
    public static boolean handleLiquid(BlockTask blockTask) {
        HighwayTools m = HighwayTools.INSTANCE;
        boolean foundLiquid = false;

        for (Direction side : Direction.values()) {
            if (side == Direction.DOWN) continue;
            BlockPos neighbourPos = blockTask.blockPos.relative(side);

            if (!WorldUtils.isLiquid(neighbourPos)) continue;

            if (PlayerUtils.distanceTo(neighbourPos) > m.maxReach.get()
                || WorldUtils.getNeighbourSequence(neighbourPos, m.placementSearch.get(), m.maxReach.get(), !m.illegalPlacements.get()).isEmpty()
            ) {
                if (m.debugLevel.get() == IO.DebugLevel.VERBOSE) {
                    MeteorClient.LOG.info("[HighwayTools] Skipping liquid block at {} due to distance", Coords.asString(neighbourPos));
                }
                blockTask.updateState(TaskState.DONE);
                return true;
            }

            foundLiquid = true;

            BlockTask existing = TaskManager.tasks.get(neighbourPos);
            if (existing != null) {
                updateLiquidTask(existing);
            } else {
                BlockTask newTask = new BlockTask(neighbourPos, TaskState.LIQUID, m.fillerMat.get());
                BlueprintTask blueprintTask = new BlueprintTask(m.fillerMat.get(), true, false);
                TaskManager.addTask(newTask, blueprintTask);
            }
        }

        return foundLiquid;
    }

    public static void updateLiquidTask(BlockTask blockTask) {
        blockTask.updateState(TaskState.LIQUID);
        blockTask.updateTask();
    }
}
