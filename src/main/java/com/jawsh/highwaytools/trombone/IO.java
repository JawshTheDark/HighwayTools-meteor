package com.jawsh.highwaytools.trombone;

import com.jawsh.highwaytools.modules.HighwayTools;
import com.jawsh.highwaytools.trombone.util.WorldUtils;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.AutoLog;
import meteordevelopment.meteorclient.systems.modules.movement.Velocity;
import meteordevelopment.meteorclient.systems.modules.player.AntiAFK;
import meteordevelopment.meteorclient.systems.modules.player.AntiHunger;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import meteordevelopment.meteorclient.utils.misc.Names;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.world.TickRate;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class IO {
    public enum DisableMode {
        NONE, ANTI_AFK, LOGOUT
    }

    public enum DebugLevel {
        OFF, IMPORTANT, VERBOSE
    }

    private static HighwayTools m() {
        return HighwayTools.INSTANCE;
    }

    /* Chat helpers that bypass String.format so '%' in messages is safe */
    public static void msg(String message) {
        m().info(Component.literal(message));
    }

    public static void warn(String message) {
        ChatUtils.sendMsg(m().title, Component.literal("§e" + message));
    }

    public static void raw(String message) {
        ChatUtils.sendMsg(Component.literal(message));
    }

    public static volatile String pauseReason = null;

    /** Why the bot is currently paused, or null when it may run. */
    public static String findPauseReason() {
        HighwayTools m = m();
        if (!Pathfinder.rubberbandTimer.tick(m.rubberbandTimeout.get(), false)) return "Rubberband timeout";
        if (mc.player.getInventory().isEmpty()) return "Empty inventory";
        if (isLagging()) return "Server lag";
        if (isEating()) return "Eating";
        int food = mc.player.getFoodData().getFoodLevel();
        if (food < m.minHunger.get()) return "Hungry (" + food + " < " + m.minHunger.get() + ")";
        if (isInQueue()) return "In queue";
        if (!mc.player.isAlive()) return "Dead";
        if (!mc.player.onGround()) return "Not on ground";
        return null;
    }

    public static boolean pauseCheck() {
        String reason = findPauseReason();
        if (!java.util.Objects.equals(reason, pauseReason)) {
            pauseReason = reason;
            if (m().debugLevel.get() != DebugLevel.OFF) {
                msg(reason != null ? "Paused: §7" + reason : "Resumed.");
            }
        }
        return reason != null;
    }

    private static boolean isLagging() {
        double threshold = m().lagPause.get();
        return threshold > 0 && TickRate.INSTANCE.getTimeSinceLastTick() > threshold;
    }

    private static boolean isEating() {
        AutoEat autoEat = Modules.get().get(AutoEat.class);
        return (autoEat != null && autoEat.eating) || PlayerUtils.shouldPause(false, true, true);
    }

    private static boolean isInQueue() {
        String brand = mc.getConnection() != null ? mc.getConnection().serverBrand() : null;
        return mc.level.getDifficulty() == Difficulty.PEACEFUL
            && mc.level.dimension() == Level.END
            && brand != null && brand.contains("2b2t");
    }

    public static void printEnable() {
        HighwayTools m = m();
        raw("    §9> §7Direction: §a" + Pathfinder.startingDirection.displayName + " / " + Pathfinder.startingDirection.displayNameXY + "§r");

        if (!m.anonymizeStats.get()) {
            if (Pathfinder.startingDirection.isDiagonal()) {
                raw(String.format("    §9> §7Axis offset: §a%,d %,d§r", Pathfinder.startingBlockPos.getX(), Pathfinder.startingBlockPos.getZ()));

                if (Math.abs(Pathfinder.startingBlockPos.getX()) != Math.abs(Pathfinder.startingBlockPos.getZ())) {
                    raw("    §c[!] You may have an offset to diagonal highway position!");
                }
            } else {
                if (Pathfinder.startingDirection == com.jawsh.highwaytools.trombone.util.Direction8.NORTH
                    || Pathfinder.startingDirection == com.jawsh.highwaytools.trombone.util.Direction8.SOUTH) {
                    raw(String.format("    §9> §7Axis offset: §a%,d§r", Pathfinder.startingBlockPos.getX()));
                } else {
                    raw(String.format("    §9> §7Axis offset: §a%,d§r", Pathfinder.startingBlockPos.getZ()));
                }
            }
        }

        if (!m.disableWarnings.get()) {
            if (Pathfinder.startingBlockPos.getY() != 120 && m.mode.get() != Trombone.Structure.TUNNEL) {
                raw("    §c[!] Check altitude and make sure to build at Y: 120 for the correct height");
            }

            if (Modules.get().isActive(AntiHunger.class)) {
                raw("    §c[!] AntiHunger does slow down block interactions.");
            }

            if (!Modules.get().isActive(AutoEat.class)) {
                raw("    §c[!] You should activate AutoEat to not die on starvation.");
            }

            if (!Modules.get().isActive(AutoLog.class)) {
                raw("    §c[!] You should activate AutoLog to prevent most deaths when afk.");
            }

            if (m.multiBuilding.get() && !Modules.get().isActive(Velocity.class)) {
                raw("    §c[!] Make sure to enable Velocity to not get pushed from your mates.");
            }

            if (m.material.get() == m.fillerMat.get()) {
                raw("    §c[!] Make sure to use §aTunnel Mode§c instead of having same material for both main and filler!");
            }

            if (m.mode.get() == Trombone.Structure.HIGHWAY && m.height.get() < 3) {
                raw("    §c[!] You may increase the height to at least 3");
            }

            if (isInQueue()) {
                raw("    §c[!] You should not activate the bot in queue! Bot will move to 0 0.");
            }

            if (!BaritoneBridge.available()) {
                raw("    §c[!] Baritone was not found. Falling back to simple straight-line walking.");
            }
        }
    }

    public static void printDisable() {
        if (m().info.get()) {
            raw(String.format("    §9> §7Placed blocks: §a%,d§r", Statistics.totalBlocksPlaced));
            raw(String.format("    §9> §7Destroyed blocks: §a%,d§r", Statistics.totalBlocksBroken));
            raw(String.format("    §9> §7Distance: §a%,d§r", (int) WorldUtils.distanceTo(Pathfinder.startingBlockPos, Pathfinder.currentBlockPos)));
        }
    }

    public static void printSettings() {
        HighwayTools m = m();
        StringBuilder sb = new StringBuilder();
        sb.append(m.title).append(" Settings")
            .append("\n §9> §rMain material: §7").append(Names.get(m.material.get()))
            .append("\n §9> §rFiller material: §7").append(Names.get(m.fillerMat.get()))
            .append("\n §9> §rFood: §7").append(Names.get(m.food.get()))
            .append("\n §9> §rTool: §7").append(Names.get(m.tool.get()))
            .append("\n §9> §rIgnored Blocks:");

        for (Block block : m.ignoreBlocks.get()) {
            sb.append("\n     §9> §7").append(BuiltInRegistries.BLOCK.getKey(block));
        }

        ChatUtils.sendMsg(Component.literal(sb.toString()));
    }

    public static void disableError(String error) {
        HighwayTools m = m();
        ChatUtils.sendMsg(0, m.title, ChatFormatting.RED, Component.literal("§c[!] " + error));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f));
        if (m.isActive()) m.toggle();

        switch (m.disableMode.get()) {
            case ANTI_AFK -> {
                warn("§c[!] §bGoing into AFK mode.");
                AntiAFK antiAFK = Modules.get().get(AntiAFK.class);
                if (antiAFK != null && !antiAFK.isActive()) antiAFK.toggle();
            }
            case LOGOUT -> {
                warn("§c[!] §4CAUTION: Logging off in 6 seconds!");
                Scheduler.scheduleMillis(6000L, () -> {
                    if (m.disableMode.get() == DisableMode.LOGOUT && !m.isActive()) {
                        if (mc.player == null || mc.getConnection() == null) return;
                        if (m.usingProxy.get()) {
                            ChatUtils.sendPlayerMsg(m.proxyCommand.get());
                        } else {
                            mc.getConnection().getConnection().disconnect(Component.literal("Done building highways."));
                        }
                    } else {
                        msg("§aLogout canceled.");
                    }
                });
            }
            case NONE -> {
                // Nothing
            }
        }
    }
}
