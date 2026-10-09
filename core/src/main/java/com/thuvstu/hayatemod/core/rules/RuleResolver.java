package com.thuvstu.hayatemod.core.rules;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import com.thuvstu.hayatemod.core.content.model.Models.*;

/** Pure difficulty resolution shared by runtime and tools (§5.15). */
public final class RuleResolver {
    public static final String GLOBAL = "solommo:global";
    private RuleResolver() { }

    public record Multipliers(double hp, double dps, double pity, double materials) {
        public Multipliers {
            for (double value : new double[] {hp, dps, pity, materials}) {
                if (!Double.isFinite(value) || value <= 0 || value > 100) {
                    throw new IllegalArgumentException("rule multiplier must be within (0, 100]");
                }
            }
        }
        public static Multipliers identity() { return new Multipliers(1, 1, 1, 1); }
        public Multipliers apply(RulePatch p) {
            return p == null ? this : new Multipliers(p.hp() == null ? hp : p.hp(),
                    p.dps() == null ? dps : p.dps(), p.pity() == null ? pity : p.pity(),
                    p.materials() == null ? materials : p.materials());
        }
    }

    public static Multipliers resolve(Map<String, Ruleset> rules, String id, EnemyData enemy) {
        Set<String> tags = new HashSet<>(enemy.tags());
        tags.add("rank:" + enemy.rank());
        tags.add("species:" + enemy.species());
        return resolve(rules, id, enemy.id(), tags);
    }

    public static Multipliers resolve(Map<String, Ruleset> rules, String id, String enemy, Set<String> tags) {
        Ruleset selected = rules.get(id);
        if (selected == null) throw new IllegalArgumentException("unknown ruleset '" + id + "'");
        Ruleset global = selected.extendsGlobal() && !id.equals(GLOBAL) ? rules.get(GLOBAL) : null;
        Multipliers value = Multipliers.identity();
        if (global != null) value = value.apply(global.defaults());
        value = value.apply(selected.defaults());
        if (global != null) value = applyTags(value, global, tags);
        value = applyTags(value, selected, tags);
        if (global != null) value = value.apply(global.byId().get(enemy));
        return value.apply(selected.byId().get(enemy));
    }

    private static Multipliers applyTags(Multipliers value, Ruleset rules, Set<String> tags) {
        // Declared list order is the explicit tie-breaker; tag set iteration never decides priority.
        for (TagRule rule : rules.tagOverrides()) {
            if (tags.contains(rule.tag())) value = value.apply(rule.patch());
        }
        return value;
    }

    /** Integer currency uses floor, never probabilistic rounding; positive guaranteed tokens stay >= 1. */
    public static int rewardCount(int base, double multiplier, boolean guaranteed) {
        if (base < 0 || !Double.isFinite(multiplier) || multiplier <= 0 || multiplier > 100) {
            throw new IllegalArgumentException("invalid reward scaling");
        }
        if (base == 0) return 0;
        long scaled = (long) Math.floor(base * multiplier);
        return (int) Math.min(4096, Math.max(guaranteed ? 1 : 0, scaled));
    }
}
