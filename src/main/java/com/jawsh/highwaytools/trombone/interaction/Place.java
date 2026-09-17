package com.jawsh.highwaytools.trombone.interaction;

import com.jawsh.highwaytools.modules.HighwayTools;
import com.jawsh.highwaytools.trombone.IO;
import com.jawsh.highwaytools.trombone.Scheduler;
import com.jawsh.highwaytools.trombone.handler.ContainerHandler;
import com.jawsh.highwaytools.trombone.handler.InventoryHandler;
import com.jawsh.highwaytools.trombone.task.BlockTask;
import com.jawsh.highwaytools.trombone.task.TaskState;
import com.jawsh.highwaytools.trombone.util.Coords;
import com.jawsh.highwaytools.trombone.util.PlaceInfo;
import com.jawsh.highwaytools.trombone.util.WorldUtils;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class Place {
    public static int extraPlaceDelay = 0;

    private static HighwayTools m() {
        return HighwayTools.INSTANCE;
    }

    public static void placeBlock(BlockTask blockTask) {
        HighwayTools m = m();
        if (blockTask.sequence.isEmpty()) {
            if (m.debugLevel.get() == IO.DebugLevel.VERBOSE) {
                MeteorClient.LOG.warn("[HighwayTools] No neighbours found for {}", Coords.asString(blockTask.blockPos));
            }
            if (blockTask == ContainerHandler.containerTask) {
                IO.msg("Can't find neighbour blocks to place down the container.");
            }
            blockTask.onStuck(21);
            blockTask.updateState(TaskState.DONE);
            return;
        }

        PlaceInfo last = blockTask.sequence.getLast();
        InventoryHandler.lastHitVec = last.hitVec();

        placeBlockNormal(blockTask, last.pos(), last.side(), last.hitVec());
    }

    private static void placeBlockNormal(BlockTask blockTask, BlockPos placePos, Direction side, Vec3 hitVec) {
        HighwayTools m = m();
        Block currentBlock = WorldUtils.state(placePos).getBlock();
        boolean sneak = BlockUtils.isClickable(currentBlock);

        InventoryHandler.waitTicks = m.dynamicDelay.get()
            ? m.placeDelay.get() + extraPlaceDelay
            : m.placeDelay.get();
        blockTask.updateState(TaskState.PENDING_PLACE);

        if (sneak) {
            mc.getConnection().send(new ServerboundPlayerInputPacket(new Input(false, false, false, false, false, true, false)));
        }

        mc.getConnection().send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, new BlockHitResult(hitVec, side, placePos, false), 0));
        mc.player.swing(InteractionHand.MAIN_HAND);

        if (sneak) {
            Scheduler.schedule(1, () -> {
                if (mc.player == null) return;
                mc.getConnection().send(new ServerboundPlayerInputPacket(new Input(false, false, false, false, false, false, false)));
            });
        }

        Scheduler.schedule(m.taskTimeout.get(), () -> {
            if (blockTask.taskState == TaskState.PENDING_PLACE) {
                blockTask.updateState(TaskState.PLACE);
                if (m.dynamicDelay.get() && extraPlaceDelay < 10) extraPlaceDelay += 1;
            }
        });
    }
}
