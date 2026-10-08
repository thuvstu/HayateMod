package com.thuvstu.hayatemod.tools;

import com.thuvstu.hayatemod.core.rules.DropSolver;

/**
 * {@code dropSolve}: drop-rate solver (§5.10). Usage:
 * {@code dropSolve <expectedRuns> <guaranteeRuns>}. With no args, reprints the
 * two design-table rows as a self-check.
 */
public final class DropSolveMain {
    private DropSolveMain() {
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            solve("dungeon boss", 8.0, 12);
            solve("named", 24.0, 36);
            return;
        }
        solve("query", Double.parseDouble(args[0]), Integer.parseInt(args[1]));
    }

    private static void solve(String label, double expectedRuns, int guaranteeRuns) {
        double p = DropSolver.solveRate(expectedRuns, guaranteeRuns);
        double check = DropSolver.expectedRuns(p, guaranteeRuns);
        System.out.println(label + ": E=" + expectedRuns + " G=" + guaranteeRuns
                + " -> p=" + String.format("%.4f", p) + " (" + String.format("%.2f", p * 100) + "%)"
                + " recheck E=" + String.format("%.3f", check));
    }
}
