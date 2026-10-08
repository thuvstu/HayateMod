package com.thuvstu.hayatemod.core.rules;

/**
 * Drop-rate math from IMPLEMENTATION.md §5.10.
 *
 * <p>{@code E = [1 - (1 - p)^G] / p}: expected runs to obtain one item with
 * per-run rate {@code p} and a pity guarantee at run {@code G}.
 * {@code p == 0} means pity-only, so {@code E == G}.
 */
public final class DropSolver {
    private DropSolver() {
    }

    public static double expectedRuns(double rate, int guaranteeRuns) {
        if (guaranteeRuns <= 0) {
            throw new IllegalArgumentException("guaranteeRuns must be positive");
        }
        if (rate <= 0.0) {
            return guaranteeRuns;
        }
        if (rate >= 1.0) {
            return 1.0;
        }
        return (1.0 - Math.pow(1.0 - rate, guaranteeRuns)) / rate;
    }

    /**
     * Solves the per-run rate for a target expectation via bisection.
     * Returns 0.0 when the target is only reachable through pity ({@code E >= G}).
     */
    public static double solveRate(double expectedRuns, int guaranteeRuns) {
        if (guaranteeRuns <= 0) {
            throw new IllegalArgumentException("guaranteeRuns must be positive");
        }
        if (expectedRuns >= guaranteeRuns) {
            return 0.0;
        }
        if (expectedRuns <= 1.0) {
            return 1.0;
        }
        double lo = 0.0;
        double hi = 1.0;
        for (int i = 0; i < 200; i++) {
            double mid = (lo + hi) / 2.0;
            if (expectedRuns(mid, guaranteeRuns) > expectedRuns) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return (lo + hi) / 2.0;
    }
}
