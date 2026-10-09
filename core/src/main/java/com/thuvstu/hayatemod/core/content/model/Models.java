package com.thuvstu.hayatemod.core.content.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Content data model. Instances are built by {@code Parsers}; see that class for schemas. */
public final class Models {
    private Models() {
    }

    public record WeaponCard(String id, String name, String family, String rarity, int itemLevel,
            List<String> tagsExtra, List<String> roleHint, Map<String, SkillDef> skills, String designNote) {
    }

    public record SkillDef(String core, Map<String, Object> mods, List<EffectDef> effects) {
    }

    public record EffectDef(String trigger, List<ConditionDef> conditions, List<ActionDef> actions,
            List<String> preventRecursive, int maxChainDepth, String scope, double radius,
            String id, String source, int priority) {
        public EffectDef {
            id = id == null ? "" : id;
            source = source == null ? "" : source;
        }
        public EffectDef(String trigger, List<ConditionDef> conditions, List<ActionDef> actions,
                List<String> preventRecursive, int maxChainDepth, String scope, double radius) {
            this(trigger, conditions, actions, preventRecursive, maxChainDepth, scope, radius, "", "", 0);
        }
        public EffectDef identified(String origin, String fallbackId) {
            return new EffectDef(trigger, conditions, actions, preventRecursive, maxChainDepth, scope, radius,
                    id.isEmpty() ? fallbackId : id, origin, priority);
        }
    }

    public record ConditionDef(String type, String status, double value, String name, double max) {
    }

    public record ActionDef(String type, int count, double damageMult, String target, double radius,
            List<String> inheritTags, double distance, double amount, String status, String effect,
            String ref, String formula, int durationTicks, double shieldRatio) {
        /** Compatibility for existing actions and generated cards that have no shield parameters. */
        public ActionDef(String type, int count, double damageMult, String target, double radius,
                List<String> inheritTags, double distance, double amount, String status, String effect,
                String ref, String formula) {
            this(type, count, damageMult, target, radius, inheritTags, distance, amount, status, effect,
                    ref, formula, 0, 0);
        }
    }

    public record EnemyData(String id, String name, int level, String rank, String species,
            String bossBody, List<String> tags, List<String> skills, String loot) {
    }

    public record AbilityDef(String id, String name, String core, Map<String, Object> mods, String target,
            String center, boolean splitDamage, String signal, boolean telegraph, boolean lethal,
            Double lethalRatio) {
    }

    public record EncounterData(String id, String name, int partySize, Map<String, Integer> requiredRoles,
            List<String> requiredCapabilities, String arenaTemplate, Map<String, Object> arenaMarkers,
            int targetMinutes, int softEnrageMinutes, List<String> checkpoints,
            List<PhaseDef> phases, Map<String, AbilityDef> abilities) {
    }

    public record PhaseDef(String id, int hpFrom, int hpTo, int loopPeriodSeconds,
            List<String> onEnter, List<TimelineEntry> timeline) {
    }

    public record TimelineEntry(int atSeconds, String ability) {
    }

    public record DirectDrop(String item, double p) {
    }

    public record RuneDrop(String id, double p) {
    }

    public record MaterialDrop(String item, int min, int max) {
    }

    public record LootTable(String id, List<DirectDrop> direct, String pityCurrency, int perKill,
            Integer exchangeCost, String exchangeItem, List<MaterialDrop> materials,
            List<RuneDrop> runes) {
    }

    public record JobDef(String id, String name, List<String> roles, List<String> weapons,
            String description) {
    }

    public record NpcData(String id, String name, String role, List<String> capabilities, double proficiency) {
    }

    public record ArenaData(String id, String name, String structure, Map<String, Object> markers) {
    }

    public record Vocabulary(Set<String> cores, Set<String> triggers, Set<String> conditions,
            Set<String> actions, Set<String> statuses, Set<String> elements, Set<String> signals,
            Set<String> tagNamespaces, int maxChainDepth, int maxProjectiles, double maxRadius,
            ExecutionLimits executionLimits, CombatLimits combatLimits, CastingLimits castingLimits) {
    }

    public record CastingLimits(double maxCastSeconds, int maxBoostDurationTicks) {
        public static CastingLimits defaults() { return new CastingLimits(30, 1200); }
    }

    /** Pack-configurable runtime budgets; defaults retain compatibility with older packs. */
    public record ExecutionLimits(int effectsPerTick, int actionsPerTick, int tasksPerTick, int pendingTasks) {
        public static ExecutionLimits defaults() {
            return new ExecutionLimits(256, 512, 128, 1024);
        }
    }

    /** Recovery/shield amounts are RPG HP units, not percentages of max health. */
    public record CombatLimits(double maxShieldAmount, int maxShieldDurationTicks,
            double maxCooldownReductionSeconds) {
        public static CombatLimits defaults() {
            return new CombatLimits(200, 1200, 30);
        }
    }

    public record ReferenceEntry(int level, double hp, double dps, double mit, double hps) {
    }

    public record DamageTuning(double critBaseChance, double critChanceCap, double critBaseMult,
            double critMultCap, double resistanceMax, double armorMax, double guardMax,
            java.util.Map<String, Double> coreBase, java.util.Map<String, RankTarget> rankTargets,
            double heroicPlayerMult, int heroicReferenceLevel, double wildDamageMult, double wildHpMult) {
    }

    public record RankTarget(int fightSeconds, double damageTakenRatio) {
    }

    /** One placement op: fill a box or set a single block (relative coords). */
    public record StructOp(String op, List<Integer> from, List<Integer> to, String block) {
    }

    public record StructTemplate(String id, String name, List<StructOp> blocks) {
    }

    public record PlacedBox(int x, int y, int z, int halfSize) {
    }

    public record WorldLayout(PlacedBox arena, PlacedBox town, double townGateRadius) {
    }

    public record RuneDef(String id, String name, String flavor, java.util.List<EffectDef> effects) {
    }

    public record MaterialDef(String id, String name, String source) {
    }

    public record KeystoneDef(String id, String name, String desc, int chainBonus, double damageMult,
            double cooldownMult, double meleeMult) {
    }

    public record BuildMods(int chainBonus, double damageMult, double cooldownMult, double meleeMult) {
        public static BuildMods neutral() {
            return new BuildMods(0, 1.0, 1.0, 1.0);
        }

        public static BuildMods combine(java.util.List<KeystoneDef> keys) {
            int chain = 0;
            double dmg = 1.0;
            double cd = 1.0;
            double melee = 1.0;
            for (KeystoneDef k : keys) {
                chain += k.chainBonus();
                dmg *= k.damageMult();
                cd *= k.cooldownMult();
                melee *= k.meleeMult();
            }
            return new BuildMods(chain, dmg, cd, melee);
        }

        /** Folds skill-point bonuses over a keystone base (/sp Training). */
        public static BuildMods withPoints(BuildMods base, int dmgPts, int cdPts, int meleePts) {
            double dmg = base.damageMult() * (1.0 + 0.02 * Math.max(0, Math.min(25, dmgPts)));
            double cd = base.cooldownMult()
                    * Math.max(0.5, 1.0 - 0.01 * Math.max(0, Math.min(20, cdPts)));
            double melee = base.meleeMult() * (1.0 + 0.02 * Math.max(0, Math.min(25, meleePts)));
            return new BuildMods(base.chainBonus(), dmg, cd, melee);
        }
    }

    /** Null fields inherit the previous layer; overrides replace, never multiply twice. */
    public record RulePatch(Double hp, Double dps, Double pity, Double materials) {
        public static RulePatch empty() { return new RulePatch(null, null, null, null); }
    }

    public record TagRule(String tag, RulePatch patch) { }

    public record Ruleset(String id, boolean extendsGlobal, RulePatch defaults,
            List<TagRule> tagOverrides, Map<String, RulePatch> byId) {
        public Ruleset {
            tagOverrides = List.copyOf(tagOverrides);
            byId = Map.copyOf(byId);
        }
        public Ruleset(String id, boolean extendsGlobal) {
            this(id, extendsGlobal, RulePatch.empty(), List.of(), Map.of());
        }
    }
}
