package dev.solaris.loader;

import java.util.Optional;

/** Monitor rectangle discovery and face basis, identical to the native client's rules. */
public final class ScreenGeometry {
    /** Offset in front of the block face that keeps the overlay from z-fighting. */
    public static final double LIFT = 0.002;

    private ScreenGeometry() {}

    /** Whether the block at the coordinates is a monitor whose {@code facing} is the face side. */
    public interface Members {
        boolean isMonitor(int x, int y, int z);
    }

    /** Image-space frame of a face: origin (upper-left seen from outside), image-right, image-down. */
    public record Basis(double[] origin, double[] right, double[] down) {}

    /**
     * The one full rectangle of monitors on {@code side} containing the block, or empty when
     * the connected coplanar set is not an exact rectangle (L shapes, holes) or exceeds 16.
     */
    public static Optional<ScreenProtocol.Id> rectangle(
            int bx, int by, int bz, int side, int dimension, Members member) {
        if (side < 2 || side > 5 || !member.isMonitor(bx, by, bz)) {
            return Optional.empty();
        }
        int axis = side <= 3 ? 0 : 2;
        int[] block = {bx, by, bz};
        int[] minimum = {block[axis], by};
        int[] maximum = {block[axis], by};
        boolean grew = true;
        while (grew) {
            grew = false;
            for (int dimensionIndex = 0; dimensionIndex < 2; dimensionIndex++) {
                for (int direction = 0; direction < 2; direction++) {
                    boolean positive = direction == 1;
                    int edge = positive ? maximum[dimensionIndex] + 1 : minimum[dimensionIndex] - 1;
                    int other = 1 - dimensionIndex;
                    boolean connected = false;
                    for (int c = minimum[other]; c <= maximum[other] && !connected; c++) {
                        connected = at(member, block, axis, dimensionIndex == 0 ? edge : c,
                                dimensionIndex == 0 ? c : edge);
                    }
                    if (connected) {
                        if (maximum[dimensionIndex] - minimum[dimensionIndex] >= 15) {
                            return Optional.empty();
                        }
                        if (positive) {
                            maximum[dimensionIndex] = edge;
                        } else {
                            minimum[dimensionIndex] = edge;
                        }
                        grew = true;
                    }
                }
            }
        }
        for (int y = minimum[1]; y <= maximum[1]; y++) {
            for (int h = minimum[0]; h <= maximum[0]; h++) {
                if (!at(member, block, axis, h, y)) {
                    return Optional.empty();
                }
            }
        }
        int[] anchor = block.clone();
        anchor[axis] = minimum[0];
        anchor[1] = minimum[1];
        return Optional.of(new ScreenProtocol.Id(anchor[0], anchor[1], anchor[2], side,
                maximum[0] - minimum[0] + 1, maximum[1] - minimum[1] + 1, dimension));
    }

    private static boolean at(Members member, int[] block, int axis, int horizontal, int y) {
        int[] point = block.clone();
        point[axis] = horizontal;
        point[1] = y;
        return member.isMonitor(point[0], point[1], point[2]);
    }

    public static Basis basis(ScreenProtocol.Id id) {
        double[] corner;
        double[] right;
        double[] down;
        switch (id.side()) {
            case 2 -> {
                corner = new double[] {1, 1, -LIFT};
                right = new double[] {-1, 0, 0};
                down = new double[] {0, -1, 0};
            }
            case 3 -> {
                corner = new double[] {0, 1, 1 + LIFT};
                right = new double[] {1, 0, 0};
                down = new double[] {0, -1, 0};
            }
            case 4 -> {
                corner = new double[] {-LIFT, 1, 0};
                right = new double[] {0, 0, 1};
                down = new double[] {0, -1, 0};
            }
            default -> {
                corner = new double[] {1 + LIFT, 1, 1};
                right = new double[] {0, 0, -1};
                down = new double[] {0, -1, 0};
            }
        }
        double[] block = {id.x(), id.y(), id.z()};
        double[] origin = new double[3];
        for (int i = 0; i < 3; i++) {
            origin[i] = block[i] + corner[i]
                    + (right[i] < 0 ? id.width() - 1 : 0)
                    + (down[i] < 0 ? id.height() - 1 : 0);
        }
        return new Basis(origin, right, down);
    }

    /** Picture size in blocks after fitting {@code w x h} into the rectangle, one outer bezel kept. */
    public static double[] fit(int w, int h, int width, int height) {
        double boundsW = width - 0.125;
        double boundsH = height - 0.125;
        double scale = Math.min(boundsW / Math.max(w, 1), boundsH / Math.max(h, 1));
        return new double[] {w * scale, h * scale};
    }
}
