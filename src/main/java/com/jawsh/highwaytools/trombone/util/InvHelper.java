package com.jawsh.highwaytools.trombone.util;

import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.function.Predicate;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Player inventory helpers. Indices are inventory indices: 0-8 hotbar, 9-35 main inventory.
 */
public class InvHelper {
    public static final int SIZE = 36;

    public static ItemStack get(int index) {
        return mc.player.getInventory().getItem(index);
    }

    public static int countItem(Predicate<ItemStack> predicate) {
        int count = 0;
        for (int i = 0; i < SIZE; i++) {
            ItemStack stack = get(i);
            if (!stack.isEmpty() && predicate.test(stack)) count += stack.getCount();
        }
        return count;
    }

    public static int countItem(Item item) {
        return countItem(stack -> stack.is(item));
    }

    public static int countBlock(Block block) {
        return countItem(block.asItem());
    }

    /** Number of stacks (not items) matching the predicate. */
    public static int countStacks(Predicate<ItemStack> predicate) {
        int count = 0;
        for (int i = 0; i < SIZE; i++) {
            ItemStack stack = get(i);
            if (!stack.isEmpty() && predicate.test(stack)) count++;
        }
        return count;
    }

    /** First index matching the predicate, hotbar first. Returns -1 if none. */
    public static int find(Predicate<ItemStack> predicate) {
        for (int i = 0; i < SIZE; i++) {
            ItemStack stack = get(i);
            if (!stack.isEmpty() && predicate.test(stack)) return i;
        }
        return -1;
    }

    public static int findItem(Item item) {
        return find(stack -> stack.is(item));
    }

    public static int firstEmpty() {
        for (int i = 0; i < SIZE; i++) {
            if (get(i).isEmpty()) return i;
        }
        return -1;
    }

    public static int firstEmptyHotbar() {
        for (int i = 0; i < 9; i++) {
            if (get(i).isEmpty()) return i;
        }
        return -1;
    }

    public static boolean isFull() {
        return firstEmpty() == -1;
    }

    /** Food check that also honours per-stack components (custom server consumables, renamed items). */
    public static boolean isFood(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return Utils.isFood(stack)
            || stack.has(DataComponents.FOOD)
            || stack.has(DataComponents.CONSUMABLE);
    }

    public static boolean isShulker(ItemStack stack) {
        return !stack.isEmpty() && Utils.isShulker(stack.getItem());
    }

    public static ItemStack[] getShulkerContents(ItemStack stack) {
        ItemStack[] items = new ItemStack[27];
        for (int i = 0; i < items.length; i++) items[i] = ItemStack.EMPTY;
        Utils.getItemsInContainerItem(stack, items);
        return items;
    }

    public static void swapToHotbar(int hotbarIndex) {
        InvUtils.swap(hotbarIndex, false);
    }

    /** Raw container click on the currently open menu (player inventory when no screen is open). */
    public static void click(int slotId, int button, ContainerInput type) {
        AbstractContainerMenu menu = mc.player.containerMenu;
        mc.gameMode.handleContainerInput(menu.containerId, slotId, button, type, mc.player);
    }

    public static int indexToId(int index) {
        return SlotUtils.indexToId(index);
    }

    /** Swaps an inventory slot with a hotbar slot using a single SWAP click (player inventory menu). */
    public static void moveToHotbar(int fromIndex, int hotbarIndex) {
        click(indexToId(fromIndex), hotbarIndex, ContainerInput.SWAP);
    }

    /** Makes sure the given block is in the main hand. Returns false if the block is not in the inventory. */
    public static boolean swapToBlockOrMove(Block block) {
        Item item = block.asItem();
        int index = findItem(item);
        if (index == -1) return false;

        if (index < 9) {
            swapToHotbar(index);
        } else {
            int hotbar = firstEmptyHotbar();
            if (hotbar == -1) hotbar = 0;
            moveToHotbar(index, hotbar);
            swapToHotbar(hotbar);
        }
        return true;
    }

    /** Makes sure the given inventory index is selected in the hotbar, moving it if necessary. */
    public static void swapOrMoveToHand(int index) {
        if (index < 9) {
            swapToHotbar(index);
        } else {
            int hotbar = firstEmptyHotbar();
            if (hotbar == -1) hotbar = 0;
            moveToHotbar(index, hotbar);
            swapToHotbar(hotbar);
        }
    }

    public static void dropSlot(int index) {
        InvUtils.drop().slot(index);
    }
}
