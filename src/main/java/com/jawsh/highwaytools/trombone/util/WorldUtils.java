package com.jawsh.highwaytools.trombone.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * World helpers replacing the Lambda {@code WorldUtils} / {@code VectorUtils} extension functions.
 */
public class WorldUtils {
    private static final AABB UNIT_BOX = new AABB(0, 0, 0, 1, 1, 1);

    public static BlockState state(BlockPos pos) {
        return mc.level.getBlockState(pos);
    }

    public static boolean isReplaceable(BlockPos pos) {
        return state(pos).canBeReplaced();
    }

    public static boolean isAir(BlockPos pos) {
        return state(pos).isAir();
    }

    public static boolean isLiquid(BlockState state) {
        return state.getBlock() instanceof LiquidBlock;
    }

    public static boolean isLiquid(BlockPos pos) {
        return isLiquid(state(pos));
    }

    public static boolean isLiquidSource(BlockState state) {
        return isLiquid(state) && state.getFluidState().isSource();
    }

    public static boolean noEntityCollision(BlockPos pos) {
        AABB box = new AABB(pos);
        return mc.level.getEntities((Entity) null, box, e ->
            !(e instanceof ItemEntity) && !(e instanceof ExperienceOrb) && e.isAlive() && e.getBoundingBox().intersects(box)
        ).isEmpty();
    }

    /** Block is replaceable and no entity blocks the placement. */
    public static boolean isPlaceable(BlockPos pos) {
        return isReplaceable(pos) && noEntityCollision(pos);
    }

    public static boolean hasCollision(BlockPos pos) {
        return !state(pos).getCollisionShape(mc.level, pos).isEmpty();
    }

    public static AABB getBox(BlockPos pos) {
        VoxelShape shape = state(pos).getShape(mc.level, pos);
        if (shape.isEmpty()) return new AABB(pos);
        return shape.bounds().move(pos);
    }

    public static Vec3 center(BlockPos pos) {
        return Vec3.atCenterOf(pos);
    }

    public static Vec3 eyePos() {
        return mc.player.getEyePosition(1f);
    }

    public static double distanceTo(BlockPos a, BlockPos b) {
        return Math.sqrt(a.distSqr(b));
    }

    /** Center of the given face of the block. */
    public static Vec3 getHitVec(BlockPos pos, Direction side) {
        return Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(side.getUnitVec3i()).scale(0.5));
    }

    public static boolean isSideVisible(BlockPos pos, Direction side, Vec3 eye) {
        Vec3 faceCenter = getHitVec(pos, side);
        Vec3 normal = Vec3.atLowerCornerOf(side.getUnitVec3i());
        return eye.subtract(faceCenter).dot(normal) > 0;
    }

    public static List<Direction> getVisibleSides(BlockPos pos) {
        Vec3 eye = eyePos();
        List<Direction> sides = new ArrayList<>();
        for (Direction side : Direction.values()) {
            if (isSideVisible(pos, side, eye)) sides.add(side);
        }
        return sides;
    }

    /** The visible, uncovered side of the block closest to the eyes, or null if there is none. */
    public static Direction getMiningSide(BlockPos pos, double range) {
        Vec3 eye = eyePos();
        Direction best = null;
        double bestDist = Double.MAX_VALUE;

        for (Direction side : Direction.values()) {
            if (!isSideVisible(pos, side, eye)) continue;
            if (state(pos.relative(side)).isSolidRender()) continue;

            double dist = eye.distanceTo(getHitVec(pos, side));
            if (dist > range) continue;

            if (dist < bestDist) {
                bestDist = dist;
                best = side;
            }
        }

        return best;
    }

    /** Finds a solid neighbour that can be clicked to place a block at {@code pos}. */
    public static PlaceInfo getNeighbour(BlockPos pos, double range, boolean visibleSideCheck) {
        Vec3 eye = eyePos();
        PlaceInfo best = null;
        double bestDist = Double.MAX_VALUE;

        for (Direction side : Direction.values()) {
            BlockPos neighbour = pos.relative(side);
            BlockState neighbourState = state(neighbour);
            if (neighbourState.canBeReplaced() || neighbourState.getCollisionShape(mc.level, neighbour).isEmpty()) continue;

            Direction hitSide = side.getOpposite();
            Vec3 hitVec = getHitVec(neighbour, hitSide);
            double dist = eye.distanceTo(hitVec);
            if (dist > range) continue;
            if (visibleSideCheck && !isSideVisible(neighbour, hitSide, eye)) continue;

            if (dist < bestDist) {
                bestDist = dist;
                best = new PlaceInfo(neighbour, hitSide, hitVec, pos);
            }
        }

        return best;
    }

    /**
     * Finds a chain of placements ending at {@code pos}.
     * Index 0 is the placement of the target itself, the last element is the placement that is possible right now.
     * Returns an empty list if no placement is possible within {@code attempts} steps.
     */
    public static List<PlaceInfo> getNeighbourSequence(BlockPos pos, int attempts, double range, boolean visibleSideCheck) {
        List<PlaceInfo> sequence = new ArrayList<>();
        Set<BlockPos> checked = new HashSet<>();
        if (searchSequence(pos, attempts, range, visibleSideCheck, sequence, checked)) return sequence;
        return List.of();
    }

    private static boolean searchSequence(BlockPos pos, int attempts, double range, boolean visibleSideCheck, List<PlaceInfo> sequence, Set<BlockPos> checked) {
        if (attempts < 1 || !checked.add(pos)) return false;

        PlaceInfo direct = getNeighbour(pos, range, visibleSideCheck);
        if (direct != null) {
            sequence.add(direct);
            return true;
        }

        if (attempts < 2) return false;

        Vec3 eye = eyePos();
        for (Direction side : Direction.values()) {
            BlockPos support = pos.relative(side);
            if (!isPlaceable(support)) continue;

            Direction hitSide = side.getOpposite();
            Vec3 hitVec = getHitVec(support, hitSide);
            if (eye.distanceTo(hitVec) > range) continue;
            if (visibleSideCheck && !isSideVisible(support, hitSide, eye)) continue;

            if (searchSequence(support, attempts - 1, range, visibleSideCheck, sequence, checked)) {
                sequence.add(0, new PlaceInfo(support, hitSide, hitVec, pos));
                return true;
            }
        }

        return false;
    }

    public static List<BlockPos> getBlockPosInSphere(Vec3 center, double radius) {
        List<BlockPos> list = new ArrayList<>();
        int r = (int) Math.ceil(radius);
        BlockPos origin = BlockPos.containing(center);
        double radiusSq = radius * radius;

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (pos.distToCenterSqr(center.x, center.y, center.z) <= radiusSq) list.add(pos);
                }
            }
        }

        return list;
    }

    /** Ray traces the unit box of {@code pos} from {@code from} along {@code direction}. */
    public static BlockHitResult rayTraceBlock(BlockPos pos, Vec3 from, Vec3 direction, double range) {
        Vec3 to = from.add(direction.normalize().scale(range));
        BlockHitResult result = AABB.clip(List.of(UNIT_BOX), from, to, pos);
        if (result == null || result.getType() != HitResult.Type.BLOCK) return null;
        return result;
    }

    /** Ticks needed to break the block at {@code pos} with the current held item. */
    public static int ticksNeeded(BlockPos pos, double factor) {
        BlockState state = state(pos);
        float progress = state.getDestroyProgress(mc.player, mc.level, pos);
        if (progress <= 0) return Integer.MAX_VALUE;
        return (int) Math.ceil((1.0 / progress) * factor);
    }
}
