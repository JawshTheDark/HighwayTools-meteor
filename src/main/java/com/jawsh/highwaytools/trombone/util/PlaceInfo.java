package com.jawsh.highwaytools.trombone.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Describes a click that places a block: click block {@code pos} on face {@code side}
 * at {@code hitVec} to place a block at {@code placedPos}.
 */
public record PlaceInfo(BlockPos pos, Direction side, Vec3 hitVec, BlockPos placedPos) {
}
