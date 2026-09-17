package com.jawsh.highwaytools.trombone.handler;

import com.jawsh.highwaytools.modules.HighwayTools;
import com.jawsh.highwaytools.trombone.IO;
import com.jawsh.highwaytools.trombone.task.BlockTask;
import com.jawsh.highwaytools.trombone.task.TaskState;
import com.jawsh.highwaytools.trombone.util.InvHelper;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.Names;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class InventoryHandler {
    public static volatile Vec3 lastHitVec = null;
    public static int waitTicks = 0;

    public static final ConcurrentLinkedDeque<Long> packetLimiter = new ConcurrentLinkedDeque<>();

    private static HighwayTools m() {
        return HighwayTools.INSTANCE;
    }

    public static void updateRotation() {
        Vec3 hitVec = lastHitVec;
        if (hitVec == null) return;
        Rotations.rotate(Rotations.getYaw(hitVec), Rotations.getPitch(hitVec));
    }

    private static boolean isEject(ItemStack stack) {
        return !stack.isEmpty() && m().ejectItems.get().contains(stack.getItem());
    }

    /** Inventory index of the fastest tool for the task's block. Hotbar slots win ties. */
    private static int getBestTool(BlockTask blockTask) {
        BlockState state = mc.level.getBlockState(blockTask.blockPos);
        int best = -1;
        float bestSpeed = -1f;

        for (int i = InvHelper.SIZE - 1; i >= 0; i--) {
            ItemStack stack = InvHelper.get(i);
            float speed;
            if (stack.isEmpty()) {
                speed = 0f;
            } else {
                speed = stack.getDestroySpeed(state);
                if (speed > 1.0f) {
                    int efficiency = Utils.getEnchantmentLevel(stack, Enchantments.EFFICIENCY);
                    if (efficiency > 0) speed += efficiency * efficiency + 1.0f;
                }
            }

            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = i;
            }
        }

        return best;
    }

    public static boolean swapOrMoveBlock(BlockTask blockTask) {
        if (blockTask.isShulker()) {
            int slot = ContainerHandler.getShulkerWith(blockTask.item);
            if (slot != -1) {
                blockTask.collectItem = InvHelper.get(slot).getItem();
                InvHelper.swapOrMoveToHand(slot);
            }
            return true;
        }

        Block useMat = findMaterial(blockTask);
        if (useMat == Blocks.AIR) return false;

        boolean success = InvHelper.swapToBlockOrMove(useMat);
        if (!success) {
            IO.disableError("Inventory transaction of " + Names.get(useMat) + " failed.");
            return false;
        }
        return true;
    }

    private static Block findMaterial(BlockTask blockTask) {
        HighwayTools m = m();
        Block material = m.material.get();

        if (blockTask.targetBlock == material) {
            if (InvHelper.countBlock(material) > m.saveMaterial.get()) {
                return material;
            }
            restockFallback(blockTask);
            return Blocks.AIR;
        }

        if (InvHelper.countBlock(blockTask.targetBlock) > 0) {
            return blockTask.targetBlock;
        }

        Set<Block> possibleMaterials = new LinkedHashSet<>();
        for (Item item : m.ejectItems.get()) {
            Block block = Block.byItem(item);
            if (block != Blocks.AIR && InvHelper.countBlock(block) > 0) possibleMaterials.add(block);
        }

        if (possibleMaterials.isEmpty()) {
            if (InvHelper.countBlock(material) > m.saveMaterial.get()) {
                return material;
            }
            restockFallback(blockTask);
            return Blocks.AIR;
        }

        return possibleMaterials.iterator().next();
    }

    private static void restockFallback(BlockTask blockTask) {
        if (m().storageManagement.get()) {
            ContainerHandler.handleRestock(blockTask.targetBlock.asItem());
        } else {
            IO.disableError("No usable material was found in inventory.");
        }
    }

    public static boolean swapOrMoveBestTool(BlockTask blockTask) {
        HighwayTools m = m();
        if (InvHelper.countItem(m.tool.get()) <= m.saveTools.get()) {
            if (ContainerHandler.containerTask.taskState == TaskState.DONE && m.storageManagement.get()) {
                ContainerHandler.handleRestock(m.tool.get());
                return false;
            }
            return swapOrMoveTool(blockTask);
        }

        return swapOrMoveTool(blockTask);
    }

    public static void zipInventory() {
        List<Integer> compressible = new ArrayList<>();
        for (int i = 0; i < InvHelper.SIZE; i++) {
            ItemStack stack = InvHelper.get(i);
            if (stack.isEmpty()) continue;
            if (stack.getCount() >= stack.getMaxStackSize()) continue;
            Item item = stack.getItem();
            if (InvHelper.countStacks(s -> s.is(item)) > 1) compressible.add(i);
        }

        if (compressible.isEmpty()) {
            IO.disableError("Inventory full. (Considering that " + m().keepFreeSlots.get() + " slots are supposed to stay free)");
            return;
        }

        for (int index : compressible) {
            InvHelper.click(InvHelper.indexToId(index), 0, ContainerInput.QUICK_MOVE);
        }
    }

    private static boolean swapOrMoveTool(BlockTask blockTask) {
        int slot = getBestTool(blockTask);
        if (slot == -1) return false;

        blockTask.toolToUse = InvHelper.get(slot);
        InvHelper.swapOrMoveToHand(slot);
        return true;
    }

    /**
     * Moves the stack in the container slot {@code originSlot} into the player inventory part of the open container.
     * Container layout: 0-26 container, 27-53 main inventory, 54-62 hotbar.
     */
    public static void moveToInventory(int originSlot, AbstractContainerMenu container) {
        ItemStack origin = container.getSlot(originSlot).getItem();

        // Merge into an existing stack
        for (int i = 27; i <= 62; i++) {
            ItemStack stack = container.getSlot(i).getItem();
            if (!stack.isEmpty() && stack.is(origin.getItem()) && stack.getCount() < origin.getMaxStackSize() - origin.getCount()) {
                InvHelper.click(originSlot, 0, ContainerInput.QUICK_MOVE);
                return;
            }
        }

        // Swap into a free hotbar slot
        for (int i = 54; i <= 62; i++) {
            ItemStack stack = container.getSlot(i).getItem();
            if (stack.isEmpty() || isEject(stack)) {
                InvHelper.click(originSlot, i - 54, ContainerInput.SWAP);
                return;
            }
        }

        // Move into a free main inventory slot
        for (int i = 27; i <= 53; i++) {
            ItemStack stack = container.getSlot(i).getItem();
            if (stack.isEmpty() || isEject(stack)) {
                InvHelper.click(originSlot, 0, ContainerInput.PICKUP);
                InvHelper.click(i, 0, ContainerInput.PICKUP);
                if (!container.getCarried().isEmpty()) {
                    InvHelper.click(originSlot, 0, ContainerInput.PICKUP);
                }
                return;
            }
        }

        zipInventory();
    }

    /** Inventory index of the first eject item, or -1. */
    public static int getEjectSlot() {
        return InvHelper.find(InventoryHandler::isEject);
    }
}
