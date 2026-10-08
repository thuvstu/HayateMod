package com.thuvstu.hayatemod.core.build;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Finds real ground for structures (town/arena): land above sea level, not
 * water, with a tolerable slope across the footprint. Pure and deterministic:
 * candidates are visited in ascending distance from the hint, so the same
 * world always yields the same spot without persistence.
 */
public final class GroundFinder {
    private GroundFinder() {
    }

    /** One vertical column sample supplied by the game. */
    public record Column(int surfaceY, boolean land) {
    }

    public interface Sampler {
        Column sample(int x, int z);
    }

    public record Found(int x, int y, int z) {
    }

    /**
     * @param hintX/hintZ search center (content/world.yaml)
     * @param seaLevel   columns at or below this Y are treated as sea
     * @param half       structure half-size; corners are checked at +-half
     * @param maxSlope   max allowed |cornerY - centerY|
     * @param radius     max search distance from the hint
     * @param step       candidate grid step
     */
    public static Optional<Found> find(Sampler sampler, int hintX, int hintZ, int seaLevel,
            int half, int maxSlope, int radius, int step) {
        List<int[]> candidates = new ArrayList<>();
        candidates.add(new int[] {hintX, hintZ});
        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                candidates.add(new int[] {hintX + dx, hintZ + dz});
            }
        }
        candidates.sort(Comparator.comparingInt(p ->
                (p[0] - hintX) * (p[0] - hintX) + (p[1] - hintZ) * (p[1] - hintZ)));
        for (int[] p : candidates) {
            Column center = sampler.sample(p[0], p[1]);
            if (!land(center, seaLevel)) {
                continue;
            }
            boolean ok = true;
            for (int[] corner : new int[][] {
                    {p[0] - half, p[1] - half}, {p[0] + half, p[1] - half},
                    {p[0] - half, p[1] + half}, {p[0] + half, p[1] + half}}) {
                Column c = sampler.sample(corner[0], corner[1]);
                if (!land(c, seaLevel) || Math.abs(c.surfaceY() - center.surfaceY()) > maxSlope) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return Optional.of(new Found(p[0], center.surfaceY(), p[1]));
            }
        }
        return Optional.empty();
    }

    private static boolean land(Column c, int seaLevel) {
        return c.land() && c.surfaceY() > seaLevel;
    }
}
