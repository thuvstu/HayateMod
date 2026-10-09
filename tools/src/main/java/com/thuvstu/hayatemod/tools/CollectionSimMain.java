package com.thuvstu.hayatemod.tools;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import com.thuvstu.hayatemod.core.content.ContentPipeline;
import com.thuvstu.hayatemod.core.rules.RuleResolver;
import com.thuvstu.hayatemod.core.sim.CollectionSimulation;

/** Seeded collection timing over exactly the loot and recipe logic used in-game. */
public final class CollectionSimMain {
    private CollectionSimMain() { }
    public static void main(String[] args) {
        if (args.length < 5 || args.length > 8) {
            throw new IllegalArgumentException("collectionSim <content> <enemyId> <rulesetId> <minutesPerRun> <itemId,itemId> [seed=42] [trials=1000] [maxRuns=1000]");
        }
        var pack = ContentPipeline.load(Path.of(args[0]));
        if (!pack.ok()) throw new IllegalArgumentException("invalid content: " + pack.loaderErrors() + pack.issues());
        var enemy = pack.set().enemies().get(args[1]);
        if (enemy == null) throw new IllegalArgumentException("unknown enemy " + args[1]);
        double minutes = Double.parseDouble(args[3]);
        if (!Double.isFinite(minutes) || minutes <= 0) throw new IllegalArgumentException("minutes per run must be positive");
        var rules = RuleResolver.resolve(pack.set().rulesets(), args[2], enemy);
        var report = CollectionSimulation.run(pack.set().loot().get(enemy.loot()), pack.set().weapons(),
                List.of(args[4].split(",")), rules, args.length > 5 ? Long.parseLong(args[5]) : 42,
                args.length > 6 ? Integer.parseInt(args[6]) : 1000, args.length > 7 ? Integer.parseInt(args[7]) : 1000);
        System.out.println("policy=fixed target order; exchange then craft; duplicate salvage; no market; travel included in supplied run minutes");
        System.out.println(report);
        System.out.printf(Locale.ROOT, "completed-only meanMinutes=%.2f p95Minutes=%.2f guaranteedUpperMinutes=%s%n",
                report.meanCompletedRuns() * minutes, report.p95CompletedRuns() * minutes,
                report.guaranteedRuns() < 0 ? "unproven" : String.valueOf(report.guaranteedRuns() * minutes));
        if (report.censored() > 0) System.out.println("WARNING: censored trials are NOT successes; completed-only averages are biased downward");
    }
}
