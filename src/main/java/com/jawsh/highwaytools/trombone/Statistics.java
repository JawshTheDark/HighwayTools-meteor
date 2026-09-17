package com.jawsh.highwaytools.trombone;

import com.jawsh.highwaytools.hud.HighwayToolsHud;
import com.jawsh.highwaytools.modules.HighwayTools;
import com.jawsh.highwaytools.trombone.handler.ContainerHandler;
import com.jawsh.highwaytools.trombone.handler.InventoryHandler;
import com.jawsh.highwaytools.trombone.interaction.Place;
import com.jawsh.highwaytools.trombone.task.BlockTask;
import com.jawsh.highwaytools.trombone.task.TaskManager;
import com.jawsh.highwaytools.trombone.task.TaskState;
import com.jawsh.highwaytools.trombone.util.Coords;
import com.jawsh.highwaytools.trombone.util.InvHelper;
import com.jawsh.highwaytools.trombone.util.WorldUtils;
import meteordevelopment.meteorclient.utils.misc.Names;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class Statistics {
    /** One HUD line. {@code value} may be null for headers / single text lines. */
    public record Line(String label, String value, double scale) {
        public static Line header(String label) {
            return new Line(label, null, 1.0);
        }

        public static Line of(String label, String value) {
            return new Line("    " + label, value, 1.0);
        }

        public static Line small(String label) {
            return new Line(label, null, 0.6);
        }
    }

    public static final ConcurrentLinkedDeque<Long> simpleMovingAveragePlaces = new ConcurrentLinkedDeque<>();
    public static final ConcurrentLinkedDeque<Long> simpleMovingAverageBreaks = new ConcurrentLinkedDeque<>();
    public static final ConcurrentLinkedDeque<Long> simpleMovingAverageDistance = new ConcurrentLinkedDeque<>();
    public static int totalBlocksPlaced = 0;
    public static int totalBlocksBroken = 0;
    private static double totalDistance = 0.0;
    private static long runtimeMilliSeconds = 0;
    private static int prevFood = 0;
    private static int foodLoss = 1;
    private static int materialLeft = 0;
    private static int fillerMatLeft = 0;
    public static volatile int durabilityUsages = 0;

    private static HighwayTools m() {
        return HighwayTools.INSTANCE;
    }

    private static int smaRange() {
        HighwayToolsHud hud = HighwayToolsHud.INSTANCE;
        return hud != null ? hud.simpleMovingAverageRange.get() : 60;
    }

    public static void updateStats() {
        updateFood();

        /* Update the minecraft statistics every 15 seconds */
        if (runtimeMilliSeconds % 15000 == 0) {
            mc.getConnection().send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.REQUEST_STATS));
        }
        runtimeMilliSeconds += 50;

        updateDeques();
    }

    private static final com.jawsh.highwaytools.trombone.util.TickTimer hungerWarnTimer = new com.jawsh.highwaytools.trombone.util.TickTimer();

    private static void updateFood() {
        int currentFood = mc.player.getFoodData().getFoodLevel();
        if (currentFood < m().minHunger.get()) {
            if (InvHelper.countItem(InvHelper::isFood) == 0) {
                IO.disableError("Out of food: hunger is " + currentFood + " and there is no food in the inventory.");
            } else if (!meteordevelopment.meteorclient.systems.modules.Modules.get().isActive(meteordevelopment.meteorclient.systems.modules.player.AutoEat.class) && hungerWarnTimer.tick(200)) {
                IO.warn("Hunger is " + currentFood + ", pausing. Enable AutoEat (or eat) so the bot can continue.");
            }
        }
        if (currentFood != prevFood) {
            if (currentFood < prevFood) foodLoss++;
            prevFood = currentFood;
        }
    }

    public static void updateTotalDistance() {
        totalDistance += WorldUtils.distanceTo(Pathfinder.startingBlockPos, Pathfinder.currentBlockPos);
    }

    private static void updateDeques() {
        long removeTime = System.currentTimeMillis() - smaRange() * 1000L;

        updateDeque(simpleMovingAveragePlaces, removeTime);
        updateDeque(simpleMovingAverageBreaks, removeTime);
        updateDeque(simpleMovingAverageDistance, removeTime);

        updateDeque(InventoryHandler.packetLimiter, System.currentTimeMillis() - 1000L);
    }

    private static void updateDeque(ConcurrentLinkedDeque<Long> deque, long removeTime) {
        while (!deque.isEmpty() && deque.peekFirst() < removeTime) {
            deque.pollFirst();
        }
    }

    public static void gatherStatistics(List<Line> out, HighwayToolsHud hud) {
        double runtimeSec = (runtimeMilliSeconds / 1000.0) + 0.0001;
        double distanceDone = (int) WorldUtils.distanceTo(Pathfinder.startingBlockPos, Pathfinder.currentBlockPos) + totalDistance;

        if (hud.showSession.get()) gatherSession(out, runtimeSec);
        if (hud.showLifeTime.get()) gatherLifeTime(out);
        if (hud.showPerformance.get()) gatherPerformance(out, runtimeSec, distanceDone);
        if (hud.showEnvironment.get()) gatherEnvironment(out);
        if (hud.showTask.get()) gatherTask(out);
        if (hud.showEstimations.get()) gatherEstimations(out, runtimeSec, distanceDone);
        if (hud.showQueue.get()) gatherQueue(out);

        out.add(Line.small("by Constructor#9948/Avanatiker"));
    }

    private static String formatTime(double seconds) {
        String s = String.format("%02d", (int) (seconds % 60.0));
        String min = String.format("%02d", (int) ((seconds % 3600.0) / 60.0));
        String h = String.format("%02d", (int) (seconds / 3600.0));
        return h + ":" + min + ":" + s;
    }

    private static void gatherSession(List<Line> out, double runtimeSec) {
        HighwayTools m = m();
        out.add(Line.header("Session"));
        out.add(Line.of("Runtime:", formatTime(runtimeSec)));
        out.add(Line.of("Direction:", Pathfinder.startingDirection.displayName + " / " + Pathfinder.startingDirection.displayNameXY));
        if (!m.anonymizeStats.get()) out.add(Line.of("Start:", "(" + Coords.asString(Pathfinder.startingBlockPos) + ")"));
        out.add(Line.of("Placed / destroyed:", String.format("%,d / %,d", totalBlocksPlaced, totalBlocksBroken)));
    }

    private static void gatherLifeTime(List<Line> out) {
        HighwayTools m = m();
        Block material = m.material.get();
        Item tool = m.tool.get();

        int matPlaced = mc.player.getStats().getValue(Stats.ITEM_USED, material.asItem());
        int matMined = mc.player.getStats().getValue(Stats.BLOCK_MINED, material);
        int enderMined = mc.player.getStats().getValue(Stats.BLOCK_MINED, Blocks.ENDER_CHEST);
        int netherrackMined = mc.player.getStats().getValue(Stats.BLOCK_MINED, Blocks.NETHERRACK);
        int pickaxeBroken = mc.player.getStats().getValue(Stats.ITEM_BROKEN, tool);

        if (matPlaced + matMined + enderMined + netherrackMined + pickaxeBroken > 0) {
            out.add(Line.header("Lifetime"));
        }

        if (m.mode.get() == Trombone.Structure.HIGHWAY || m.mode.get() == Trombone.Structure.FLAT) {
            if (matPlaced > 0) out.add(Line.of(Names.get(material) + " placed:", String.format("%,d", matPlaced)));
            if (matMined > 0) out.add(Line.of(Names.get(material) + " mined:", String.format("%,d", matMined)));
            if (enderMined > 0) out.add(Line.of(Names.get(Blocks.ENDER_CHEST) + " mined:", String.format("%,d", enderMined)));
        }

        if (netherrackMined > 0) out.add(Line.of(Names.get(Blocks.NETHERRACK) + " mined:", String.format("%,d", netherrackMined)));
        if (pickaxeBroken > 0) out.add(Line.of(Names.get(tool) + " broken:", String.format("%,d", pickaxeBroken)));
    }

    private static void gatherPerformance(List<Line> out, double runtimeSec, double distanceDone) {
        double range = smaRange();
        out.add(Line.header("Performance"));
        out.add(Line.of("Placements / s:", String.format("%.2f SMA(%.2f)", totalBlocksPlaced / runtimeSec, simpleMovingAveragePlaces.size() / range)));
        out.add(Line.of("Breaks / s:", String.format("%.2f SMA(%.2f)", totalBlocksBroken / runtimeSec, simpleMovingAverageBreaks.size() / range)));
        out.add(Line.of("Distance km / h:", String.format("%.2f SMA(%.2f)", (distanceDone / runtimeSec * 60.0 * 60.0) / 1000.0, (simpleMovingAverageDistance.size() / range * 60.0 * 60.0) / 1000.0)));
        out.add(Line.of("Food level loss / h:", String.format("%.2f", totalBlocksBroken / (double) foodLoss)));
        out.add(Line.of("Pickaxes / h:", String.format("%.2f", (durabilityUsages / runtimeSec) * 60.0 * 60.0 / 1561.0)));
        out.add(Line.of("Mining packets / s:", String.valueOf(InventoryHandler.packetLimiter.size())));
    }

    private static void gatherEnvironment(List<Line> out) {
        HighwayTools m = m();
        out.add(Line.header("Environment"));
        out.add(Line.of("Materials:", "Main(" + Names.get(m.material.get()) + ") Filler(" + Names.get(m.fillerMat.get()) + ")"));
        out.add(Line.of("Dimensions:", "Width(" + m.width.get() + ") Height(" + m.height.get() + ")"));
        if (m.dynamicDelay.get()) {
            out.add(Line.of("Delays:", "Place(" + (m.placeDelay.get() + Place.extraPlaceDelay) + ") Break(" + m.breakDelay.get() + ")"));
        } else {
            out.add(Line.of("Delays:", "Place(" + m.placeDelay.get() + ") Break(" + m.breakDelay.get() + ")"));
        }
        out.add(Line.of("Movement:", Pathfinder.moveState.name()));
        out.add(Line.of("Pathing:", BaritoneBridge.available() ? "Baritone" : "Fallback"));
    }

    private static void gatherTask(List<Line> out) {
        HighwayTools m = m();
        BlockTask containerTask = ContainerHandler.containerTask;
        BlockTask task = containerTask.taskState != TaskState.DONE
            ? containerTask
            : (TaskManager.sortedTasks.isEmpty() ? null : TaskManager.sortedTasks.getFirst());

        if (task == null) return;

        out.add(Line.header("Task"));
        out.add(Line.of("Status:", task.taskState.name()));
        out.add(Line.of("Target block:", Names.get(task.targetBlock)));
        if (task.item != Items.AIR) out.add(Line.of("Target item:", Names.get(task.item)));
        if (!m.anonymizeStats.get()) out.add(Line.of("Position:", "(" + Coords.asString(task.blockPos) + ")"));
        out.add(Line.of("Ticks stuck:", String.valueOf(task.stuckTicks)));
    }

    private static void gatherEstimations(List<Line> out, double runtimeSec, double distanceDone) {
        HighwayTools m = m();
        Block material = m.material.get();
        double distanceSoFar = (int) WorldUtils.distanceTo(Pathfinder.startingBlockPos, Pathfinder.currentBlockPos);

        switch (m.mode.get()) {
            case HIGHWAY, FLAT -> {
                materialLeft = InvHelper.countBlock(material);
                fillerMatLeft = InvHelper.countBlock(m.fillerMat.get());
                int indirectMaterialLeft = 8 * InvHelper.countBlock(Blocks.ENDER_CHEST);

                double pavingLeft = materialLeft / (Math.max(totalBlocksPlaced, 1) / Math.max(distanceDone, 1.0));
                double secLeft = Math.max(pavingLeft, 0.0) / (distanceSoFar / runtimeSec);
                if (Double.isNaN(secLeft) || Double.isInfinite(secLeft)) secLeft = 0;

                out.add(Line.header("Refill"));
                if (material == Blocks.OBSIDIAN) {
                    out.add(Line.of(Names.get(material) + ":", "Direct(" + materialLeft + ") Indirect(" + indirectMaterialLeft + ")"));
                } else {
                    out.add(Line.of(Names.get(material) + ":", String.valueOf(materialLeft)));
                }
                out.add(Line.of(Names.get(m.fillerMat.get()) + ":", String.valueOf(fillerMatLeft)));

                if (ContainerHandler.grindCycles > 0) {
                    out.add(Line.of("Ender Chest cycles left:", String.valueOf(ContainerHandler.grindCycles)));
                } else {
                    out.add(Line.of("Distance left:", String.valueOf((int) pavingLeft)));
                    if (!m.anonymizeStats.get()) {
                        out.add(Line.of("Destination:", "(" + Coords.asString(Pathfinder.startingDirection.offset(Pathfinder.currentBlockPos, (int) pavingLeft)) + ")"));
                    }
                    out.add(Line.of("ETA:", formatTime(secLeft)));
                }
            }
            case TUNNEL -> {
                int pickaxesLeft = InvHelper.countItem(stack -> stack.is(ItemTags.PICKAXES));
                double tunnelingLeft = (pickaxesLeft * 1561.0) / (Math.max(durabilityUsages, 1) / Math.max(distanceDone, 1.0));
                double secLeft = Math.max(tunnelingLeft, 0.0) / (distanceSoFar / runtimeSec);
                if (Double.isNaN(secLeft) || Double.isInfinite(secLeft)) secLeft = 0;

                out.add(Line.header("Destination:"));
                out.add(Line.of("Pickaxes:", String.valueOf(pickaxesLeft)));
                out.add(Line.of("Distance left:", String.valueOf((int) tunnelingLeft)));
                if (!m.anonymizeStats.get()) {
                    out.add(Line.of("Destination:", "(" + Coords.asString(Pathfinder.startingDirection.offset(Pathfinder.currentBlockPos, (int) tunnelingLeft)) + ")"));
                }
                out.add(Line.of("ETA:", formatTime(secLeft)));
            }
        }
    }

    private static void gatherQueue(List<Line> out) {
        BlockTask containerTask = ContainerHandler.containerTask;
        if (containerTask.taskState != TaskState.DONE) {
            out.add(Line.small("Container"));
            out.add(Line.small(containerTask.prettyPrint()));
        }

        if (!TaskManager.sortedTasks.isEmpty()) {
            out.add(Line.small("Pending"));
            addTaskComponentList(out, List.copyOf(TaskManager.sortedTasks));
        }
    }

    private static void addTaskComponentList(List<Line> out, Collection<BlockTask> tasks) {
        for (BlockTask task : tasks) {
            out.add(Line.small(task.prettyPrint()));
        }
    }

    public static void resetStats() {
        simpleMovingAveragePlaces.clear();
        simpleMovingAverageBreaks.clear();
        simpleMovingAverageDistance.clear();
        totalBlocksPlaced = 0;
        totalBlocksBroken = 0;
        totalDistance = 0.0;
        runtimeMilliSeconds = 0;
        prevFood = 0;
        foodLoss = 1;
        materialLeft = 0;
        fillerMatLeft = 0;
        durabilityUsages = 0;
    }
}
