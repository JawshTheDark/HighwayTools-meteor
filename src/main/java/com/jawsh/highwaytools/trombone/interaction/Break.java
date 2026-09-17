package com.jawsh.highwaytools.trombone.interaction;

import com.jawsh.highwaytools.modules.HighwayTools;
import com.jawsh.highwaytools.trombone.Scheduler;
import com.jawsh.highwaytools.trombone.handler.InventoryHandler;
import com.jawsh.highwaytools.trombone.handler.LiquidHandler;
import com.jawsh.highwaytools.trombone.task.BlockTask;
import com.jawsh.highwaytools.trombone.task.TaskManager;
import com.jawsh.highwaytools.trombone.task.TaskState;
import com.jawsh.highwaytools.trombone.util.PlaceInfo;
import com.jawsh.highwaytools.trombone.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class Break {
    public static BlockPos prePrimedPos = null;
    public static BlockPos primedPos = null;

    private static HighwayTools m() {
        return HighwayTools.INSTANCE;
    }

    public static void mineBlock(BlockTask blockTask) {
        HighwayTools m = m();
        BlockState blockState = WorldUtils.state(blockTask.blockPos);

        if (blockState.getBlock() instanceof BaseFireBlock) {
            PlaceInfo neighbour = WorldUtils.getNeighbour(blockTask.blockPos, m.maxReach.get(), !m.illegalPlacements.get());
            if (neighbour != null) {
                InventoryHandler.lastHitVec = neighbour.hitVec();
                extinguishFire(blockTask, neighbour.pos(), neighbour.side());
            } else {
                blockTask.updateState(TaskState.PLACE);
            }
        } else {
            int ticksNeeded = WorldUtils.ticksNeeded(blockTask.blockPos, m.miningSpeedFactor.get());

            Direction side = WorldUtils.getMiningSide(blockTask.blockPos, m.maxReach.get());
            if (side == null) {
                blockTask.onStuck();
                return;
            }

            if (blockTask.blockPos.equals(primedPos) && m.instantMine.get()) {
                side = side.getOpposite();
            }

            InventoryHandler.lastHitVec = WorldUtils.getHitVec(blockTask.blockPos, side);

            if (blockTask.ticksMined > ticksNeeded * 1.1 && blockTask.taskState == TaskState.BREAKING) {
                blockTask.updateState(TaskState.BREAK);
                blockTask.ticksMined = 0;
            }

            if (ticksNeeded == 1 || mc.player.getAbilities().instabuild) {
                mineBlockInstant(blockTask, side);
            } else {
                mineBlockNormal(blockTask, side, ticksNeeded);
            }
        }

        blockTask.ticksMined += 1;
    }

    private static void mineBlockInstant(BlockTask blockTask, Direction side) {
        HighwayTools m = m();
        InventoryHandler.waitTicks = m.breakDelay.get();
        blockTask.updateState(TaskState.PENDING_BREAK);

        sendMiningPackets(blockTask.blockPos, side, true, false, false);

        if (m.multiBreak.get()) tryMultiBreak(blockTask);

        Scheduler.schedule(m.taskTimeout.get(), () -> {
            if (blockTask.taskState == TaskState.PENDING_BREAK) {
                blockTask.updateState(TaskState.BREAK);
            }
        });
    }

    private static void tryMultiBreak(BlockTask blockTask) {
        HighwayTools m = m();
        Vec3 hitVec = InventoryHandler.lastHitVec;
        if (hitVec == null) return;

        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        Vec3 viewVec = hitVec.subtract(eyePos).normalize();

        for (BlockTask task : TaskManager.tasks.values()) {
            if (task.taskState != TaskState.BREAK || task == blockTask) continue;

            int ticks = WorldUtils.ticksNeeded(task.blockPos, m.miningSpeedFactor.get());
            if (ticks > 1) continue;

            if (InventoryHandler.packetLimiter.size() > m.interactionLimit.get() || LiquidHandler.handleLiquid(task)) return;

            BlockHitResult rayTrace = WorldUtils.rayTraceBlock(task.blockPos, eyePos, viewVec, m.maxReach.get());
            if (rayTrace == null) continue;

            task.updateState(TaskState.PENDING_BREAK);

            sendMiningPackets(task.blockPos, rayTrace.getDirection(), true, false, false);

            Scheduler.schedule(m.taskTimeout.get(), () -> {
                if (task.taskState == TaskState.PENDING_BREAK) {
                    task.updateState(TaskState.BREAK);
                }
            });
        }
    }

    private static void mineBlockNormal(BlockTask blockTask, Direction side, int ticks) {
        if (blockTask.taskState == TaskState.BREAK) {
            blockTask.updateState(TaskState.BREAKING);
            sendMiningPackets(blockTask.blockPos, side, true, false, false);
        } else {
            if (blockTask.ticksMined >= ticks) {
                sendMiningPackets(blockTask.blockPos, side, false, true, false);
            } else {
                sendMiningPackets(blockTask.blockPos, side, false, false, false);
            }
        }
    }

    private static void extinguishFire(BlockTask blockTask, BlockPos pos, Direction side) {
        HighwayTools m = m();
        InventoryHandler.waitTicks = m.breakDelay.get();
        blockTask.updateState(TaskState.PENDING_BREAK);

        sendMiningPackets(pos, side, true, false, true);

        Scheduler.schedule(m.taskTimeout.get(), () -> {
            if (blockTask.taskState == TaskState.PENDING_BREAK) {
                blockTask.updateState(TaskState.BREAK);
            }
        });
    }

    private static void sendMiningPackets(BlockPos pos, Direction side, boolean start, boolean stop, boolean abort) {
        boolean packetFlood = m().packetFlood.get();
        InventoryHandler.packetLimiter.add(System.currentTimeMillis());

        if (start || packetFlood) {
            mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, side));
        }
        if (abort) {
            mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, pos, side));
        }
        if (stop || packetFlood) {
            mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, side));
        }
        mc.player.swing(InteractionHand.MAIN_HAND);
    }
}
