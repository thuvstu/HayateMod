package com.thuvstu.hayatemod.core.validate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.thuvstu.hayatemod.core.content.ContentSet;
import com.thuvstu.hayatemod.core.content.model.Models.AbilityDef;
import com.thuvstu.hayatemod.core.content.model.Models.ActionDef;
import com.thuvstu.hayatemod.core.content.model.Models.DirectDrop;
import com.thuvstu.hayatemod.core.content.model.Models.EffectDef;
import com.thuvstu.hayatemod.core.content.model.Models.EncounterData;
import com.thuvstu.hayatemod.core.content.model.Models.EnemyData;
import com.thuvstu.hayatemod.core.content.model.Models.LootTable;
import com.thuvstu.hayatemod.core.content.model.Models.NpcData;
import com.thuvstu.hayatemod.core.content.model.Models.PhaseDef;
import com.thuvstu.hayatemod.core.content.model.Models.SkillDef;
import com.thuvstu.hayatemod.core.content.model.Models.TimelineEntry;
import com.thuvstu.hayatemod.core.content.model.Models.Vocabulary;
import com.thuvstu.hayatemod.core.content.model.Models.WeaponCard;
import com.thuvstu.hayatemod.core.rules.DropSolver;
import com.thuvstu.hayatemod.core.validate.Issue.Severity;

/**
 * Content Pack validator (IMPLEMENTATION.md §9).
 *
 * <p>Rules implemented: V01 required/unknown keys are enforced by the loader;
 * this class checks V02 vocabulary, V03 ranges, V04 dangling references, V05
 * cycle report, V06 runtime guards, V07 party size, V08 capability coverage,
 * V09 lethal requirements, V12 drop sums, V13 pity consistency.
 * V10 (description templates) is enforced by the describer throwing on gaps.
 * V11 reports excessive individual ruleset overrides.
 */
public final class Validator {
    /** Actions that can re-emit combat events, mapped to the triggers they can fire. */
    private static final Map<String, Set<String>> ACTION_EMITS =
            Map.of("spawn_projectiles", Set.of("on_hit"));

    private Validator() {
    }

    public static List<Issue> validate(ContentSet set) {
        List<Issue> issues = new ArrayList<>();
        Vocabulary v = set.vocabulary();
        var casting = v.castingLimits();
        if (!Double.isFinite(casting.maxCastSeconds()) || casting.maxCastSeconds() <= 0 || casting.maxCastSeconds() > 60
                || casting.maxBoostDurationTicks() < 1 || casting.maxBoostDurationTicks() > 72000) {
            issues.add(err("V03", "vocabulary:limits", "invalid casting limits"));
        }
        var combat = v.combatLimits();
        if (!Double.isFinite(combat.maxShieldAmount()) || combat.maxShieldAmount() <= 0
                || combat.maxShieldAmount() > 1000000 || combat.maxShieldDurationTicks() < 1
                || combat.maxShieldDurationTicks() > 72000
                || !Double.isFinite(combat.maxCooldownReductionSeconds())
                || combat.maxCooldownReductionSeconds() <= 0 || combat.maxCooldownReductionSeconds() > 3600) {
            issues.add(err("V03", "vocabulary:limits", "invalid shield/cooldown limits"));
        }
        var limits = v.executionLimits();
        Map<String, Integer> budgets = Map.of("max_effects_per_tick", limits.effectsPerTick(),
                "max_actions_per_tick", limits.actionsPerTick(), "max_tasks_per_tick", limits.tasksPerTick(),
                "max_pending_tasks", limits.pendingTasks());
        for (var entry : budgets.entrySet()) {
            if (entry.getValue() <= 0 || entry.getValue() > 65536) {
                issues.add(err("V03", "vocabulary:limits." + entry.getKey(), "budget must be within [1, 65536]"));
            }
        }
        if (v.maxChainDepth() < 0 || v.maxChainDepth() > 3) {
            issues.add(err("V03", "vocabulary:limits.max_chain_depth", "chain depth must be within [0, 3]"));
        }
        if (v.maxProjectiles() < 1 || v.maxProjectiles() > 256) {
            issues.add(err("V03", "vocabulary:limits.max_projectiles", "projectiles must be within [1, 256]"));
        }
        for (var rules : set.rulesets().values()) {
            String loc = "rulesets:" + rules.id();
            checkRulePatch(rules.defaults(), loc, issues);
            Set<String> tags = new HashSet<>();
            for (var enemy : set.enemies().values()) {
                tags.addAll(enemy.tags());
                tags.add("rank:" + enemy.rank());
                tags.add("species:" + enemy.species());
            }
            Set<String> seen = new HashSet<>();
            for (var tag : rules.tagOverrides()) {
                checkRulePatch(tag.patch(), loc + ".tag_overrides." + tag.tag(), issues);
                if (!tags.contains(tag.tag())) issues.add(err("V04", loc, "unmatched enemy tag '" + tag.tag() + "'"));
                if (!seen.add(tag.tag())) issues.add(err("V01", loc, "duplicate tag override '" + tag.tag() + "'"));
            }
            for (var entry : rules.byId().entrySet()) {
                checkRulePatch(entry.getValue(), loc + ".by_id." + entry.getKey(), issues);
                if (!set.enemies().containsKey(entry.getKey())) issues.add(err("V04", loc, "unknown enemy '" + entry.getKey() + "'"));
            }
            if (rules.byId().size() > 3) {
                issues.add(new Issue(Severity.WARNING, "V11", loc, "more than 3 by_id overrides; prefer shared enemy tags"));
            }
        }
        for (WeaponCard w : set.weapons().values()) {
            checkWeapon(set, w, issues);
        }
        for (EnemyData e : set.enemies().values()) {
            checkEnemy(set, e, issues);
        }
        for (EncounterData e : set.encounters().values()) {
            checkEncounter(set, e, issues);
        }
        for (LootTable t : set.loot().values()) {
            checkLoot(set, t, issues);
        }
        for (var r : set.runes().values()) {
            checkEffectIdentities(r.effects(), "runes:" + r.id(), issues);
            for (int i = 0; i < r.effects().size(); i++) {
                checkEffect(set, r.id(), r.effects().get(i), "runes:" + r.id() + ".effects[" + i + "]",
                        issues);
            }
        }
        for (var k : set.keystones().values()) {
            String loc = "keystones:" + k.id();
            if (k.chainBonus() < 0 || k.chainBonus() > 2) {
                issues.add(err("V03", loc, "chain_bonus must be within [0, 2]"));
            }
            if (k.damageMult() <= 0.0 || k.cooldownMult() <= 0.0 || k.meleeMult() <= 0.0) {
                issues.add(err("V03", loc, "multipliers must be positive"));
            }
        }
        checkMarket(set, issues);
        checkMining(set, issues);
        checkStructures(set, issues);
        if (set.fishing() != null) {
            int lastXp = -1;
            for (var row : set.fishing().levels()) {
                if (row.xpRequired() < 0 || row.xpRequired() < lastXp) {
                    issues.add(err("V03", "life_skills:fishing",
                            "level xp_required must be ascending non-negative"));
                }
                lastXp = row.xpRequired();
            }
        }
        for (AbilityDef a : set.skills().values()) {
            checkAbilityCore(set, a, "skills:" + a.id(), issues);
        }
        for (NpcData n : set.npcs().values()) {
            if (n.proficiency() < 0.0 || n.proficiency() > 1.0) {
                issues.add(err("V03", "npcs:" + n.id(), "proficiency must be in [0,1]"));
            }
        }
        checkEffectCycles(set, issues);
        return issues;
    }

    // ---- weapons ----

    private static void checkRulePatch(com.thuvstu.hayatemod.core.content.model.Models.RulePatch patch,
            String loc, List<Issue> issues) {
        for (Double value : new Double[] {patch.hp(), patch.dps(), patch.pity(), patch.materials()}) {
            if (value != null && (!Double.isFinite(value) || value <= 0 || value > 100)) {
                issues.add(err("V03", loc, "rule multiplier must be within (0, 100]"));
            }
        }
    }

    private static void checkWeapon(ContentSet set, WeaponCard w, List<Issue> issues) {
        String loc = "weapons:" + w.id();
        Vocabulary v = set.vocabulary();
        if (w.itemLevel() <= 0) {
            issues.add(err("V03", loc, "item_level must be positive"));
        }
        for (String t : w.tagsExtra()) {
            checkTag(set, t, loc, issues);
        }
        checkEffectIdentities(w.skills().values().stream().flatMap(s -> s.effects().stream()).toList(), loc, issues);
        for (Map.Entry<String, SkillDef> e : w.skills().entrySet()) {
            checkSkillCore(set, e.getValue().core(), loc + ".skills." + e.getKey(), issues);
            if (e.getValue().core().equals("self_buff") && !Set.of("special", "heavy").contains(e.getKey())) {
                issues.add(err("V06", loc, "self_buff requires an active special/heavy slot"));
            }
            Object castTime = e.getValue().mods().get("cast_time");
            if (castTime instanceof Number n && (!Double.isFinite(n.doubleValue()) || n.doubleValue() < 0
                    || n.doubleValue() > v.castingLimits().maxCastSeconds()
                    || (n.doubleValue() > 0 && !Set.of("special", "heavy").contains(e.getKey())))) {
                issues.add(err("V03", loc, "cast_time must be bounded, nonnegative and on an active slot"));
            }
            checkTimerInterval(e.getValue(), loc + ".skills." + e.getKey(), issues);
            checkResourceCost(e.getValue(), loc + ".skills." + e.getKey(), issues);
            Object element = e.getValue().mods().get("element");
            if (element instanceof String s && !v.elements().contains(s)) {
                issues.add(err("V02", loc, "undefined element '" + s + "'"));
            }
            for (int i = 0; i < e.getValue().effects().size(); i++) {
                checkEffect(set, w.id(), e.getValue().effects().get(i),
                        loc + ".skills." + e.getKey() + ".effects[" + i + "]", issues);
                var effect = e.getValue().effects().get(i);
                if (effect.trigger().equals("on_cast") && !Set.of("special", "heavy").contains(e.getKey())) {
                    issues.add(err("V06", loc, "on_cast requires an active special/heavy slot"));
                }
                for (ActionDef action : effect.actions()) {
                    if (Set.of("reduce_cooldown", "reduce_next_cast").contains(action.type()) && !w.skills().containsKey(action.ref())) {
                        issues.add(err("V04", loc, "dangling timed-skill slot '" + action.ref() + "'"));
                    }
                }
            }
        }
    }

    private static void checkSkillCore(ContentSet set, String core, String loc, List<Issue> issues) {
        if (!set.vocabulary().cores().contains(core)) {
            issues.add(err("V02", loc, "undefined skill core '" + core + "'"));
        }
        if (set.tuning() != null && !set.tuning().coreBase().containsKey(core)) {
            issues.add(err("V02", loc, "no core_base for '" + core + "'"));
        }
    }
    private static void checkTimerInterval(SkillDef s, String loc, List<Issue> issues) {
        boolean timer = s.effects().stream().anyMatch(e -> e.trigger().equals("on_timer"));
        if (!timer) {
            return;
        }
        Object iv = s.mods().get("interval");
        if (!(iv instanceof Number n) || n.doubleValue() <= 0.0) {
            issues.add(err("V06", loc, "on_timer requires mods.interval > 0"));
        }
    }

    private static void checkResourceCost(SkillDef s, String loc, List<Issue> issues) {
        Object resource = s.mods().get("resource");
        Object cost = s.mods().get("resource_cost");
        if (resource == null && cost == null) {
            return;
        }
        if (!(resource instanceof String name)
                || (!name.equals("stamina") && !name.equals("mana"))) {
            issues.add(err("V02", loc, "unknown resource '" + resource + "'"));
        }
        if (!(cost instanceof Number n) || n.doubleValue() <= 0.0) {
            issues.add(err("V03", loc, "resource_cost must be positive"));
        }
    }
    private static void checkEffectIdentities(List<EffectDef> effects, String loc, List<Issue> issues) {
        Set<String> seen = new HashSet<>();
        for (EffectDef effect : effects) {
            if (!effect.id().isEmpty() && !seen.add(effect.source() + "#" + effect.id())) {
                issues.add(err("V01", loc, "duplicate effect id '" + effect.id() + "' in source '" + effect.source() + "'"));
            }
        }
    }

    static void checkEffect(ContentSet set, String ownerId, EffectDef e, String loc, List<Issue> issues) {
        Vocabulary v = set.vocabulary();
        if (e.priority() < -1000 || e.priority() > 1000) {
            issues.add(err("V03", loc, "effect priority must be within [-1000,1000]"));
        }
        if (!e.id().isEmpty() && (e.id().length() > 128 || !e.id().matches("[A-Za-z0-9_./:-]+"))) {
            issues.add(err("V01", loc, "effect id must be a stable token of at most 128 characters"));
        }
        if (!v.triggers().contains(e.trigger())) {
            issues.add(err("V02", loc, "undefined trigger '" + e.trigger() + "'"));
        }
        if (!e.scope().isEmpty() && !e.scope().equals("single") && !e.scope().equals("area")) {
            issues.add(err("V02", loc, "unknown effect scope '" + e.scope() + "'"));
        }
        if (e.scope().equals("area") && e.radius() <= 0.0) {
            issues.add(err("V06", loc, "area scope requires radius > 0"));
        }
        for (var c : e.conditions()) {
            if (!v.conditions().contains(c.type())) {
                issues.add(err("V02", loc, "undefined condition '" + c.type() + "'"));
            }
            if (c.type().equals("target_has_status")
                    && (c.status() == null || !v.statuses().contains(c.status()))) {
                issues.add(err("V02", loc, "undefined status '" + c.status() + "'"));
            }
            if (c.type().equals("chance") && (c.value() < 0.0 || c.value() > 1.0)) {
                issues.add(err("V03", loc, "chance value must be within [0, 1]"));
            }
            if (c.type().equals("health_below") && (c.value() <= 0.0 || c.value() > 1.0)) {
                issues.add(err("V03", loc, "health_below value must be within (0, 1]"));
            }
            if ((c.type().equals("holding") || c.type().equals("entity_type"))
                    && (c.name() == null || c.name().isEmpty())) {
                issues.add(err("V06", loc, "condition '" + c.type() + "' requires name"));
            }
            if (c.type().equals("has_effect")
                    && (c.name() == null || !Sets.EFFECTS.contains(c.name()))) {
                issues.add(err("V02", loc, "unknown effect '" + c.name() + "'"));
            }
            if (c.type().equals("variable") && (c.name() == null || c.name().isEmpty())) {
                issues.add(err("V06", loc, "variable condition requires name"));
            }
            if (c.type().equals("variable") && c.max() > 0 && c.max() < c.value()) {
                issues.add(err("V03", loc, "variable max must be >= value"));
            }
        }
        for (var a : e.actions()) {
            checkAction(set, a, loc, issues);
            if (e.scope().equals("area") && Set.of("grant_shield", "heal_with_shield", "reduce_cooldown", "reduce_next_cast").contains(a.type())) {
                issues.add(err("V06", loc, "owner-only recovery/cooldown actions do not support area scope"));
            }
        }
        if (e.maxChainDepth() < 0) {
            issues.add(err("V06", loc, "effect requires flags.max_chain_depth"));
        } else if (e.maxChainDepth() > v.maxChainDepth()) {
            issues.add(err("V03", loc,
                    "max_chain_depth " + e.maxChainDepth() + " exceeds vocabulary limit " + v.maxChainDepth()));
        }
        for (var a : e.actions()) {
            Set<String> emitted = ACTION_EMITS.getOrDefault(a.type(), Set.of());
            if (!emitted.isEmpty() && e.preventRecursive().isEmpty()) {
                issues.add(err("V06", loc,
                        "action '" + a.type() + "' can re-emit " + emitted + " but flags.prevent_recursive is empty"));
            }
        }
    }

    private static void checkAction(ContentSet set, ActionDef a, String loc, List<Issue> issues) {
        Vocabulary v = set.vocabulary();
        if (!v.actions().contains(a.type())) {
            issues.add(err("V02", loc, "undefined action '" + a.type() + "'"));
        }
        if (a.damageMult() < 0.0) {
            issues.add(err("V03", loc, "damage_mult must be >= 0"));
        }
        if (a.count() < 0) {
            issues.add(err("V03", loc, "count must be >= 0"));
        }
        if (a.count() > v.maxProjectiles()) {
            issues.add(err("V03", loc,
                    "count " + a.count() + " exceeds vocabulary limit " + v.maxProjectiles()));
        }
        if (a.radius() < 0.0 || a.radius() > v.maxRadius()) {
            issues.add(err("V03", loc, "radius must be within [0, " + v.maxRadius() + "]"));
        }
        if (!a.target().isEmpty() && !a.target().equals("attacker")
                && !a.target().equals("look")
                && !a.target().equals("nearest_enemy_in_radius")
                && !a.target().equals("party")) {
            issues.add(err("V02", loc, "unknown action target '" + a.target() + "'"));
        }
        if (Set.of("grant_shield", "heal_with_shield", "reduce_cooldown", "reduce_next_cast").contains(a.type())) {
            var combat = v.combatLimits();
            double limit = a.type().equals("reduce_next_cast") ? v.castingLimits().maxCastSeconds()
                    : a.type().equals("reduce_cooldown") ? combat.maxCooldownReductionSeconds()
                    : combat.maxShieldAmount();
            if (!Double.isFinite(a.amount()) || a.amount() <= 0 || a.amount() > limit) {
                issues.add(err("V03", loc, a.type() + " amount must be within (0, " + limit + "]"));
            }
            if (!a.formula().isEmpty() || !a.target().isEmpty()) {
                issues.add(err("V06", loc, a.type() + " uses a fixed amount and the owner; formula/target are unsupported"));
            }
            if (Set.of("reduce_cooldown", "reduce_next_cast").contains(a.type())) {
                if (a.type().equals("reduce_next_cast") && (a.durationTicks() < 1
                        || a.durationTicks() > v.castingLimits().maxBoostDurationTicks())) {
                    issues.add(err("V03", loc, "next-cast boost duration_ticks outside limits"));
                }
                if (!Set.of("special", "heavy").contains(a.ref())) {
                    issues.add(err("V06", loc, "cooldown/cast reduction ref must be special or heavy"));
                }
            } else {
                if (a.durationTicks() < 1 || a.durationTicks() > combat.maxShieldDurationTicks()) {
                    issues.add(err("V03", loc, "shield duration_ticks is outside vocabulary limits"));
                }
                if (a.type().equals("heal_with_shield") && (!Double.isFinite(a.shieldRatio())
                        || a.shieldRatio() < 0 || a.shieldRatio() > 1)) {
                    issues.add(err("V03", loc, "shield_ratio must be within [0, 1]"));
                }
            }
        }
        if (a.type().equals("apply_status")
                && (a.status() == null || !v.statuses().contains(a.status()))) {
            issues.add(err("V02", loc, "undefined status '" + a.status() + "'"));
        }
        if (a.type().equals("potion")
                && (a.effect() == null || !Sets.EFFECTS.contains(a.effect()))) {
            issues.add(err("V02", loc, "unknown potion effect '" + a.effect() + "'"));
        }
        if (a.type().equals("particles")
                && (a.effect() == null || !Sets.PARTICLES.contains(a.effect()))) {
            issues.add(err("V02", loc, "unknown particle '" + a.effect() + "'"));
        }
        if (a.type().equals("sound")
                && (a.effect() == null || !Sets.SOUNDS.contains(a.effect()))) {
            issues.add(err("V02", loc, "unknown sound '" + a.effect() + "'"));
        }
        if (a.type().equals("summon")
                && (a.ref() == null || !set.enemies().containsKey(a.ref()))) {
            issues.add(err("V04", loc, "dangling summon enemy '" + a.ref() + "'"));
        }
        if ((a.type().equals("cast") || a.type().equals("setvar") || a.type().equals("addvar")
                || a.type().equals("message"))
                && (a.ref() == null || a.ref().isEmpty())) {
            issues.add(err("V06", loc, "action '" + a.type() + "' requires ref"));
        }
        if ((a.type().equals("dropitem") || a.type().equals("giveitem"))
                && (a.ref() == null || !Sets.GIFTS.contains(a.ref()))) {
            issues.add(err("V02", loc, "untradable gift '" + a.ref() + "'"));
        }
        if (a.type().equals("explosion") && a.damageMult() > 4.0) {
            issues.add(err("V03", loc, "explosion power above 4.0 is grief-adjacent"));
        }
        if (!a.formula().isEmpty()) {
            try {
                var names =
                        com.thuvstu.hayatemod.core.engine.Formula.identifiers(a.formula());
                for (String name : names) {
                    if (!com.thuvstu.hayatemod.core.engine.Formula.BINDINGS.contains(name)
                            && !com.thuvstu.hayatemod.core.engine.Formula.isVar(name)) {
                        issues.add(err("V02", loc, "unknown formula name '" + name + "'"));
                    }
                }
            } catch (com.thuvstu.hayatemod.core.engine.Formula.FormulaException e) {
                issues.add(err("V06", loc, "bad formula '" + a.formula() + "': " + e.getMessage()));
            }
        }
        for (String t : a.inheritTags()) {
            checkTag(set, t, loc, issues);
        }
    }

    private static void checkTag(ContentSet set, String tag, String loc, List<Issue> issues) {
        int colon = tag.indexOf(':');
        if (colon < 0) {
            issues.add(err("V02", loc, "tag '" + tag + "' must use a namespace (ns:value)"));
            return;
        }
        String ns = tag.substring(0, colon);
        String value = tag.substring(colon + 1);
        Vocabulary v = set.vocabulary();
        if (!v.tagNamespaces().contains(ns)) {
            issues.add(err("V02", loc, "undefined tag namespace '" + ns + "'"));
            return;
        }
        if (ns.equals("element") && !v.elements().contains(value)) {
            issues.add(err("V02", loc, "undefined element tag '" + tag + "'"));
        }
        if (ns.equals("status") && !v.statuses().contains(value)) {
            issues.add(err("V02", loc, "undefined status tag '" + tag + "'"));
        }
    }

    // ---- enemies / encounters ----

    private static void checkEnemy(ContentSet set, EnemyData e, List<Issue> issues) {
        String loc = "enemies:" + e.id();
        if (e.level() <= 0) {
            issues.add(err("V03", loc, "level must be positive"));
        }
        for (String t : e.tags()) {
            checkTag(set, t, loc, issues);
        }
        for (String s : e.skills()) {
            if (!set.skills().containsKey(s)) {
                issues.add(err("V04", loc, "dangling skill reference '" + s + "'"));
            }
        }
        if (!set.loot().containsKey(e.loot())) {
            issues.add(err("V04", loc, "dangling loot reference '" + e.loot() + "'"));
        }
    }

    private static void checkEncounter(ContentSet set, EncounterData e, List<Issue> issues) {
        String loc = "encounters:" + e.id();
        int roleSum = e.requiredRoles().values().stream().mapToInt(Integer::intValue).sum();
        if (roleSum != e.partySize()) {
            issues.add(err("V07", loc,
                    "party size " + e.partySize() + " != required_roles sum " + roleSum));
        }
        // V08: capability + role coverage (player flexes tank/dps per §5.1).
        Set<String> rosterCaps = new HashSet<>();
        Map<String, Integer> rosterRoles = new HashMap<>();
        for (NpcData n : set.npcs().values()) {
            rosterCaps.addAll(n.capabilities());
            rosterRoles.merge(n.role(), 1, Integer::sum);
        }
        for (String cap : e.requiredCapabilities()) {
            if (!rosterCaps.contains(cap)) {
                issues.add(err("V08", loc, "no NPC provides required capability '" + cap + "'"));
            }
        }
        for (Map.Entry<String, Integer> r : e.requiredRoles().entrySet()) {
            int have = rosterRoles.getOrDefault(r.getKey(), 0)
                    + ((r.getKey().equals("tank") || r.getKey().equals("dps")) ? 1 : 0);
            if (have < r.getValue()) {
                issues.add(err("V08", loc,
                        "role '" + r.getKey() + "' needs " + r.getValue() + " but roster+player cover " + have));
            }
        }
        if (!set.arenas().containsKey(e.arenaTemplate())) {
            issues.add(err("V04", loc, "dangling arena template '" + e.arenaTemplate() + "'"));
        } else {
            String structure = set.arenas().get(e.arenaTemplate()).structure();
            if (structure != null && !structure.isEmpty()
                    && (set.structures() == null || !set.structures().containsKey(structure))) {
                issues.add(err("V04", loc, "dangling arena structure '" + structure + "'"));
            }
        }
        if (e.targetMinutes() >= e.softEnrageMinutes()) {
            issues.add(err("V03", loc, "target_minutes must be < soft_enrage_minutes"));
        }
        Set<String> knownAbilities = new HashSet<>(e.abilities().keySet());
        knownAbilities.addAll(set.skills().keySet());
        for (PhaseDef p : e.phases()) {
            String ploc = loc + ".phases." + p.id();
            if (p.hpFrom() < 0 || p.hpTo() < 0 || p.hpFrom() > 100 || p.hpTo() > 100 || p.hpFrom() < p.hpTo()) {
                issues.add(err("V03", ploc, "hp_range must be [high, low] within [0, 100]"));
            }
            for (String a : p.onEnter()) {
                if (!knownAbilities.contains(a)) {
                    issues.add(err("V04", ploc, "dangling on_enter ability '" + a + "'"));
                }
            }
            for (TimelineEntry t : p.timeline()) {
                if (t.atSeconds() < 0) {
                    issues.add(err("V03", ploc, "at_seconds must be >= 0"));
                }
                if (!knownAbilities.contains(t.ability())) {
                    issues.add(err("V04", ploc, "dangling timeline ability '" + t.ability() + "'"));
                }
            }
        }
        for (AbilityDef a : e.abilities().values()) {
            checkEncounterAbility(set, e, a, loc + ".abilities." + a.id(), issues);
        }
    }

    private static void checkAbilityCore(ContentSet set, AbilityDef a, String loc, List<Issue> issues) {
        checkSkillCore(set, a.core(), loc, issues);
        if (a.core().equals("self_buff")) {
            issues.add(err("V06", loc, "self_buff is a player weapon core, not an encounter ability"));
        }
        if (a.lethal()) {
            if (!a.telegraph()) {
                issues.add(err("V09", loc, "lethal ability requires telegraph: true"));
            }
            if (a.signal() == null || a.signal().isEmpty()
                    || !set.vocabulary().signals().contains(a.signal())) {
                issues.add(err("V09", loc, "lethal ability requires a vocabulary signal"));
            }
            if (a.lethalRatio() == null || a.lethalRatio() <= 0.0) {
                issues.add(err("V09", loc, "lethal ability requires lethal_ratio > 0"));
            }
        }
    }

    private static void checkEncounterAbility(ContentSet set, EncounterData enc, AbilityDef a, String loc,
            List<Issue> issues) {
        checkAbilityCore(set, a, loc, issues);
        if (a.center() != null && a.center().startsWith("marker:")) {
            String marker = a.center().substring("marker:".length());
            Set<String> markers = new HashSet<>(enc.arenaMarkers().keySet());
            var arena = set.arenas().get(enc.arenaTemplate());
            if (arena != null) {
                markers.addAll(arena.markers().keySet());
            }
            if (!markers.contains(marker)) {
                issues.add(err("V04", loc, "dangling marker '" + marker + "'"));
            }
        }
    }

    // ---- loot ----

    private static void checkLoot(ContentSet set, LootTable t, List<Issue> issues) {
        if (t.perKill() < 0 || t.perKill() > 4096) {
            issues.add(err("V03", "loot:" + t.id(), "pity per_kill must be within [0,4096]"));
        }
        for (var material : t.materials()) {
            if (material.min() < 0 || material.max() < material.min() || material.max() > 4096) {
                issues.add(err("V03", "loot:" + t.id(), "material range must be within [0,4096]"));
            }
        }
        String loc = "loot:" + t.id();
        double sum = t.direct().stream().mapToDouble(DirectDrop::p).sum();
        if (sum > 1.0 + 1e-9) {
            issues.add(err("V12", loc, "direct drop probabilities sum to " + sum + " (> 1)"));
        }
        for (DirectDrop d : t.direct()) {
            if (d.p() < 0.0 || d.p() > 1.0) {
                issues.add(err("V03", loc, "drop probability for '" + d.item() + "' must be in [0,1]"));
            }
        }
        if (!t.pityCurrency().isEmpty() && !t.pityCurrency().equals("pity_shard")) {
            issues.add(err("V02", loc, "unknown pity currency '" + t.pityCurrency() + "'"));
        }
        for (var mat : t.materials()) {
            if (!mat.item().equals("craft_material")
                    && (set.materials() == null || !set.materials().containsKey(mat.item()))) {
                issues.add(err("V02", loc, "unknown material '" + mat.item() + "'"));
            }
            if (mat.min() < 0 || mat.max() < mat.min()) {
                issues.add(err("V03", loc, "material count range must satisfy 0 <= min <= max"));
            }
        }
        for (var r : t.runes()) {
            if (r.p() < 0.0 || r.p() > 1.0) {
                issues.add(err("V03", loc, "rune probability for '" + r.id() + "' must be in [0,1]"));
            }
            if (!set.runes().containsKey(r.id())) {
                issues.add(err("V04", loc, "dangling rune reference '" + r.id() + "'"));
            }
        }
        if (t.exchangeCost() != null) {
            if (t.exchangeItem() == null || !set.weapons().containsKey(t.exchangeItem())) {
                issues.add(err("V04", loc, "dangling exchange item '" + t.exchangeItem() + "'"));
            }
            if (t.perKill() <= 0) {
                issues.add(err("V13", loc, "exchange defined but pity per_kill is 0 (unreachable guarantee)"));
            } else {
                int pityRuns = (int) Math.ceil((double) t.exchangeCost() / t.perKill());
                for (DirectDrop d : t.direct()) {
                    double expectation = DropSolver.expectedRuns(d.p(), pityRuns);
                    if (expectation > pityRuns + 1e-9) {
                        issues.add(err("V13", loc,
                                "expectation " + expectation + " exceeds pity " + pityRuns + " for '" + d.item()
                                        + "'"));
                    }
                }
            }
        }
    }

    // ---- effect cycle report (V05) ----

    private static void checkEffectCycles(ContentSet set, List<Issue> issues) {
        for (WeaponCard w : set.weapons().values()) {
            for (Map.Entry<String, SkillDef> e : w.skills()
                    .entrySet()) {
                Set<String> triggers = new HashSet<>();
                for (var eff : e.getValue().effects()) {
                    triggers.add(eff.trigger());
                }
                for (var eff : e.getValue().effects()) {
                    for (var a : eff.actions()) {
                        Set<String> emitted = ACTION_EMITS.getOrDefault(a.type(), Set.of());
                        for (String t : emitted) {
                            if (triggers.contains(t)) {
                                issues.add(new Issue(Severity.INFO, "V05",
                                        "weapons:" + w.id() + ".skills." + e.getKey(),
                                        "cycle candidate: '" + a.type() + "' may re-emit '" + t
                                                + "' (guarded by prevent_recursive=" + eff.preventRecursive()
                                                + ", max_chain_depth=" + eff.maxChainDepth() + ")"));
                            }
                        }
                    }
                }
            }
        }
    }

    private static Issue err(String code, String location, String message) {
        return new Issue(Severity.ERROR, code, location, message);
    }

    private static void checkStructures(ContentSet set, List<Issue> issues) {
        if (set.structures() == null) {
            return;
        }
        for (var t : set.structures().values()) {
            String loc = "structures:" + t.id();
            for (var op : t.blocks()) {
                if (!op.op().equals("fill") && !op.op().equals("set")) {
                    issues.add(err("V02", loc, "unknown struct op '" + op.op() + "'"));
                }
                if (!com.thuvstu.hayatemod.core.build.StructurePlanner.ALLOWED_BLOCKS
                        .contains(op.block())) {
                    issues.add(err("V02", loc,
                            "block '" + op.block() + "' is outside the structure palette"));
                }
            }
            long volume = 0;
            for (var op : t.blocks()) {
                volume += (long) (Math.abs(op.to().get(0) - op.from().get(0)) + 1)
                        * (Math.abs(op.to().get(1) - op.from().get(1)) + 1)
                        * (Math.abs(op.to().get(2) - op.from().get(2)) + 1);
            }
            if (volume > 200_000) {
                issues.add(err("V03", loc, "template volume " + volume + " exceeds 200000 (split it)"));
            }
        }
    }

    private static void checkMarket(ContentSet set, List<Issue> issues) {
        var book = set.market();
        if (book == null) {
            return;
        }
        if (book.spreadDefault() < 0.0 || book.spreadDefault() >= 1.0) {
            issues.add(err("V03", "economy:market", "spread_default must be within [0, 1)"));
        }
        for (var l : book.listings()) {
            String loc = "economy:market:" + l.item();
            if (l.item().equals("solommo:pity_shard")) {
                issues.add(err("V02", loc, "pity_shard must not be tradable (progression integrity)"));
            } else if (l.item().startsWith("solommo:") && !l.item().equals("solommo:craft_material")) {
                issues.add(err("V02", loc, "unknown tradable '" + l.item() + "'"));
            } else if (!l.item().matches("[a-z_]+:[a-z_/]+")) {
                issues.add(err("V02", loc, "malformed item id '" + l.item() + "'"));
            }
            if (l.basePrice() <= 0.0 || l.targetStock() <= 0.0 || l.elasticity() <= 0.0) {
                issues.add(err("V03", loc, "base_price/target_stock/elasticity must be positive"));
            }
        }
    }

    private static void checkMining(ContentSet set, List<Issue> issues) {
        var book = set.mining();
        if (book == null) {
            return;
        }
        int lastXp = -1;
        for (var row : book.levels()) {
            if (row.xpRequired() < 0 || row.xpRequired() < lastXp) {
                issues.add(err("V03", "life_skills:mining",
                        "level xp_required must be ascending non-negative"));
            }
            lastXp = row.xpRequired();
        }
        for (var e : book.oreXp().entrySet()) {
            if (e.getValue() < 0) {
                issues.add(err("V03", "life_skills:mining", "ore xp must be >= 0"));
            }
        }
    }
}
