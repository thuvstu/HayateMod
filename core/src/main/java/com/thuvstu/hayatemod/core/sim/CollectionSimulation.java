package com.thuvstu.hayatemod.core.sim;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import com.thuvstu.hayatemod.core.content.model.Models.*;
import com.thuvstu.hayatemod.core.rules.*;

/** Actual loot + deterministic exchange/craft collection policy, without resetting pity on drops. */
public final class CollectionSimulation {
    private CollectionSimulation() { }
    public record Report(long seed, int trials, int completed, double meanCompletedRuns,
            int p50CompletedRuns, int p95CompletedRuns, int maximumCompletedRuns, int censored,
            long guaranteedRuns) { }

    public static Report run(LootTable table, Map<String, WeaponCard> cards, List<String> wanted,
            RuleResolver.Multipliers rules, long seed, int trials, int maxRuns) {
        if (trials < 1 || trials > 100000 || maxRuns < 1 || maxRuns > 100000
                || (long) trials * maxRuns > 10000000 || wanted.isEmpty()
                || wanted.size() > 100 || new HashSet<>(wanted).size() != wanted.size()) {
            throw new IllegalArgumentException("invalid simulation size/targets (work budget 10 million rolls)");
        }
        for (String id : wanted) if (!cards.containsKey(id)) throw new IllegalArgumentException("unknown target " + id);
        Random random = new Random(seed);
        List<Integer> complete = new ArrayList<>();
        for (int trial = 0; trial < trials; trial++) {
            var owned = new HashSet<String>();
            long pity = 0, materials = 0;
            for (int run = 1; run <= maxRuns; run++) {
                var loot = LootRoller.roll(table, rules, random::nextDouble, random::nextInt);
                pity += loot.pity();
                for (var material : loot.materials()) if (material.id().equals("craft_material")) materials += material.count();
                if (!loot.equipment().isEmpty()) {
                    if (!owned.add(loot.equipment())) {
                        var duplicate = cards.get(loot.equipment());
                        if (duplicate != null) {
                            materials += CraftRules.salvageMaterials(duplicate);
                            pity += 2; // Same codex-owned salvage reward as CraftOps.
                        }
                    }
                }
                // Fixed target order is part of the published policy; not an optimal market/travel planner.
                for (String id : wanted) {
                    if (owned.contains(id)) continue;
                    if (id.equals(table.exchangeItem()) && table.exchangeCost() != null && pity >= table.exchangeCost()) {
                        pity -= table.exchangeCost();
                        owned.add(id);
                    } else {
                        var cost = CraftRules.cost(cards.get(id));
                        if (pity >= cost.pity() && materials >= cost.materials()) {
                            pity -= cost.pity();
                            materials -= cost.materials();
                            owned.add(id);
                        }
                    }
                }
                if (owned.containsAll(wanted)) { complete.add(run); break; }
            }
        }
        complete.sort(Integer::compare);
        double mean = complete.stream().mapToInt(Integer::intValue).average().orElse(Double.NaN);
        return new Report(seed, trials, complete.size(), mean, percentile(complete, .5), percentile(complete, .95),
                complete.isEmpty() ? 0 : complete.getLast(), trials - complete.size(),
                guarantee(table, cards, wanted, rules));
    }

    private static int percentile(List<Integer> values, double fraction) {
        return values.isEmpty() ? 0 : values.get((int) Math.ceil(values.size() * fraction) - 1);
    }

    /** Conservative no-drop/no-salvage bound for this fixed-order policy. -1 means no proven bound. */
    public static long guarantee(LootTable table, Map<String, WeaponCard> cards, List<String> wanted,
            RuleResolver.Multipliers rules) {
        long pityPerRun = RuleResolver.rewardCount(table.perKill(), rules.pity(), true);
        long materialPerRun = table.materials().stream().filter(m -> m.item().equals("craft_material"))
                .mapToLong(m -> RuleResolver.rewardCount(m.min(), rules.materials(), false)).sum();
        long total = 0;
        for (String id : wanted) {
            var price = CraftRules.cost(cards.get(id));
            long craft = Math.max(runs(price.materials(), materialPerRun), runs(price.pity(), pityPerRun));
            long exchange = id.equals(table.exchangeItem()) && table.exchangeCost() != null
                    ? runs(table.exchangeCost(), pityPerRun) : Long.MAX_VALUE;
            long bound = Math.min(craft, exchange);
            if (bound == Long.MAX_VALUE) return -1;
            total += bound;
        }
        return total;
    }
    private static long runs(long cost, long perRun) {
        return cost == 0 ? 0 : perRun == 0 ? Long.MAX_VALUE : (cost + perRun - 1) / perRun;
    }
}
