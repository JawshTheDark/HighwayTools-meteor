package com.jawsh.highwaytools.trombone;

import com.jawsh.highwaytools.modules.HighwayTools;
import com.jawsh.highwaytools.trombone.handler.ContainerHandler;
import com.jawsh.highwaytools.trombone.task.BlockTask;
import com.jawsh.highwaytools.trombone.task.TaskManager;
import com.jawsh.highwaytools.trombone.task.TaskState;
import com.jawsh.highwaytools.trombone.util.Coords;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

public class Renderer {
    private static final Color WHITE_SIDE = new Color(255, 255, 255, 40);
    private static final Color WHITE_LINE = new Color(255, 255, 255, 200);

    private static HighwayTools m() {
        return HighwayTools.INSTANCE;
    }

    public static void renderWorld(Render3DEvent event) {
        HighwayTools m = m();
        if (!m.filled.get() && !m.outline.get()) return;

        long currentTime = System.currentTimeMillis();

        if (m.showCurrentPos.get()) {
            event.renderer.box(Pathfinder.currentBlockPos, WHITE_SIDE, WHITE_LINE, ShapeMode.Both, 0);
        }

        BlockTask containerTask = ContainerHandler.containerTask;
        if (containerTask.taskState != TaskState.DONE) {
            addToRenderer(event, containerTask, currentTime, false);
        }

        for (BlockTask task : TaskManager.tasks.values()) {
            if (task.targetBlock == Blocks.AIR && task.taskState == TaskState.DONE) continue;
            addToRenderer(event, task, currentTime, task.toRemove);
        }
    }

    private static void addToRenderer(Render3DEvent event, BlockTask blockTask, long currentTime, boolean reverse) {
        HighwayTools m = m();
        AABB box = blockTask.aabb;

        if (m.popUp.get()) {
            double speed = Math.max(1, m.popUpSpeed.get());
            double t = Math.min(currentTime - blockTask.timestamp, speed * Math.PI / 2) / speed;
            double flip = reverse ? Math.cos(t) : Math.sin(t);
            box = box.deflate(0.5 - flip * 0.5);
        }

        Color base = blockTask.taskState.color;
        Color side = new Color(base.r, base.g, base.b, m.filled.get() ? m.aFilled.get() : 0);
        Color line = new Color(base.r, base.g, base.b, m.outline.get() ? m.aOutline.get() : 0);

        ShapeMode mode;
        if (m.filled.get() && m.outline.get()) mode = ShapeMode.Both;
        else if (m.filled.get()) mode = ShapeMode.Sides;
        else mode = ShapeMode.Lines;

        event.renderer.box(box, side, line, mode, 0);
    }

    public static void renderOverlay(Render2DEvent event) {
        HighwayTools m = m();
        if (!m.showDebugRender.get()) return;

        BlockTask containerTask = ContainerHandler.containerTask;
        if (containerTask.taskState != TaskState.DONE) {
            updateOverlay(event, containerTask.blockPos, containerTask);
        }

        for (BlockTask task : TaskManager.tasks.values()) {
            if (task.taskState == TaskState.DONE) continue;
            updateOverlay(event, task.blockPos, task);
        }
    }

    private static void updateOverlay(Render2DEvent event, BlockPos pos, BlockTask blockTask) {
        HighwayTools m = m();
        Vector3d screenPos = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (!NametagUtils.to2D(screenPos, m.textScale.get())) return;

        List<String> lines = new ArrayList<>();
        if (!m.anonymizeStats.get()) lines.add("Pos: " + Coords.asString(pos));
        if (blockTask != ContainerHandler.containerTask) {
            lines.add(String.format("Start Distance: %.2f", blockTask.startDistance));
            lines.add(String.format("Eye Distance: %.2f", blockTask.eyeDistance));
        } else {
            lines.add("Item: " + BuiltInRegistries.ITEM.getKey(blockTask.item));
        }
        if (blockTask.taskState == TaskState.PLACE || blockTask.taskState == TaskState.LIQUID) {
            lines.add("Depth: " + blockTask.sequence.size());
            if (blockTask.isLiquidSource) lines.add("Liquid Source");
        }
        if (blockTask.isOpen) lines.add("Open");
        if (blockTask.isLoaded) lines.add("Loaded");
        if (blockTask.destroy) lines.add("Destroy");
        if (blockTask.stuckTicks > 0) lines.add("Stuck: " + blockTask.stuckTicks);

        TextRenderer text = TextRenderer.get();
        NametagUtils.begin(screenPos, event.graphics);
        text.beginBig(event.graphics);

        double y = 0;
        double height = text.getHeight();
        for (String line : lines) {
            double halfWidth = text.getWidth(line) / 2.0;
            text.render(line, -halfWidth, y, Color.WHITE);
            y += height + 2;
        }

        text.end();
        NametagUtils.end(event.graphics);
    }
}
