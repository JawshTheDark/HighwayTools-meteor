package com.jawsh.highwaytools.trombone.task;

import com.jawsh.highwaytools.modules.HighwayTools;
import com.jawsh.highwaytools.trombone.IO;
import com.jawsh.highwaytools.trombone.Pathfinder;
import com.jawsh.highwaytools.trombone.Statistics;
import com.jawsh.highwaytools.trombone.Trombone;
import com.jawsh.highwaytools.trombone.blueprint.BlueprintGenerator;
import com.jawsh.highwaytools.trombone.handler.ContainerHandler;
import com.jawsh.highwaytools.trombone.handler.InventoryHandler;
import com.jawsh.highwaytools.trombone.handler.LiquidHandler;
import com.jawsh.highwaytools.trombone.interaction.Break;
import com.jawsh.highwaytools.trombone.interaction.Place;
import com.jawsh.highwaytools.trombone.util.Coords;
import com.jawsh.highwaytools.trombone.util.InvHelper;
import com.jawsh.highwaytools.trombone.util.TickTimer;
import com.jawsh.highwaytools.trombone.util.WorldUtils;
import meteordevelopment.meteorclient.utils.misc.Names;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class TaskExecutor {
    private static final TickTimer restockTimer = new TickTimer();

    private static HighwayTools m() {
        return HighwayTools.INSTANCE;
    }

    public static void doTask(BlockTask blockTask, boolean updateOnly) {
        if (!updateOnly) blockTask.onTick();

        switch (blockTask.taskState) {
            case RESTOCK -> {
                if (!updateOnly) doRestock();
            }
            case PICKUP -> {
                if (!updateOnly) doPickup();
            }
            case OPEN_CONTAINER -> {
                if (!updateOnly) doOpenContainer();
            }
            case BREAKING -> doBreaking(blockTask, updateOnly);
            case BROKEN -> doBroken(blockTask);
            case PLACED -> doPlaced(blockTask);
            case BREAK -> doBreak(blockTask, updateOnly);
            case PLACE, LIQUID -> doPlace(blockTask, updateOnly);
            case PENDING_BREAK, PENDING_PLACE -> blockTask.onStuck();
            case IMPOSSIBLE_PLACE -> {
                if (!updateOnly) doImpossiblePlace();
            }
            case DONE -> { /* do nothing */ }
        }
    }

    private static boolean isEject(ItemStack stack) {
        return !stack.isEmpty() && m().ejectItems.get().contains(stack.getItem());
    }

    private static void doRestock() {
        HighwayTools m = m();
        BlockTask containerTask = ContainerHandler.containerTask;
        AbstractContainerMenu container = mc.player.containerMenu;

        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?>) && !containerTask.isLoaded) {
            containerTask.updateState(TaskState.OPEN_CONTAINER);
            return;
        }

        if (container.slots.size() != 63) {
            IO.disableError("Inventory container changed. Current container has " + container.slots.size() + " slots (id " + container.containerId + ").");
            return;
        }

        if (m.leaveEmptyShulkers.get()
            && !m.ejectItems.get().contains(containerTask.item)
            && containerTask.isShulker()
            && allContainerSlots(container, 0, 26, stack -> stack.isEmpty() || isEject(stack))
        ) {
            if (m.debugLevel.get() != IO.DebugLevel.OFF) {
                if (!m.anonymizeStats.get()) {
                    IO.msg("Left empty " + Names.get(containerTask.targetBlock) + "@(" + Coords.asString(containerTask.blockPos) + ")");
                } else {
                    IO.msg("Left empty " + Names.get(containerTask.targetBlock));
                }
            }

            containerTask.isOpen = false;
            mc.player.closeContainer();
            containerTask.updateState(TaskState.DONE);
            Pathfinder.moveState = Pathfinder.MovementState.RUNNING;
            return;
        }

        int freeSlots = 0;
        for (int i = 27; i <= 62; i++) {
            ItemStack stack = container.getSlot(i).getItem();
            if (stack.isEmpty() || isEject(stack)) freeSlots++;
        }
        freeSlots = freeSlots - 1 - m.keepFreeSlots.get();

        if (containerTask.stopPull || freeSlots < 1) {
            containerTask.updateState(TaskState.BREAK);
            containerTask.isOpen = false;
            mc.player.closeContainer();
            return;
        }

        int slot = firstContainerSlotWith(container, 0, 26, containerTask.item);
        if (slot != -1) {
            InventoryHandler.moveToInventory(slot, container);
            containerTask.stacksPulled++;
            containerTask.stopPull = true;
            if (m.fastFill.get()) {
                boolean tunnel = m.mode.get() == Trombone.Structure.TUNNEL;
                if (tunnel && containerTask.item == m.tool.get()) {
                    containerTask.stopPull = false;
                } else if (!tunnel && containerTask.item == m.material.get().asItem()) {
                    containerTask.stopPull = false;
                }
            }
        } else {
            if (containerTask.stacksPulled == 0) {
                int shulkerSlot = ContainerHandler.getShulkerWithInContainer(container, 0, 26, containerTask.item);
                if (shulkerSlot != -1) {
                    InventoryHandler.moveToInventory(shulkerSlot, container);
                    containerTask.stopPull = true;
                } else {
                    IO.disableError("No " + Names.get(containerTask.item) + " left in any container.");
                }
            } else {
                containerTask.updateState(TaskState.BREAK);
                containerTask.isOpen = false;
                mc.player.closeContainer();
            }
        }
    }

    private static boolean allContainerSlots(AbstractContainerMenu container, int from, int to, java.util.function.Predicate<ItemStack> predicate) {
        for (int i = from; i <= to; i++) {
            if (!predicate.test(container.getSlot(i).getItem())) return false;
        }
        return true;
    }

    private static int firstContainerSlotWith(AbstractContainerMenu container, int from, int to, Item item) {
        for (int i = from; i <= to; i++) {
            ItemStack stack = container.getSlot(i).getItem();
            if (!stack.isEmpty() && stack.is(item)) return i;
        }
        return -1;
    }

    private static void doPickup() {
        BlockTask containerTask = ContainerHandler.containerTask;

        if (ContainerHandler.getCollectingPosition() == null) {
            containerTask.updateState(TaskState.DONE);
            Pathfinder.moveState = Pathfinder.MovementState.RUNNING;
            return;
        }

        if (InvHelper.isFull() && restockTimer.tick(20)) {
            int ejectSlot = InventoryHandler.getEjectSlot();
            if (ejectSlot != -1) InvHelper.dropSlot(ejectSlot);
        } else {
            containerTask.onStuck();
        }
    }

    private static void doOpenContainer() {
        BlockTask containerTask = ContainerHandler.containerTask;
        Pathfinder.moveState = Pathfinder.MovementState.RESTOCK;

        if (containerTask.isOpen) {
            containerTask.updateState(TaskState.RESTOCK);
            return;
        }

        if (ContainerHandler.shulkerOpenTimer.tick(20)) {
            Vec3 center = WorldUtils.center(containerTask.blockPos);
            Vec3 diff = mc.player.getEyePosition(1f).subtract(center).normalize();

            Direction side = Direction.getApproximateNearest(diff);
            Vec3 hitVec = WorldUtils.getHitVec(containerTask.blockPos, side);
            InventoryHandler.lastHitVec = hitVec;

            mc.getConnection().send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, new BlockHitResult(hitVec, side, containerTask.blockPos, false), 0));
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    private static void doBreaking(BlockTask blockTask, boolean updateOnly) {
        BlockState state = WorldUtils.state(blockTask.blockPos);

        if (state.isAir()) {
            InventoryHandler.waitTicks = m().breakDelay.get();
            blockTask.updateState(TaskState.BROKEN);
            return;
        }

        if (WorldUtils.isLiquid(state)) {
            LiquidHandler.updateLiquidTask(blockTask);
            return;
        }

        if (!updateOnly
            && InventoryHandler.swapOrMoveBestTool(blockTask)
            && InventoryHandler.packetLimiter.size() < m().interactionLimit.get()
        ) {
            Break.mineBlock(blockTask);
        }
    }

    private static void doBroken(BlockTask blockTask) {
        HighwayTools m = m();
        BlockTask containerTask = ContainerHandler.containerTask;

        if (!WorldUtils.isAir(blockTask.blockPos)) {
            blockTask.updateState(TaskState.BREAK);
            return;
        }

        Statistics.totalBlocksBroken++;

        for (BlockTask task : TaskManager.tasks.values()) {
            if (task.taskState == TaskState.BREAK) task.resetStuck();
        }

        // Instant break exploit
        if (blockTask.blockPos.equals(Break.prePrimedPos)) {
            Break.primedPos = Break.prePrimedPos;
            Break.prePrimedPos = null;
        }

        Statistics.simpleMovingAverageBreaks.add(System.currentTimeMillis());

        // Sound
        if (m.fakeSounds.get()) {
            SoundType soundType = blockTask.targetBlock.defaultBlockState().getSoundType();
            mc.level.playSound(mc.player, blockTask.blockPos, soundType.getBreakSound(), SoundSource.BLOCKS, (soundType.getVolume() + 1.0f) / 2.0f, soundType.getPitch() * 0.8f);
        }

        if (blockTask == containerTask) {
            if (containerTask.collect) {
                Pathfinder.moveState = Pathfinder.MovementState.PICKUP;
                blockTask.updateState(TaskState.PICKUP);
            } else {
                blockTask.updateState(TaskState.DONE);
            }
            return;
        }

        if (blockTask.targetBlock == Blocks.AIR) {
            blockTask.updateState(TaskState.DONE);
        } else {
            blockTask.updateState(TaskState.PLACE);
        }
    }

    private static void doPlaced(BlockTask blockTask) {
        HighwayTools m = m();
        BlockTask containerTask = ContainerHandler.containerTask;
        BlockState currentState = WorldUtils.state(blockTask.blockPos);
        Block currentBlock = currentState.getBlock();

        if ((blockTask.targetBlock == currentBlock || blockTask.isFiller) && !currentState.canBeReplaced()) {
            Statistics.totalBlocksPlaced++;
            Break.prePrimedPos = blockTask.blockPos;
            Statistics.simpleMovingAveragePlaces.add(System.currentTimeMillis());

            if (m.dynamicDelay.get() && Place.extraPlaceDelay > 0) Place.extraPlaceDelay /= 2;

            if (blockTask == containerTask) {
                if (containerTask.destroy) {
                    containerTask.updateState(TaskState.BREAK);
                } else {
                    containerTask.updateState(TaskState.OPEN_CONTAINER);
                }
            } else {
                blockTask.updateState(TaskState.DONE);
            }

            for (BlockTask task : TaskManager.tasks.values()) {
                if (task.taskState == TaskState.PLACE) task.resetStuck();
            }

            if (m.fakeSounds.get()) {
                SoundType soundType = currentState.getSoundType();
                mc.level.playSound(mc.player, blockTask.blockPos, soundType.getPlaceSound(), SoundSource.BLOCKS, (soundType.getVolume() + 1.0f) / 2.0f, soundType.getPitch() * 0.8f);
            }
        } else if (blockTask.targetBlock == currentBlock && currentBlock == Blocks.AIR) {
            blockTask.updateState(TaskState.BREAK);
        } else if (blockTask.targetBlock == Blocks.AIR && currentBlock != Blocks.AIR) {
            blockTask.updateState(TaskState.BREAK);
        } else {
            blockTask.updateState(TaskState.PLACE);
        }
    }

    private static void doBreak(BlockTask blockTask, boolean updateOnly) {
        HighwayTools m = m();
        BlockState currentState = WorldUtils.state(blockTask.blockPos);
        Block currentBlock = currentState.getBlock();

        if ((m.isIgnored(currentBlock)
            && !blockTask.isShulker()
            && !BlueprintGenerator.isInsideBlueprintBuild(blockTask.blockPos))
            || currentBlock == Blocks.NETHER_PORTAL
            || currentBlock == Blocks.END_PORTAL
            || currentBlock == Blocks.END_PORTAL_FRAME
            || currentBlock == Blocks.BEDROCK
        ) {
            blockTask.updateState(TaskState.DONE);
            return;
        }

        if (blockTask.targetBlock == m.fillerMat.get()) {
            if (WorldUtils.state(blockTask.blockPos.above()).getBlock() == m.material.get()
                || (!WorldUtils.isPlaceable(blockTask.blockPos) && WorldUtils.hasCollision(blockTask.blockPos))) {
                blockTask.updateState(TaskState.DONE);
                return;
            }
        } else if (blockTask.targetBlock == m.material.get()) {
            if (currentBlock == m.material.get()) {
                blockTask.updateState(TaskState.DONE);
                return;
            }
        }

        if (currentState.isAir()) {
            if (blockTask.targetBlock == Blocks.AIR) {
                blockTask.updateState(TaskState.BROKEN);
            } else {
                blockTask.updateState(TaskState.PLACE);
            }
            return;
        }

        if (WorldUtils.isLiquid(currentState)) {
            LiquidHandler.updateLiquidTask(blockTask);
            return;
        }

        if (!updateOnly
            && mc.player.onGround()
            && InventoryHandler.swapOrMoveBestTool(blockTask)
            && !LiquidHandler.handleLiquid(blockTask)
            && InventoryHandler.packetLimiter.size() < m.interactionLimit.get()
        ) {
            Break.mineBlock(blockTask);
        }
    }

    private static void doPlace(BlockTask blockTask, boolean updateOnly) {
        HighwayTools m = m();
        BlockTask containerTask = ContainerHandler.containerTask;
        BlockState currentState = WorldUtils.state(blockTask.blockPos);
        Block currentBlock = currentState.getBlock();

        if (blockTask.taskState == TaskState.LIQUID && !WorldUtils.isLiquid(currentState)) {
            blockTask.updateState(TaskState.DONE);
            return;
        }

        if (blockTask.targetBlock == m.material.get()) {
            if (currentBlock == m.material.get()) {
                blockTask.updateState(TaskState.PLACED);
                return;
            }
        } else if (blockTask.targetBlock == m.fillerMat.get()) {
            if (currentBlock == m.fillerMat.get()) {
                blockTask.updateState(TaskState.PLACED);
                return;
            } else if (m.mode.get() == Trombone.Structure.HIGHWAY
                && WorldUtils.state(blockTask.blockPos.above()).getBlock() == m.material.get()) {
                blockTask.updateState(TaskState.DONE);
                return;
            }
        } else if (blockTask.targetBlock == Blocks.AIR) {
            if (!WorldUtils.isLiquid(currentState)) {
                if (!currentState.isAir()) {
                    blockTask.updateState(TaskState.BREAK);
                } else {
                    blockTask.updateState(TaskState.BROKEN);
                }
                return;
            }
        }

        if (updateOnly) return;

        if (!WorldUtils.isPlaceable(blockTask.blockPos)) {
            if (m.debugLevel.get() == IO.DebugLevel.VERBOSE) {
                if (!m.anonymizeStats.get()) {
                    IO.msg("Invalid place position @(" + Coords.asString(blockTask.blockPos) + ") Removing task");
                } else {
                    IO.msg("Invalid place position. Removing task");
                }
            }

            if (blockTask == containerTask) {
                IO.msg("Failed container task. Trying to break block.");
                containerTask.updateState(TaskState.BREAK);
            } else {
                TaskManager.tasks.remove(blockTask.blockPos);
            }
            return;
        }

        if (!InventoryHandler.swapOrMoveBlock(blockTask)) {
            blockTask.onStuck();
            return;
        }

        Place.placeBlock(blockTask);
    }

    private static void doImpossiblePlace() {
        if (Pathfinder.shouldBridge()
            && Pathfinder.moveState != Pathfinder.MovementState.RESTOCK
            && mc.player.position().distanceTo(WorldUtils.center(Pathfinder.currentBlockPos)) < 1
        ) {
            Pathfinder.moveState = Pathfinder.MovementState.BRIDGE;
        }
    }

    public static List<BlockTask> tasksSnapshot() {
        return List.copyOf(TaskManager.tasks.values());
    }
}
