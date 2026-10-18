package dev.solaris.agent.javaagent;

import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Short supported excavation plans, without invented footing or assumed surface arrival. */
final class CliffStaircase {
    static final int MAX_RISE = 4;
    static final int HEADROOM = 3;
    static final int MAX_SHAFT_DEPTH = 72;

    private CliffStaircase() {
    }

    static Direction toward(double deltaX, double deltaZ) {
        if (Math.abs(deltaX) >= Math.abs(deltaZ)) {
            return deltaX >= 0.0 ? Direction.EAST : Direction.WEST;
        }
        return deltaZ >= 0.0 ? Direction.SOUTH : Direction.NORTH;
    }

    static List<Step> plan(
        Step origin, Direction direction,
        Predicate<Step> supported, Predicate<Step> passable, Predicate<Step> excavatable
    ) {
        List<Step> steps = new ArrayList<>();
        for (int rise = 1; rise <= MAX_RISE; rise++) {
            Step step = new Step(origin.x() + direction.getStepX() * rise, origin.y() + rise,
                origin.z() + direction.getStepZ() * rise);
            if (!supported.test(step.above(-1))) {
                return List.of();
            }
            boolean open = true;
            for (int height = 0; height < HEADROOM; height++) {
                Step block = step.above(height);
                if (!passable.test(block)) {
                    open = false;
                    if (!excavatable.test(block)) {
                        return List.of();
                    }
                }
            }
            steps.add(step);
            if (open) {
                return List.copyOf(steps);
            }
        }
        return List.of();
    }

    static List<Step> route(
        Step origin, Direction direction,
        Predicate<Step> supported, Predicate<Step> passable, Predicate<Step> excavatable
    ) {
        List<Step> staircase = plan(origin, direction, supported, passable, excavatable);
        if (!staircase.isEmpty()) {
            return staircase;
        }
        // A ridge may keep rising above a low target. Cut a short level passage
        // instead of pretending that an unobserved surface exists after four rises.
        List<Step> passage = new ArrayList<>();
        for (int advance = 1; advance <= MAX_RISE; advance++) {
            Step step = new Step(origin.x() + direction.getStepX() * advance, origin.y(),
                origin.z() + direction.getStepZ() * advance);
            if (!supported.test(step.above(-1))) {
                return List.copyOf(passage);
            }
            boolean open = true;
            for (int height = 0; height < HEADROOM; height++) {
                Step block = step.above(height);
                if (!passable.test(block)) {
                    open = false;
                    if (!excavatable.test(block)) {
                        return List.copyOf(passage);
                    }
                }
            }
            passage.add(step);
            if (open) {
                break;
            }
        }
        return List.copyOf(passage);
    }

    /** Longest observed rising prefix; a cave gap may require a turn or an earned pillar. */
    static List<Step> toSurface(
        Step origin, int surfaceY, Direction direction,
        Predicate<Step> supported, Predicate<Step> passable, Predicate<Step> excavatable
    ) {
        List<Step> steps = new ArrayList<>();
        for (int rise = 1; rise <= surfaceY - origin.y(); rise++) {
            Step step = new Step(origin.x() + direction.getStepX() * rise, origin.y() + rise,
                origin.z() + direction.getStepZ() * rise);
            if (!supported.test(step.above(-1))) {
                break;
            }
            boolean clearable = true;
            for (int height = 0; height < HEADROOM; height++) {
                Step block = step.above(height);
                if (!passable.test(block) && !excavatable.test(block)) {
                    clearable = false;
                    break;
                }
            }
            if (!clearable) {
                break;
            }
            steps.add(step);
        }
        return List.copyOf(steps);
    }

    static boolean shaftClearable(Step ore, int surfaceY, Predicate<Step> excavatable) {
        if (surfaceY <= ore.y() || surfaceY - ore.y() > MAX_SHAFT_DEPTH) {
            return false;
        }
        for (int y = surfaceY - 1; y > ore.y(); y--) {
            if (!excavatable.test(new Step(ore.x(), y, ore.z()))) {
                return false;
            }
        }
        return true;
    }

    static boolean pillarFits(Step origin, int surfaceY, int ownedBlocks, Predicate<Step> passable) {
        int depth = surfaceY - origin.y();
        if (depth <= 0 || ownedBlocks < depth) {
            return false;
        }
        for (int y = origin.y() + 2; y <= surfaceY + 2; y++) {
            if (!passable.test(new Step(origin.x(), y, origin.z()))) {
                return false;
            }
        }
        return true;
    }

    static List<Step> takeoffClearance(double minX, double minZ, double maxX, double maxZ, double feetY) {
        List<Step> blocks = new ArrayList<>();
        int ceilingY = (int) Math.floor(feetY) + 2;
        for (int x = (int) Math.floor(minX); x <= (int) Math.floor(Math.nextDown(maxX)); x++) {
            for (int z = (int) Math.floor(minZ); z <= (int) Math.floor(Math.nextDown(maxZ)); z++) {
                blocks.add(new Step(x, ceilingY, z));
            }
        }
        return List.copyOf(blocks);
    }

    record Step(int x, int y, int z) {
        Step above(int height) {
            return new Step(x, y + height, z);
        }
    }
}
