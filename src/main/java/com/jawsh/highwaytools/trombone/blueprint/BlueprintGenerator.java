package com.jawsh.highwaytools.trombone.blueprint;

import com.jawsh.highwaytools.modules.HighwayTools;
import com.jawsh.highwaytools.trombone.Pathfinder;
import com.jawsh.highwaytools.trombone.Trombone;
import com.jawsh.highwaytools.trombone.util.Direction8;
import com.jawsh.highwaytools.trombone.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BlueprintGenerator {
    public static final Map<BlockPos, BlueprintTask> blueprint = new ConcurrentHashMap<>();

    private static HighwayTools m() {
        return HighwayTools.INSTANCE;
    }

    public static void generateBluePrint() {
        HighwayTools m = m();
        blueprint.clear();
        BlockPos basePos = Pathfinder.currentBlockPos.below();

        if (m.mode.get() == Trombone.Structure.FLAT) {
            generateFlat(basePos);
            return;
        }

        Direction8 zDirection = Pathfinder.startingDirection;
        Direction8 xDirection = zDirection.clockwise(zDirection.isDiagonal() ? 1 : 2);

        double maxReach = m.maxReach.get();
        int from = -(int) Math.floor(maxReach) * 5;
        int to = (int) Math.ceil(maxReach) * 5;

        for (int x = from; x <= to; x++) {
            BlockPos thisPos = zDirection.offset(basePos, x);
            if (m.clearSpace.get()) generateClear(thisPos, xDirection);
            if (m.mode.get() == Trombone.Structure.TUNNEL) {
                if (m.backfill.get()) {
                    generateBackfill(thisPos, xDirection);
                } else {
                    if (m.cleanFloor.get()) generateFloor(thisPos, xDirection);
                    if (m.cleanRightWall.get() || m.cleanLeftWall.get()) generateWalls(thisPos, xDirection);
                    if (m.cleanRoof.get()) generateRoof(thisPos, xDirection);
                    if (m.cleanCorner.get() && !m.cornerBlock.get() && m.width.get() > 2) generateCorner(thisPos, xDirection);
                }
            } else {
                generateBase(thisPos, xDirection);
            }
        }

        if (m.mode.get() == Trombone.Structure.TUNNEL && (!m.cleanFloor.get() || m.backfill.get())) {
            Block filler = m.fillerMat.get();
            int reach = (int) Math.floor(maxReach);
            if (zDirection.isDiagonal()) {
                for (int x = 0; x <= reach; x++) {
                    BlockPos pos = zDirection.offset(basePos, x);
                    blueprint.put(pos, new BlueprintTask(filler, true, false));
                    blueprint.put(zDirection.clockwise(7).offset(pos), new BlueprintTask(filler, true, false));
                }
            } else {
                for (int x = 0; x <= reach; x++) {
                    blueprint.put(zDirection.offset(basePos, x), new BlueprintTask(filler, true, false));
                }
            }
        }
    }

    private static void generateClear(BlockPos basePos, Direction8 xDirection) {
        HighwayTools m = m();
        int width = m.width.get();
        int height = m.height.get();
        boolean highway = m.mode.get() == Trombone.Structure.HIGHWAY;

        for (int w = 0; w < width; w++) {
            for (int h = 0; h < height; h++) {
                int x = w - width / 2;
                BlockPos pos = xDirection.offset(basePos, x).above(h);

                if (highway && h == 0 && isRail(w)) continue;

                if (highway) {
                    blueprint.put(pos, new BlueprintTask(Blocks.AIR));
                } else {
                    if (!(isRail(w) && h == 0 && !m.cornerBlock.get() && width > 2)) {
                        blueprint.put(pos.above(), new BlueprintTask(Blocks.AIR));
                    }
                }
            }
        }
    }

    private static void generateBase(BlockPos basePos, Direction8 xDirection) {
        HighwayTools m = m();
        int width = m.width.get();
        Block material = m.material.get();

        for (int w = 0; w < width; w++) {
            int x = w - width / 2;
            BlockPos pos = xDirection.offset(basePos, x);

            if (m.mode.get() == Trombone.Structure.HIGHWAY && isRail(w)) {
                if (!m.cornerBlock.get() && width > 2 && Pathfinder.startingDirection.isDiagonal()) {
                    blueprint.put(pos, new BlueprintTask(m.fillerMat.get(), false, true));
                }
                int startHeight = (m.cornerBlock.get() && width > 2) ? 0 : 1;
                for (int y = startHeight; y <= m.railingHeight.get(); y++) {
                    blueprint.put(pos.above(y), new BlueprintTask(material));
                }
            } else {
                blueprint.put(pos, new BlueprintTask(material));
            }
        }
    }

    private static void generateFloor(BlockPos basePos, Direction8 xDirection) {
        HighwayTools m = m();
        int width = m.width.get();
        int wid = (m.cornerBlock.get() && width > 2) ? width : width - 2;
        for (int w = 0; w < wid; w++) {
            int x = w - wid / 2;
            blueprint.put(xDirection.offset(basePos, x), new BlueprintTask(m.fillerMat.get(), true, false));
        }
    }

    private static void generateWalls(BlockPos basePos, Direction8 xDirection) {
        HighwayTools m = m();
        int width = m.width.get();
        int cb = (!m.cornerBlock.get() && width > 2) ? 1 : 0;
        for (int h = cb; h < m.height.get(); h++) {
            if (m.cleanRightWall.get()) {
                blueprint.put(xDirection.offset(basePos, width - width / 2).above(h + 1), new BlueprintTask(m.fillerMat.get(), true, false));
            }
            if (m.cleanLeftWall.get()) {
                blueprint.put(xDirection.offset(basePos, -1 - width / 2).above(h + 1), new BlueprintTask(m.fillerMat.get(), true, false));
            }
        }
    }

    private static void generateRoof(BlockPos basePos, Direction8 xDirection) {
        HighwayTools m = m();
        int width = m.width.get();
        for (int w = 0; w < width; w++) {
            int x = w - width / 2;
            blueprint.put(xDirection.offset(basePos, x).above(m.height.get() + 1), new BlueprintTask(m.fillerMat.get(), true, false));
        }
    }

    private static void generateCorner(BlockPos basePos, Direction8 xDirection) {
        HighwayTools m = m();
        int width = m.width.get();
        blueprint.put(xDirection.offset(basePos, -1 - width / 2 + 1).above(), new BlueprintTask(m.fillerMat.get(), true, false));
        blueprint.put(xDirection.offset(basePos, width - width / 2 - 1).above(), new BlueprintTask(m.fillerMat.get(), true, false));
    }

    private static void generateBackfill(BlockPos basePos, Direction8 xDirection) {
        HighwayTools m = m();
        int width = m.width.get();
        int height = m.height.get();
        double startToCurrent = WorldUtils.center(Pathfinder.startingBlockPos).distanceTo(WorldUtils.center(Pathfinder.currentBlockPos));

        for (int w = 0; w < width; w++) {
            for (int h = 0; h < height; h++) {
                int x = w - width / 2;
                BlockPos pos = xDirection.offset(basePos, x).above(h + 1);

                if (WorldUtils.center(Pathfinder.startingBlockPos).distanceTo(WorldUtils.center(pos)) + 1 < startToCurrent) {
                    blueprint.put(pos, new BlueprintTask(m.fillerMat.get(), true, false));
                }
            }
        }
    }

    private static boolean isRail(int w) {
        HighwayTools m = m();
        int width = m.width.get();
        // Rails need at least one floor column between them; narrower highways are floor only.
        return m.railing.get() && width > 2 && (w == 0 || w == width - 1);
    }

    private static void generateFlat(BlockPos basePos) {
        HighwayTools m = m();
        int width = m.width.get();
        int height = m.height.get();

        // Base
        for (int w1 = 0; w1 < width; w1++) {
            for (int w2 = 0; w2 < width; w2++) {
                int x = w1 - width / 2;
                int z = w2 - width / 2;
                blueprint.put(basePos.offset(x, 0, z), new BlueprintTask(m.material.get()));
            }
        }

        // Clear
        if (!m.clearSpace.get()) return;
        for (int w1 = -width; w1 <= width; w1++) {
            for (int w2 = -width; w2 <= width; w2++) {
                for (int y = 1; y < height; y++) {
                    int x = w1 - width / 2;
                    int z = w2 - width / 2;
                    blueprint.put(basePos.offset(x, y, z), new BlueprintTask(Blocks.AIR));
                }
            }
        }
    }

    public static boolean isInsideBlueprint(BlockPos pos) {
        return blueprint.containsKey(pos);
    }

    public static boolean isInsideBlueprintBuild(BlockPos pos) {
        BlueprintTask task = blueprint.get(pos);
        return task != null && task.targetBlock() == m().material.get();
    }
}
