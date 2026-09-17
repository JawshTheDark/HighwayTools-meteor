package com.jawsh.highwaytools;

import com.jawsh.highwaytools.commands.HighwayToolsCommand;
import com.jawsh.highwaytools.hud.HighwayToolsHud;
import com.jawsh.highwaytools.modules.HighwayTools;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class HighwayToolsAddon extends MeteorAddon {
    public static final HudGroup HUD_GROUP = new HudGroup("HighwayTools");

    @Override
    public void onInitialize() {
        Modules.get().add(new HighwayTools());
        Commands.add(new HighwayToolsCommand());
        Hud.get().register(HighwayToolsHud.INFO);
    }

    @Override
    public String getPackage() {
        return "com.jawsh.highwaytools";
    }
}
