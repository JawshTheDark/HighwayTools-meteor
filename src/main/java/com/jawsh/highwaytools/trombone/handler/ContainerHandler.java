package com.jawsh.highwaytools.trombone.handler;

import com.jawsh.highwaytools.modules.HighwayTools;
import com.jawsh.highwaytools.trombone.IO;
import com.jawsh.highwaytools.trombone.Pathfinder;
import com.jawsh.highwaytools.trombone.blueprint.BlueprintGenerator;
import com.jawsh.highwaytools.trombone.task.BlockTask;
import com.jawsh.highwaytools.trombone.task.TaskState;
import com.jawsh.highwaytools.trombone.util.InvHelper;
import com.jawsh.highwaytools.trombone.util.TickTimer;
import com.jawsh.highwaytools.trombone.util.WorldUtils;
import meteordevelopment.meteorclient.utils.misc.Names;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class ContainerHandler {
    public static BlockTask containerTask = new BlockTask(BlockPos.ZERO, TaskState.DONE, Blocks.AIR);
    public static final TickTimer shulkerOpenTimer = new TickTimer();
    public static int grindCycles = 0;

    private static HighwayTools m() {
        return HighwayTools.INSTANCE;
    }

    public static void handleRestock(Item item) {
        HighwayTools m = m();
        if (m.preferEnderChests.get() && Block.byItem(item) == Blocks.OBSIDIAN) {
            handleEnderChest(item);
            return;
        }

        // Case 1: item is in a shulker in the inventory
        int slot = getShulkerWith(item);
        if (slot != -1) {
            BlockPos pos = getRemotePos();
            if (pos != null) {
                containerTask = new BlockTask(pos, TaskState.PLACE, Block.byItem(InvHelper.get(slot).getItem()), item);
            } else {
                IO.disableError("Can't find possible container position (Case: 1)");
            }
        } else {
            handleEnderChest(item);
        }
    }

    private static void handleEnderChest(Item item) {
        HighwayTools m = m();
        if (m.grindObsidian.get() && Block.byItem(item) == Blocks.OBSIDIAN) {
            // Case 2: desired item is Obsidian and grinding E-Chests is allowed
            int enderChests = InvHelper.countBlock(Blocks.ENDER_CHEST);

            if (enderChests <= m.saveEnder.get()) {
                handleRestock(Blocks.ENDER_CHEST.asItem());
                return;
            }

            if (grindCycles > 0) {
                BlockPos pos = getRemotePos();
                if (pos != null) {
                    containerTask = new BlockTask(pos, TaskState.PLACE, Blocks.ENDER_CHEST, Items.OBSIDIAN);
                    containerTask.destroy = true;
                    if (grindCycles > 1) containerTask.collect = false;
                    containerTask.collectItem = Items.OBSIDIAN;
                    grindCycles--;
                } else {
                    IO.disableError("Can't find possible container position (Case: 3)");
                }
            } else {
                int freeSlots = 0;
                for (int i = 0; i < InvHelper.SIZE; i++) {
                    ItemStack stack = InvHelper.get(i);
                    if (stack.isEmpty() || m.ejectItems.get().contains(stack.getItem())) freeSlots++;
                }

                int cycles = (freeSlots - 1 - m.keepFreeSlots.get()) * 8;

                if (cycles > 0) {
                    grindCycles = cycles;
                } else {
                    InventoryHandler.zipInventory();
                }
            }
        } else {
            // Case 3: last hope is the ender chest
            if (!m.searchEChest.get()) {
                IO.disableError(insufficientMaterial(item) + "\nTo provide sufficient material, grant access to your ender chest. Activate in settings: §7Storage Management > Search Ender Chest");
                return;
            }

            dispatchEnderChest(item);
        }
    }

    private static void dispatchEnderChest(Item item) {
        HighwayTools m = m();
        if (InvHelper.countBlock(Blocks.ENDER_CHEST) > m.saveEnder.get()) {
            BlockPos pos = getRemotePos();
            if (pos != null) {
                containerTask = new BlockTask(pos, TaskState.PLACE, Blocks.ENDER_CHEST, item);
                containerTask.collectItem = Items.OBSIDIAN;
            } else {
                IO.disableError("Can't find possible container position (Case: 4)");
            }
        } else {
            int slot = getShulkerWith(Blocks.ENDER_CHEST.asItem());
            if (slot != -1) {
                BlockPos pos = getRemotePos();
                if (pos != null) {
                    containerTask = new BlockTask(pos, TaskState.PLACE, Block.byItem(InvHelper.get(slot).getItem()), Blocks.ENDER_CHEST.asItem());
                } else {
                    IO.disableError("Can't find possible container position (Case: 5)");
                }
            } else {
                IO.disableError("No " + Names.get(Blocks.ENDER_CHEST) + " was found in inventory.");
            }
        }
    }

    private static BlockPos getRemotePos() {
        HighwayTools m = m();
        BlockPos current = Pathfinder.currentBlockPos;
        Vec3 origin = WorldUtils.center(current.above());
        double minDistance = m.minDistance.get();

        return WorldUtils.getBlockPosInSphere(origin, m.maxReach.get()).stream()
            .filter(pos -> !BlueprintGenerator.isInsideBlueprintBuild(pos)
                && !pos.equals(current)
                && WorldUtils.isPlaceable(pos)
                && !WorldUtils.isReplaceable(pos.below())
                && WorldUtils.isAir(pos.above())
                && WorldUtils.getVisibleSides(pos.below()).contains(Direction.UP)
                && mc.player.position().distanceTo(WorldUtils.center(pos)) > minDistance
                && pos.getY() >= current.getY())
            .min(Comparator.<BlockPos>comparingInt(pos -> -secureScore(pos))
                .thenComparingInt(pos -> (int) Math.ceil(pos.distToCenterSqr(origin.x, origin.y, origin.z)))
                .thenComparingInt(pos -> Math.abs(pos.getY() - current.getY())))
            .orElse(null);
    }

    private static int secureScore(BlockPos pos) {
        int safe = 0;
        if (!WorldUtils.isReplaceable(pos.below().north())) safe++;
        if (!WorldUtils.isReplaceable(pos.below().east())) safe++;
        if (!WorldUtils.isReplaceable(pos.below().south())) safe++;
        if (!WorldUtils.isReplaceable(pos.below().west())) safe++;
        return safe;
    }

    /** Inventory index of the shulker box containing the fewest stacks of the item, or -1. */
    public static int getShulkerWith(Item item) {
        int best = -1;
        int bestCount = Integer.MAX_VALUE;
        for (int i = 0; i < InvHelper.SIZE; i++) {
            ItemStack stack = InvHelper.get(i);
            if (!InvHelper.isShulker(stack)) continue;
            int count = getShulkerData(stack, item);
            if (count > 0 && count < bestCount) {
                bestCount = count;
                best = i;
            }
        }
        return best;
    }

    /** Container slot id of the shulker box containing the fewest stacks of the item, or -1. */
    public static int getShulkerWithInContainer(AbstractContainerMenu container, int from, int to, Item item) {
        int best = -1;
        int bestCount = Integer.MAX_VALUE;
        for (int i = from; i <= to; i++) {
            ItemStack stack = container.getSlot(i).getItem();
            if (!InvHelper.isShulker(stack)) continue;
            int count = getShulkerData(stack, item);
            if (count > 0 && count < bestCount) {
                bestCount = count;
                best = i;
            }
        }
        return best;
    }

    /** Number of stacks of the given item inside the shulker box item. */
    public static int getShulkerData(ItemStack stack, Item item) {
        if (!InvHelper.isShulker(stack)) return 0;
        int count = 0;
        for (ItemStack content : InvHelper.getShulkerContents(stack)) {
            if (content != null && !content.isEmpty() && content.is(item)) count++;
        }
        return count;
    }

    public static BlockPos getCollectingPosition() {
        double range = 8.0;
        Item collectItem = containerTask.collectItem;

        List<ItemEntity> items = mc.level.getEntitiesOfClass(ItemEntity.class, mc.player.getBoundingBox().inflate(range),
            e -> !e.getItem().isEmpty() && e.getItem().is(collectItem));

        ItemEntity nearest = items.stream().min(Comparator.comparingDouble(e -> mc.player.distanceTo(e))).orElse(null);
        if (nearest == null) return null;

        Vec3 itemVec = nearest.position();
        return WorldUtils.getBlockPosInSphere(itemVec, range).stream()
            .filter(pos -> WorldUtils.isAir(pos.above())
                && WorldUtils.isAir(pos)
                && !WorldUtils.isPlaceable(pos.below()))
            .min(Comparator.<BlockPos>comparingDouble(pos -> pos.distToCenterSqr(itemVec.x, itemVec.y, itemVec.z))
                .thenComparingInt(BlockPos::getY))
            .orElse(null);
    }

    private static String insufficientMaterial(Item item) {
        HighwayTools m = m();
        int itemCount = InvHelper.countItem(item);
        StringBuilder message = new StringBuilder();
        if (m.saveMaterial.get() > 0 && item == m.material.get().asItem()) {
            message.append(insufficientMaterialPrint(itemCount, m.saveMaterial.get(), Names.get(m.material.get())));
        }
        if (m.saveEnder.get() > 0 && Block.byItem(item) == Blocks.ENDER_CHEST) {
            message.append(insufficientMaterialPrint(itemCount, m.saveEnder.get(), Names.get(Blocks.ENDER_CHEST)));
        }
        if (m.saveTools.get() > 0 && item == m.tool.get()) {
            message.append(insufficientMaterialPrint(itemCount, m.saveTools.get(), Names.get(m.tool.get()) + "(s)"));
        }
        if (m.saveFood.get() > 0 && item == m.food.get()) {
            message.append(insufficientMaterialPrint(itemCount, m.saveFood.get(), Names.get(m.food.get()) + "(s)"));
        }
        return message + "\nTo continue anyways, set setting in §7Storage Management > Save <Material>§r to zero.";
    }

    private static String insufficientMaterialPrint(int itemCount, int settingCount, String name) {
        return "For safety purposes you need §b" + (settingCount - itemCount + 1) + "§c more " + name + " in your inventory (" + itemCount + "/" + (settingCount + 1) + ").";
    }
}
