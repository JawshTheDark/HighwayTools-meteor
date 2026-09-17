package com.jawsh.highwaytools.trombone.task;

import com.jawsh.highwaytools.modules.HighwayTools;
import com.jawsh.highwaytools.trombone.Pathfinder;
import com.jawsh.highwaytools.trombone.util.Coords;
import com.jawsh.highwaytools.trombone.util.PlaceInfo;
import com.jawsh.highwaytools.trombone.util.WorldUtils;
import meteordevelopment.meteorclient.utils.misc.Names;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class BlockTask {
    public final BlockPos blockPos;
    public volatile TaskState taskState;
    public Block targetBlock;
    public boolean isSupport;
    public boolean isFiller;
    public Item item;

    private int ranTicks = 0;
    public int stuckTicks = 0;
    public int shuffle = 0;
    public double startDistance = 0.0;
    public double eyeDistance = 0.0;

    public List<PlaceInfo> sequence = List.of();
    public boolean isLiquidSource = false;

    public boolean isOpen = false;
    public boolean stopPull = false;
    public int stacksPulled = 0;
    public boolean isLoaded = false;
    public Item collectItem = Items.AIR;
    public boolean destroy = false;
    public boolean collect = true;

    public long timestamp = System.currentTimeMillis();
    public AABB aabb;

    public boolean toRemove = false;
    public int ticksMined = 1;
    public ItemStack toolToUse = ItemStack.EMPTY;

    public BlockTask(BlockPos blockPos, TaskState taskState, Block targetBlock) {
        this(blockPos, taskState, targetBlock, Items.AIR);
    }

    public BlockTask(BlockPos blockPos, TaskState taskState, Block targetBlock, Item item) {
        this.blockPos = blockPos.immutable();
        this.taskState = taskState;
        this.targetBlock = targetBlock;
        this.item = item;
        this.aabb = new AABB(this.blockPos);
    }

    public void updateState(TaskState state) {
        if (state != taskState) {
            timestamp = System.currentTimeMillis();
            stuckTicks = 0;
            ranTicks = 0;
            taskState = state;
        }
    }

    public void onTick() {
        ranTicks++;
        if (ranTicks > taskState.stuckThreshold) {
            stuckTicks++;
        }
    }

    public void onStuck() {
        onStuck(1);
    }

    public void onStuck(int weight) {
        stuckTicks += weight;
    }

    public void resetStuck() {
        stuckTicks = 0;
    }

    public void updateTask() {
        HighwayTools m = HighwayTools.INSTANCE;
        isLiquidSource = WorldUtils.isLiquidSource(WorldUtils.state(blockPos));

        switch (taskState) {
            case PLACE, LIQUID -> sequence = WorldUtils.getNeighbourSequence(
                blockPos, m.placementSearch.get(), m.maxReach.get(), !m.illegalPlacements.get()
            );
            default -> {}
        }

        startDistance = WorldUtils.center(Pathfinder.startingBlockPos).distanceTo(WorldUtils.center(blockPos));
        eyeDistance = mc.player.getEyePosition(1f).distanceTo(WorldUtils.center(blockPos));
        aabb = WorldUtils.getBox(blockPos);
    }

    public boolean isShulker() {
        return targetBlock instanceof ShulkerBoxBlock;
    }

    public void shuffle() {
        shuffle = ThreadLocalRandom.current().nextInt(0, 1000);
    }

    public String prettyPrint() {
        return "    " + Names.get(targetBlock) + "@(" + Coords.asString(blockPos) + ") State: " + taskState
            + " Timings: (Threshold: " + taskState.stuckThreshold + " Timeout: " + taskState.stuckTimeout + ")"
            + " Priority: " + taskState.ordinal() + " Stuck: " + stuckTicks;
    }

    @Override
    public String toString() {
        return "Block: " + Names.get(targetBlock) + " @ Position: (" + Coords.asString(blockPos) + ") State: " + taskState.name();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof BlockTask task && blockPos.equals(task.blockPos));
    }

    @Override
    public int hashCode() {
        return blockPos.hashCode();
    }
}
