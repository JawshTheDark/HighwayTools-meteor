package com.jawsh.highwaytools.hud;

import com.jawsh.highwaytools.HighwayToolsAddon;
import com.jawsh.highwaytools.modules.HighwayTools;
import com.jawsh.highwaytools.trombone.Statistics;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import java.util.ArrayList;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class HighwayToolsHud extends HudElement {
    public static final HudElementInfo<HighwayToolsHud> INFO = new HudElementInfo<>(HighwayToolsAddon.HUD_GROUP, "highway-tools", "Statistics of the HighwayTools module.", HighwayToolsHud::new);
    public static HighwayToolsHud INSTANCE;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSections = settings.createGroup("Sections");
    private final SettingGroup sgStyle = settings.createGroup("Style");

    public final Setting<Integer> simpleMovingAverageRange = sgGeneral.add(new IntSetting.Builder()
        .name("moving-average").description("Timeframe of the moving average in seconds.").defaultValue(60).range(5, 600).sliderRange(5, 600).build());
    public final Setting<Boolean> resetStats = sgGeneral.add(new BoolSetting.Builder()
        .name("reset-stats").description("Toggle to reset the stats.").defaultValue(false)
        .onChanged(value -> {
            if (value) {
                Statistics.resetStats();
                resetPending = true;
            }
        }).build());

    public final Setting<Boolean> showSession = sgSections.add(new BoolSetting.Builder()
        .name("show-session").description("Toggles the Session section.").defaultValue(true).build());
    public final Setting<Boolean> showLifeTime = sgSections.add(new BoolSetting.Builder()
        .name("show-lifetime").description("Toggles the Lifetime section.").defaultValue(true).build());
    public final Setting<Boolean> showPerformance = sgSections.add(new BoolSetting.Builder()
        .name("show-performance").description("Toggles the Performance section.").defaultValue(true).build());
    public final Setting<Boolean> showEnvironment = sgSections.add(new BoolSetting.Builder()
        .name("show-environment").description("Toggles the Environment section.").defaultValue(true).build());
    public final Setting<Boolean> showTask = sgSections.add(new BoolSetting.Builder()
        .name("show-task").description("Toggles the Task section.").defaultValue(true).build());
    public final Setting<Boolean> showEstimations = sgSections.add(new BoolSetting.Builder()
        .name("show-estimations").description("Toggles the Estimations section.").defaultValue(true).build());
    public final Setting<Boolean> showQueue = sgSections.add(new BoolSetting.Builder()
        .name("show-queue").description("Shows the task queue.").defaultValue(false).build());

    public final Setting<Double> scale = sgStyle.add(new DoubleSetting.Builder()
        .name("scale").description("Text scale.").defaultValue(1.0).range(0.5, 3.0).sliderRange(0.5, 3.0).build());
    public final Setting<Boolean> shadow = sgStyle.add(new BoolSetting.Builder()
        .name("shadow").description("Text shadow.").defaultValue(true).build());
    public final Setting<SettingColor> primaryColor = sgStyle.add(new ColorSetting.Builder()
        .name("primary-color").description("Color of labels.").defaultValue(new SettingColor(255, 255, 255)).build());
    public final Setting<SettingColor> secondaryColor = sgStyle.add(new ColorSetting.Builder()
        .name("secondary-color").description("Color of values.").defaultValue(new SettingColor(35, 188, 254)).build());

    private final List<Statistics.Line> lines = new ArrayList<>();
    private boolean resetPending = false;

    public HighwayToolsHud() {
        super(INFO);
        INSTANCE = this;
    }

    @Override
    public void tick(HudRenderer renderer) {
        if (resetPending) {
            resetPending = false;
            resetStats.set(false);
        }

        lines.clear();
        HighwayTools module = HighwayTools.INSTANCE;

        if (mc.player == null || module == null || !module.isActive()) {
            lines.add(Statistics.Line.header("HighwayTools"));
            lines.add(Statistics.Line.of("Status:", "Inactive"));
        } else {
            Statistics.gatherStatistics(lines, this);
        }

        double width = 0;
        double height = 0;
        for (Statistics.Line line : lines) {
            double lineScale = scale.get() * line.scale();
            double w = renderer.textWidth(line.label(), shadow.get(), lineScale);
            if (line.value() != null) w += renderer.textWidth(" " + line.value(), shadow.get(), lineScale);
            width = Math.max(width, w);
            height += renderer.textHeight(shadow.get(), lineScale);
        }

        setSize(width, height);
    }

    @Override
    public void render(HudRenderer renderer) {
        double y = this.y;
        Color primary = primaryColor.get();
        Color secondary = secondaryColor.get();

        for (Statistics.Line line : lines) {
            double lineScale = scale.get() * line.scale();
            double x = this.x;
            x += renderer.text(line.label(), x, y, primary, shadow.get(), lineScale);
            if (line.value() != null) {
                renderer.text(" " + line.value(), x, y, secondary, shadow.get(), lineScale);
            }
            y += renderer.textHeight(shadow.get(), lineScale);
        }
    }
}
