package com.jawsh.highwaytools.trombone.handler;

import com.jawsh.highwaytools.trombone.Pathfinder;
import com.jawsh.highwaytools.trombone.Statistics;
import com.jawsh.highwaytools.trombone.blueprint.BlueprintGenerator;
import com.jawsh.highwaytools.trombone.task.BlockTask;
import com.jawsh.highwaytools.trombone.task.TaskManager;
import com.jawsh.highwaytools.trombone.task.TaskState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Handles incoming packets. Note: Meteor fires receive events on the network thread.
 */
public class PacketHandler {
    public static void handlePacket(Packet<?> packet) {
        BlockTask containerTask = ContainerHandler.containerTask;

        if (packet instanceof ClientboundBlockUpdatePacket blockUpdate) {
            BlockPos pos = blockUpdate.getPos();
            if (!BlueprintGenerator.isInsideBlueprint(pos) && !pos.equals(containerTask.blockPos)) return;

            Block prev = mc.level.getBlockState(pos).getBlock();
            Block updated = blockUpdate.getBlockState().getBlock();

            if (prev != updated) {
                BlockTask task;
                if (pos.equals(containerTask.blockPos)) {
                    task = containerTask;
                } else {
                    task = TaskManager.tasks.get(pos);
                    if (task == null) return;
                }

                switch (task.taskState) {
                    case PENDING_BREAK, BREAKING -> {
                        if (updated == Blocks.AIR) task.updateState(TaskState.BROKEN);
                    }
                    case PENDING_PLACE -> {
                        if (updated != Blocks.AIR && (task.targetBlock == updated || task.isFiller)) {
                            task.updateState(TaskState.PLACED);
                        }
                    }
                    default -> {
                        // Ignored
                    }
                }
            }
        } else if (packet instanceof ClientboundPlayerPositionPacket) {
            Pathfinder.rubberbandTimer.reset();
        } else if (packet instanceof ClientboundOpenScreenPacket openScreen) {
            if (containerTask.taskState != TaskState.DONE
                && ((openScreen.getType() == MenuType.SHULKER_BOX && containerTask.isShulker())
                || (openScreen.getType() == MenuType.GENERIC_9x3 && !containerTask.isShulker()))) {
                containerTask.isOpen = true;
            }
        } else if (packet instanceof ClientboundContainerSetContentPacket) {
            if (containerTask.isOpen) containerTask.isLoaded = true;
        } else if (packet instanceof ClientboundContainerSetSlotPacket setSlot) {
            int selected = mc.player.getInventory().getSelectedSlot();
            ItemStack currentStack = mc.player.getInventory().getItem(selected);
            ItemStack newStack = setSlot.getItem();
            if (setSlot.getSlot() == selected + 36
                && !newStack.isEmpty()
                && newStack.is(currentStack.getItem())
                && newStack.getDamageValue() > currentStack.getDamageValue()
            ) {
                Statistics.durabilityUsages += newStack.getDamageValue() - currentStack.getDamageValue();
            }
        }
    }
}
