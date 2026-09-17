package com.jawsh.highwaytools.trombone.blueprint;

import net.minecraft.world.level.block.Block;

public record BlueprintTask(Block targetBlock, boolean isFiller, boolean isSupport) {
    public BlueprintTask(Block targetBlock) {
        this(targetBlock, false, false);
    }
}
