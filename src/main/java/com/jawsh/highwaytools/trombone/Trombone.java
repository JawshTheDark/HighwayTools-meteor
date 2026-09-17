package com.jawsh.highwaytools.trombone;

import com.jawsh.highwaytools.modules.HighwayTools;
import com.jawsh.highwaytools.trombone.handler.InventoryHandler;
import com.jawsh.highwaytools.trombone.task.TaskManager;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class Trombone {
    public static volatile boolean active = false;

    public enum Structure {
        HIGHWAY, FLAT, TUNNEL
    }

    public static HighwayTools module() {
        return HighwayTools.INSTANCE;
    }

    public static void onEnable() {
        TaskManager.clearTasks();
        Pathfinder.setupPathing();
        BaritoneBridge.setup();
        if (module().info.get()) IO.printEnable();
    }

    public static void onDisable() {
        BaritoneBridge.reset();
        IO.printDisable();
        Pathfinder.clearProcess();
        TaskManager.clearTasks();
        Statistics.updateTotalDistance();
        InventoryHandler.lastHitVec = null;
    }

    public static void tick() {
        if (module().noSprint.get()) {
            mc.options.keySprint.setDown(false);
            if (mc.player.isSprinting()) mc.player.setSprinting(false);
        }

        TaskManager.populateTasks();
        Statistics.updateStats();

        if (IO.pauseCheck()) return;

        Pathfinder.updateProcess();
        TaskManager.runTasks();
        InventoryHandler.updateRotation();
    }
}
