package com.jawsh.highwaytools.trombone;

import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import com.jawsh.highwaytools.modules.HighwayTools;
import com.jawsh.highwaytools.trombone.task.BlockTask;
import com.jawsh.highwaytools.trombone.task.TaskManager;
import com.jawsh.highwaytools.trombone.util.Coords;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import net.minecraft.core.BlockPos;

/**
 * All Baritone access lives in the nested {@link Impl} class so the addon still loads without Baritone installed.
 */
public class BaritoneBridge {
    public static boolean available() {
        return BaritoneUtils.IS_AVAILABLE;
    }

    public static void setup() {
        if (available()) Impl.setup();
    }

    public static void reset() {
        if (available()) Impl.reset();
    }

    public static void registerProcess() {
        if (available()) Impl.register();
    }

    private static class Impl {
        private static boolean settingAllowPlace = false;
        private static boolean settingAllowBreak = false;
        private static boolean settingRenderGoal = false;
        private static boolean settingAllowInventory = false;
        private static boolean settingAllowSprint = true;
        private static final TromboneProcess PROCESS = new TromboneProcess();

        static void setup() {
            Settings s = BaritoneAPI.getSettings();
            settingAllowPlace = s.allowPlace.value;
            settingAllowBreak = s.allowBreak.value;
            settingRenderGoal = s.renderGoal.value;
            settingAllowInventory = s.allowInventory.value;
            settingAllowSprint = s.allowSprint.value;
            s.allowPlace.value = false;
            s.allowBreak.value = false;
            s.renderGoal.value = HighwayTools.INSTANCE.goalRender.get();
            s.allowInventory.value = false;
            s.allowSprint.value = !HighwayTools.INSTANCE.noSprint.get();
        }

        static void reset() {
            Settings s = BaritoneAPI.getSettings();
            s.allowPlace.value = settingAllowPlace;
            s.allowBreak.value = settingAllowBreak;
            s.renderGoal.value = settingRenderGoal;
            s.allowInventory.value = settingAllowInventory;
            s.allowSprint.value = settingAllowSprint;
        }

        static void register() {
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingControlManager().registerProcess(PROCESS);
        }
    }

    private static class TromboneProcess implements IBaritoneProcess {
        @Override
        public boolean isActive() {
            HighwayTools m = HighwayTools.INSTANCE;
            return m != null && m.isActive() && Trombone.active;
        }

        @Override
        public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
            BlockPos goal = Pathfinder.goal;
            if (goal != null) {
                return new PathingCommand(new GoalNear(goal, 0), PathingCommandType.SET_GOAL_AND_PATH);
            }
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        @Override
        public boolean isTemporary() {
            return true;
        }

        @Override
        public void onLostControl() {
        }

        @Override
        public double priority() {
            return 2.0;
        }

        @Override
        public String displayName0() {
            String processName;
            if (!HighwayTools.INSTANCE.anonymizeStats.get()) {
                BlockTask task = TaskManager.lastTask;
                BlockPos goal = Pathfinder.goal;
                if (task != null) processName = task.toString();
                else if (goal != null) processName = Coords.asString(goal);
                else processName = "Thinking";
            } else {
                processName = "Running";
            }
            return "Trombone: " + processName;
        }
    }
}
