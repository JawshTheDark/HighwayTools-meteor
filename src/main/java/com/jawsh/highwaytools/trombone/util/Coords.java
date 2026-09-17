package com.jawsh.highwaytools.trombone.util;

import net.minecraft.core.BlockPos;

public class Coords {
    public static String asString(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
