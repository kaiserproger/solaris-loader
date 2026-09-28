package dev.solaris.agent.javaagent;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static dev.solaris.agent.javaagent.ScenarioClientState.requireInPlay;

/** Finds loaded block targets and constructs interaction geometry on the client thread. */
final class ScenarioBlockTargets {
    private ScenarioBlockTargets() {
    }
    static final double SURVIVAL_REACH_SQUARED = 20.25;
    private static final double FAR_REACH_SQUARED = 25.0;

    private static final int NATURAL_BREAKABLE_SCAN_RADIUS = 64;
    private static final int NATURAL_BREAKABLE_SCAN_DOWN = 32;
    private static final int NATURAL_BREAKABLE_SCAN_UP = 16;
    private static final int APPROACHABLE_SCAN_DOWN = 4;
    private static final int APPROACHABLE_SCAN_UP = 5;
    private static final int[] NATURAL_BREAKABLE_SCAN_VERTICAL_OFFSETS = naturalBreakableScanVerticalOffsets();
    static final Direction[] HORIZONTAL_DIRECTIONS = {
        Direction.EAST,
        Direction.WEST,
        Direction.SOUTH,
        Direction.NORTH
    };
    static final Direction[] PLACE_DIRECTIONS = {
        Direction.UP,
        Direction.EAST,
        Direction.WEST,
        Direction.SOUTH,
        Direction.NORTH
    };
    private static final Direction[] BREAK_DIRECTIONS = {
        Direction.EAST,
        Direction.WEST,
        Direction.SOUTH,
        Direction.NORTH,
        Direction.UP,
        Direction.DOWN
    };

    static ScenarioBlockPair findOccupiedPairOnClientThread(ScenarioReach reach) {
        Minecraft minecraft = requireInPlay();
        BlockPos origin = minecraft.player.blockPosition();
        Vec3 eye = minecraft.player.getEyePosition();
        for (int radius = 1; radius <= NATURAL_BREAKABLE_SCAN_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    for (int dy = -3; dy <= 1; dy++) {
                        BlockPos clicked = origin.offset(dx, dy, dz);
                        ScenarioBlockPair pair = firstSolidNeighbourPair(clicked, eye, reach);
                        if (pair != null) {
                            return pair;
                        }
                    }
                }
            }
        }
        return null;
    }

    static ScenarioBlockPair findPlaceablePairOnClientThread(ScenarioReach reach, boolean requireDryTarget) {
        return findPlaceablePairOnClientThread(reach, requireDryTarget, false);
    }

    static ScenarioBlockPair findPlaceablePairOnClientThread(
        ScenarioReach reach,
        boolean requireDryTarget,
        boolean requirePlayerClearance
    ) {
        return findPlaceablePairOnClientThread(
            reach,
            requireDryTarget,
            requirePlayerClearance,
            true,
            PLACE_DIRECTIONS
        );
    }

    static ScenarioBlockPair findPlaceablePairOnClientThread(
        ScenarioReach reach,
        boolean requireDryTarget,
        boolean requirePlayerClearance,
        Direction[] directions
    ) {
        return findPlaceablePairOnClientThread(reach, requireDryTarget, requirePlayerClearance, true, directions);
    }

    static ScenarioBlockPair findPlaceablePairOnClientThread(
        ScenarioReach reach,
        boolean requireDryTarget,
        boolean requirePlayerClearance,
        boolean requireDirtSupport,
        Direction[] directions
    ) {
        Minecraft minecraft = requireInPlay();
        BlockPos origin = minecraft.player.blockPosition();
        Vec3 eye = minecraft.player.getEyePosition();
        for (int radius = 1; radius <= NATURAL_BREAKABLE_SCAN_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    for (int dy = -3; dy <= 1; dy++) {
                        BlockPos clicked = origin.offset(dx, dy, dz);
                        ScenarioBlockPair pair = firstPlaceableNeighbourPair(
                            clicked,
                            eye,
                            reach,
                            requireDryTarget,
                            requirePlayerClearance,
                            requireDirtSupport,
                            directions
                        );
                        if (pair != null) {
                            return pair;
                        }
                    }
                }
            }
        }
        return null;
    }

    static ScenarioBlockPair findHorizontalAttachmentPairOnClientThread(
        ScenarioBlockTarget support,
        ScenarioReach reach
    ) {
        Minecraft minecraft = requireInPlay();
        BlockPos supportPos = pos(support);
        if (!minecraft.level.isLoaded(supportPos) || minecraft.level.getBlockState(supportPos).isAir()) {
            return null;
        }
        Vec3 eye = minecraft.player.getEyePosition();
        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            BlockPos target = supportPos.relative(direction);
            if (!isEmptyLoaded(target) || isPlayerSpace(target) || !isFluidNeighbourhoodEmpty(target)) {
                continue;
            }
            double distance = eye.distanceToSqr(Vec3.atLowerCornerWithOffset(
                supportPos,
                cursorX(direction),
                0.5,
                cursorZ(direction)
            ));
            if (reach == ScenarioReach.WITHIN_SURVIVAL_REACH && distance > SURVIVAL_REACH_SQUARED) {
                continue;
            }
            if (reach == ScenarioReach.OUTSIDE_SURVIVAL_REACH && distance <= FAR_REACH_SQUARED) {
                continue;
            }
            return new ScenarioBlockPair(
                new ScenarioBlockTarget(
                    support.x(),
                    support.y(),
                    support.z(),
                    direction.getName(),
                    "horizontal-attachment-support",
                    blockIdAt(support)
                ),
                new ScenarioBlockTarget(
                    target.getX(),
                    target.getY(),
                    target.getZ(),
                    direction.getOpposite().getName(),
                    "horizontal-attachment-target",
                    blockIdAt(target)
                )
            );
        }
        return null;
    }

    static ScenarioBlockPair findTillableSoilOnClientThread(ScenarioReach reach) {
        Minecraft minecraft = requireInPlay();
        BlockPos origin = minecraft.player.blockPosition();
        Vec3 eye = minecraft.player.getEyePosition();
        for (int radius = 1; radius <= NATURAL_BREAKABLE_SCAN_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    for (int dy = -3; dy <= 1; dy++) {
                        BlockPos clicked = origin.offset(dx, dy, dz);
                        if (!isSolidLoaded(clicked)) {
                            continue;
                        }
                        String blockId = blockIdAt(clicked);
                        if (!isTillableSoilBlockId(blockId)) {
                            continue;
                        }
                        BlockPos target = clicked.above();
                        if (!isEmptyLoaded(target)
                            || isPlayerSpace(target)
                            || !isFluidNeighbourhoodEmpty(target)) {
                            continue;
                        }
                        double distance = eye.distanceToSqr(Vec3.atLowerCornerWithOffset(
                            clicked,
                            0.5,
                            1.0,
                            0.5
                        ));
                        if (reach == ScenarioReach.WITHIN_SURVIVAL_REACH
                            && distance > SURVIVAL_REACH_SQUARED) {
                            continue;
                        }
                        if (reach == ScenarioReach.OUTSIDE_SURVIVAL_REACH
                            && distance <= FAR_REACH_SQUARED) {
                            continue;
                        }
                        return new ScenarioBlockPair(
                            new ScenarioBlockTarget(
                                clicked.getX(),
                                clicked.getY(),
                                clicked.getZ(),
                                "up",
                                reach.label() + "-tillable-soil",
                                blockId
                            ),
                            new ScenarioBlockTarget(
                                target.getX(),
                                target.getY(),
                                target.getZ(),
                                "down",
                                reach.label() + "-crop-target",
                                blockIdAt(target)
                            )
                        );
                    }
                }
            }
        }
        return null;
    }

    static ScenarioBlockTarget findBreakableBlockOnClientThread(List<String> blockIds, ScenarioReach reach) {
        Minecraft minecraft = requireInPlay();
        BlockPos origin = minecraft.player.blockPosition();
        Vec3 eye = minecraft.player.getEyePosition();
        for (int radius = 1; radius <= NATURAL_BREAKABLE_SCAN_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    for (int dy : NATURAL_BREAKABLE_SCAN_VERTICAL_OFFSETS) {
                        if (reach == ScenarioReach.OUTSIDE_SURVIVAL_REACH && !isApproachableVerticalOffset(dy)) {
                            continue;
                        }
                        BlockPos target = origin.offset(dx, dy, dz);
                        ScenarioBlockTarget block = breakableBlockTarget(target, eye, reach, blockIds);
                        if (block != null) {
                            return block;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static int[] naturalBreakableScanVerticalOffsets() {
        int[] offsets = new int[NATURAL_BREAKABLE_SCAN_DOWN + NATURAL_BREAKABLE_SCAN_UP + 1];
        int index = 0;
        offsets[index++] = 0;
        int max = Math.max(NATURAL_BREAKABLE_SCAN_DOWN, NATURAL_BREAKABLE_SCAN_UP);
        for (int step = 1; step <= max; step++) {
            if (step <= NATURAL_BREAKABLE_SCAN_UP) {
                offsets[index++] = step;
            }
            if (step <= NATURAL_BREAKABLE_SCAN_DOWN) {
                offsets[index++] = -step;
            }
        }
        return offsets;
    }

    private static boolean isApproachableVerticalOffset(int dy) {
        return dy >= -APPROACHABLE_SCAN_DOWN && dy <= APPROACHABLE_SCAN_UP;
    }

    static boolean clearFoliageObstacleTowardOnClientThread(ScenarioBlockTarget target) {
        Minecraft minecraft = requireInPlay();
        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 center = Vec3.atLowerCornerWithOffset(pos(target), 0.5, 0.5, 0.5);
        Vec3 delta = center.subtract(eye);
        int steps = Math.max(1, (int) Math.ceil(delta.length() * 4.0));
        BlockPos targetPos = pos(target);
        for (int step = 1; step < steps; step++) {
            double scale = (double) step / (double) steps;
            Vec3 sample = eye.add(delta.scale(scale));
            BlockPos obstacle = BlockPos.containing(sample);
            if (clearFoliageBlockOnClientThread(minecraft, obstacle, targetPos)) {
                return true;
            }
        }
        return clearNearbyFoliageTowardOnClientThread(minecraft, targetPos, center);
    }

    private static boolean clearNearbyFoliageTowardOnClientThread(
        Minecraft minecraft,
        BlockPos targetPos,
        Vec3 targetCenter
    ) {
        BlockPos origin = minecraft.player.blockPosition();
        double currentDistance = minecraft.player.position().distanceToSqr(targetCenter);
        for (int radius = 1; radius <= 2; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    for (int dy = 0; dy <= 2; dy++) {
                        BlockPos obstacle = origin.offset(dx, dy, dz);
                        Vec3 obstacleCenter = Vec3.atLowerCornerWithOffset(obstacle, 0.5, 0.5, 0.5);
                        if (obstacleCenter.distanceToSqr(targetCenter) >= currentDistance) {
                            continue;
                        }
                        if (clearFoliageBlockOnClientThread(minecraft, obstacle, targetPos)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean clearFoliageBlockOnClientThread(
        Minecraft minecraft,
        BlockPos obstacle,
        BlockPos targetPos
    ) {
        if (obstacle.equals(targetPos) || !minecraft.level.isLoaded(obstacle)) {
            return false;
        }
        BlockState state = minecraft.level.getBlockState(obstacle);
        if (state.isAir()) {
            return false;
        }
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if (!blockId.endsWith("_leaves")) {
            return false;
        }
        minecraft.gameMode.startDestroyBlock(obstacle, Direction.UP);
        minecraft.gameMode.continueDestroyBlock(obstacle, Direction.UP);
        minecraft.player.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    static ScenarioBlockTarget breakableBlockTarget(
        BlockPos target,
        Vec3 eye,
        ScenarioReach reach,
        List<String> blockIds
    ) {
        if (!isNonAirLoaded(target)) {
            return null;
        }
        String blockId = blockIdAt(target);
        if (!blockIds.contains(blockId)) {
            return null;
        }
        Direction closestDirection = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        for (Direction direction : BREAK_DIRECTIONS) {
            if (!isBreakFaceAccessible(target, direction)) {
                continue;
            }
            double distance = eye.distanceToSqr(Vec3.atLowerCornerWithOffset(
                target,
                cursorX(direction),
                cursorY(direction),
                cursorZ(direction)
            ));
            if (reach == ScenarioReach.WITHIN_SURVIVAL_REACH && distance > SURVIVAL_REACH_SQUARED) {
                continue;
            }
            if (reach == ScenarioReach.OUTSIDE_SURVIVAL_REACH && distance <= FAR_REACH_SQUARED) {
                continue;
            }
            if (distance < closestDistance) {
                closestDirection = direction;
                closestDistance = distance;
            }
        }
        if (closestDirection == null) {
            return null;
        }
        return new ScenarioBlockTarget(
            target.getX(),
            target.getY(),
            target.getZ(),
            closestDirection.getName(),
            reach.label() + "-breakable",
            blockId
        );
    }

    static Direction closestAccessibleFace(BlockPos target, Vec3 eye) {
        Direction closestDirection = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        for (Direction direction : BREAK_DIRECTIONS) {
            if (!isBreakFaceAccessible(target, direction)) {
                continue;
            }
            double distance = eye.distanceToSqr(Vec3.atLowerCornerWithOffset(
                target,
                cursorX(direction),
                cursorY(direction),
                cursorZ(direction)
            ));
            if (distance < closestDistance) {
                closestDirection = direction;
                closestDistance = distance;
            }
        }
        return closestDirection;
    }

    private static boolean isBreakFaceAccessible(BlockPos target, Direction direction) {
        Minecraft minecraft = requireInPlay();
        BlockPos neighbour = target.relative(direction);
        if (!minecraft.level.isLoaded(neighbour)) {
            return false;
        }
        BlockState state = minecraft.level.getBlockState(neighbour);
        return state.isAir() || !state.blocksMotion();
    }

    private static ScenarioBlockPair firstSolidNeighbourPair(
        BlockPos clicked,
        Vec3 eye,
        ScenarioReach reach
    ) {
        if (!isSolidLoaded(clicked)) {
            return null;
        }
        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            BlockPos target = clicked.relative(direction);
            if (!isSolidLoaded(target)) {
                continue;
            }
            double distance = eye.distanceToSqr(Vec3.atLowerCornerWithOffset(
                clicked,
                cursorX(direction),
                0.5,
                cursorZ(direction)
            ));
            if (reach == ScenarioReach.WITHIN_SURVIVAL_REACH && distance > SURVIVAL_REACH_SQUARED) {
                continue;
            }
            if (reach == ScenarioReach.OUTSIDE_SURVIVAL_REACH && distance <= FAR_REACH_SQUARED) {
                continue;
            }
            String face = direction.getName();
            return new ScenarioBlockPair(
                new ScenarioBlockTarget(
                    clicked.getX(),
                    clicked.getY(),
                    clicked.getZ(),
                    face,
                    reach.label() + "-clicked",
                    blockIdAt(clicked)
                ),
                new ScenarioBlockTarget(
                    target.getX(),
                    target.getY(),
                    target.getZ(),
                    direction.getOpposite().getName(),
                    reach.label() + "-target",
                    blockIdAt(target)
                )
            );
        }
        return null;
    }

    private static ScenarioBlockPair firstPlaceableNeighbourPair(
        BlockPos clicked,
        Vec3 eye,
        ScenarioReach reach,
        boolean requireDryTarget,
        boolean requirePlayerClearance,
        boolean requireDirtSupport,
        Direction[] directions
    ) {
        if (!isSolidLoaded(clicked)) {
            return null;
        }
        String clickedBlockId = blockIdAt(clicked);
        if (requireDirtSupport && !dropsAsDirt(clickedBlockId)) {
            return null;
        }
        if (!requireDirtSupport) {
            Minecraft minecraft = requireInPlay();
            BlockState state = minecraft.level.getBlockState(clicked);
            if (!state.isCollisionShapeFullBlock(minecraft.level, clicked)
                || state.getMenuProvider(minecraft.level, clicked) != null) {
                return null;
            }
        }
        for (Direction direction : directions) {
            BlockPos target = clicked.relative(direction);
            if (!isEmptyLoaded(target) || isPlayerSpace(target)) {
                continue;
            }
            if (requireDryTarget && !isFluidNeighbourhoodEmpty(target)) {
                continue;
            }
            if (
                requirePlayerClearance
                    && !hasFullBlockPlacementClearance(target)
            ) {
                continue;
            }
            double distance = eye.distanceToSqr(Vec3.atLowerCornerWithOffset(
                clicked,
                cursorX(direction),
                cursorY(direction),
                cursorZ(direction)
            ));
            if (reach == ScenarioReach.WITHIN_SURVIVAL_REACH && distance > SURVIVAL_REACH_SQUARED) {
                continue;
            }
            if (reach == ScenarioReach.OUTSIDE_SURVIVAL_REACH && distance <= FAR_REACH_SQUARED) {
                continue;
            }
            return new ScenarioBlockPair(
                new ScenarioBlockTarget(
                    clicked.getX(),
                    clicked.getY(),
                    clicked.getZ(),
                    direction.getName(),
                    reach.label() + "-place-clicked",
                    clickedBlockId
                ),
                new ScenarioBlockTarget(
                    target.getX(),
                    target.getY(),
                    target.getZ(),
                    direction.getOpposite().getName(),
                    reach.label() + "-place-target",
                    blockIdAt(target)
                )
            );
        }
        return null;
    }

    private static boolean hasFullBlockPlacementClearance(BlockPos target) {
        Minecraft minecraft = requireInPlay();
        BlockState state = Blocks.CRAFTING_TABLE.defaultBlockState();
        return BlockPlacementClearance.allowsFullBlockPlacement(
            state.canSurvive(minecraft.level, target),
            minecraft.level.isUnobstructed(
                state,
                target,
                CollisionContext.placementContext(minecraft.player)
            )
        );
    }

    static boolean isPlayerPassable(BlockPos pos) {
        Minecraft minecraft = requireInPlay();
        if (!minecraft.level.isLoaded(pos)) {
            return false;
        }
        BlockState state = minecraft.level.getBlockState(pos);
        return (state.isAir() || !state.blocksMotion()) && minecraft.level.getFluidState(pos).isEmpty();
    }

    private static boolean dropsAsDirt(String blockId) {
        return switch (blockId) {
            case "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:grass_block", "minecraft:podzol" -> true;
            default -> false;
        };
    }

    private static boolean isTillableSoilBlockId(String blockId) {
        return switch (blockId) {
            case "minecraft:dirt", "minecraft:grass_block", "minecraft:dirt_path" -> true;
            default -> false;
        };
    }

    private static boolean isSolidLoaded(BlockPos pos) {
        Minecraft minecraft = requireInPlay();
        if (!minecraft.level.isLoaded(pos)) {
            return false;
        }
        BlockState state = minecraft.level.getBlockState(pos);
        return !state.isAir() && state.blocksMotion();
    }

    private static boolean isNonAirLoaded(BlockPos pos) {
        Minecraft minecraft = requireInPlay();
        return minecraft.level.isLoaded(pos) && !minecraft.level.getBlockState(pos).isAir();
    }

    private static boolean isEmptyLoaded(BlockPos pos) {
        Minecraft minecraft = requireInPlay();
        if (!minecraft.level.isLoaded(pos)) {
            return false;
        }
        return minecraft.level.getBlockState(pos).isAir()
            && minecraft.level.getFluidState(pos).isEmpty();
    }

    private static boolean isFluidNeighbourhoodEmpty(BlockPos pos) {
        Minecraft minecraft = requireInPlay();
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = pos.relative(direction);
            if (!minecraft.level.isLoaded(neighbour)) {
                return false;
            }
            if (!minecraft.level.getFluidState(neighbour).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isPlayerSpace(BlockPos pos) {
        Minecraft minecraft = requireInPlay();
        var playerBox = minecraft.player.getBoundingBox();
        return BlockPlacementClearance.intersects(
            playerBox.minX,
            playerBox.minY,
            playerBox.minZ,
            playerBox.maxX,
            playerBox.maxY,
            playerBox.maxZ,
            pos.getX(),
            pos.getY(),
            pos.getZ()
        );
    }

    static String blockIdAt(ScenarioBlockTarget target) {
        return blockIdAt(pos(target));
    }

    static String blockIdAt(BlockPos pos) {
        Minecraft minecraft = requireInPlay();
        BlockState state = minecraft.level.getBlockState(pos);
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    static boolean blockPropertyMatches(ScenarioBlockTarget target, String property, String value) {
        Minecraft minecraft = requireInPlay();
        BlockState state = minecraft.level.getBlockState(pos(target));
        return state.getValues().anyMatch(propertyValue ->
            Objects.equals(propertyValue.property().getName(), property)
                && Objects.equals(propertyValue.valueName(), value)
        );
    }

    static BlockHitResult hitResult(ScenarioBlockTarget target) {
        Direction direction = direction(target.face());
        return new BlockHitResult(
            Vec3.atLowerCornerWithOffset(pos(target), cursorX(direction), cursorY(direction), cursorZ(direction)),
            direction,
            pos(target),
            false
        );
    }

    static BlockHitResult hitResult(ScenarioBlockTarget target, double cursorHeight) {
        Direction direction = direction(target.face());
        return new BlockHitResult(
            Vec3.atLowerCornerWithOffset(pos(target), cursorX(direction), cursorHeight, cursorZ(direction)),
            direction,
            pos(target),
            false
        );
    }

    static Direction direction(String face) {
        Direction direction = Direction.byName(face.toLowerCase(Locale.ROOT));
        if (direction == null) {
            throw new IllegalArgumentException("unknown face: " + face);
        }
        return direction;
    }

    private static double cursorX(Direction direction) {
        return switch (direction) {
            case EAST -> 1.0;
            case WEST -> 0.0;
            default -> 0.5;
        };
    }

    private static double cursorY(Direction direction) {
        return switch (direction) {
            case UP -> 1.0;
            case DOWN -> 0.0;
            default -> 0.5;
        };
    }

    private static double cursorZ(Direction direction) {
        return switch (direction) {
            case SOUTH -> 1.0;
            case NORTH -> 0.0;
            default -> 0.5;
        };
    }

    static BlockPos pos(ScenarioBlockTarget target) {
        return new BlockPos(target.x(), target.y(), target.z());
    }
}
