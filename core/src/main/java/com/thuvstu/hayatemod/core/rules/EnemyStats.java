package com.thuvstu.hayatemod.core.rules;

import java.util.Comparator;
import java.util.List;

import com.thuvstu.hayatemod.core.content.model.Models.RankTarget;
import com.thuvstu.hayatemod.core.content.model.Models.ReferenceEntry;

/**
 * Enemy stat derivation (IMPLEMENTATION.md §5.6). Enemies only declare level,
 * rank, species, tags, skills and loot; HP and DPS come from the reference
 * table, rank targets and the ruleset multiplier.
 *
 * <pre>
 * enemy HP ≈ reference DPS × rank fight seconds × hpMult
 * enemy DPS ≈ reference HP × damage taken ratio / fight seconds × dpsMult
 * </pre>
 */
public final class EnemyStats {
    private EnemyStats() {
    }

    public record DerivedStats(double maxHp, double dps) {
    }

    public static DerivedStats derive(int level, String rank, List<ReferenceEntry> reference,
            java.util.Map<String, RankTarget> targets, double hpMult, double dpsMult) {
        RankTarget target = targets.get(rank);
        if (target == null) {
            throw new IllegalArgumentException("unknown rank '" + rank + "'");
        }
        double[] ref = interpolate(reference, level);
        double hpRef = ref[0];
        double dpsRef = ref[1];
        double maxHp = dpsRef * target.fightSeconds() * hpMult;
        double dps = hpRef * target.damageTakenRatio() / target.fightSeconds() * dpsMult;
        return new DerivedStats(maxHp, dps);
    }

    /** Returns {hp, dps} from the reference table, lerping between rows. */
    static double[] interpolate(List<ReferenceEntry> reference, int level) {
        if (reference.isEmpty()) {
            throw new IllegalArgumentException("empty reference table");
        }
        List<ReferenceEntry> sorted = reference.stream()
                .sorted(Comparator.comparingInt(ReferenceEntry::level)).toList();
        if (level <= sorted.get(0).level()) {
            return pair(sorted.get(0));
        }
        if (level >= sorted.get(sorted.size() - 1).level()) {
            return pair(sorted.get(sorted.size() - 1));
        }
        for (int i = 0; i < sorted.size() - 1; i++) {
            ReferenceEntry a = sorted.get(i);
            ReferenceEntry b = sorted.get(i + 1);
            if (level >= a.level() && level <= b.level()) {
                double t = (double) (level - a.level()) / (b.level() - a.level());
                return new double[] {
                        a.hp() + (b.hp() - a.hp()) * t,
                        a.dps() + (b.dps() - a.dps()) * t,
                };
            }
        }
        return pair(sorted.get(sorted.size() - 1));
    }

    private static double[] pair(ReferenceEntry e) {
        return new double[] {e.hp(), e.dps()};
    }
}
