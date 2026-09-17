package com.jawsh.highwaytools.trombone.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Eight-way horizontal direction, equivalent to Lambda's {@code Direction} util.
 */
public enum Direction8 {
    NORTH("North", "Z-", 0, -1),
    NORTH_EAST("North East", "X+ Z-", 1, -1),
    EAST("East", "X+", 1, 0),
    SOUTH_EAST("South East", "X+ Z+", 1, 1),
    SOUTH("South", "Z+", 0, 1),
    SOUTH_WEST("South West", "X- Z+", -1, 1),
    WEST("West", "X-", -1, 0),
    NORTH_WEST("North West", "X- Z-", -1, -1);

    public final String displayName;
    public final String displayNameXY;
    public final int x;
    public final int z;
    public final Vec3i directionVec;

    Direction8(String displayName, String displayNameXY, int x, int z) {
        this.displayName = displayName;
        this.displayNameXY = displayNameXY;
        this.x = x;
        this.z = z;
        this.directionVec = new Vec3i(x, 0, z);
    }

    public boolean isDiagonal() {
        return x != 0 && z != 0;
    }

    public Direction8 clockwise(int n) {
        return values()[(ordinal() + n) & 7];
    }

    public Direction8 counterClockwise(int n) {
        return values()[(ordinal() - n) & 7];
    }

    public BlockPos offset(BlockPos pos, int n) {
        return pos.offset(x * n, 0, z * n);
    }

    public BlockPos offset(BlockPos pos) {
        return pos.offset(x, 0, z);
    }

    public Vec3 toVec3() {
        return new Vec3(x, 0, z);
    }

    public static Direction8 fromEntity(Entity entity) {
        return fromYaw(entity.getYRot());
    }

    public static Direction8 fromYaw(float yaw) {
        float wrapped = Mth.wrapDegrees(yaw) + 180.0f + 22.5f;
        return values()[Mth.floor(wrapped / 45.0f) & 7];
    }
}
