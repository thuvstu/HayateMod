package com.thuvstu.hayatemod.core.content;

import static com.thuvstu.hayatemod.core.content.Maps.optBool;
import static com.thuvstu.hayatemod.core.content.Maps.optDouble;
import static com.thuvstu.hayatemod.core.content.Maps.optInt;
import static com.thuvstu.hayatemod.core.content.Maps.optList;
import static com.thuvstu.hayatemod.core.content.Maps.optMap;
import static com.thuvstu.hayatemod.core.content.Maps.optStr;
import static com.thuvstu.hayatemod.core.content.Maps.reqDouble;
import static com.thuvstu.hayatemod.core.content.Maps.reqInt;
import static com.thuvstu.hayatemod.core.content.Maps.reqList;
import static com.thuvstu.hayatemod.core.content.Maps.reqMap;
import static com.thuvstu.hayatemod.core.content.Maps.reqStr;
import static com.thuvstu.hayatemod.core.content.Maps.strList;
import static com.thuvstu.hayatemod.core.content.Maps.unknownKeys;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.thuvstu.hayatemod.core.content.model.Models.Ruleset;
import com.thuvstu.hayatemod.core.content.model.Models.ExecutionLimits;
import com.thuvstu.hayatemod.core.content.model.Models.AbilityDef;
import com.thuvstu.hayatemod.core.content.model.Models.ActionDef;
import com.thuvstu.hayatemod.core.content.model.Models.ConditionDef;
import com.thuvstu.hayatemod.core.content.model.Models.DamageTuning;
import com.thuvstu.hayatemod.core.content.model.Models.DirectDrop;
import com.thuvstu.hayatemod.core.content.model.Models.EffectDef;
import com.thuvstu.hayatemod.core.content.model.Models.EnemyData;
import com.thuvstu.hayatemod.core.content.model.Models.EncounterData;
import com.thuvstu.hayatemod.core.content.model.Models.JobDef;
import com.thuvstu.hayatemod.core.content.model.Models.LootTable;
import com.thuvstu.hayatemod.core.content.model.Models.MaterialDef;
import com.thuvstu.hayatemod.core.content.model.Models.MaterialDrop;
import com.thuvstu.hayatemod.core.content.model.Models.NpcData;
import com.thuvstu.hayatemod.core.content.model.Models.PhaseDef;
import com.thuvstu.hayatemod.core.content.model.Models.RankTarget;
import com.thuvstu.hayatemod.core.content.model.Models.RuneDef;
import com.thuvstu.hayatemod.core.content.model.Models.KeystoneDef;
import com.thuvstu.hayatemod.core.content.model.Models.StructOp;
import com.thuvstu.hayatemod.core.content.model.Models.StructTemplate;
import com.thuvstu.hayatemod.core.content.model.Models.PlacedBox;
import com.thuvstu.hayatemod.core.content.model.Models.WorldLayout;
import com.thuvstu.hayatemod.core.economy.MarketDefs.Listing;
import com.thuvstu.hayatemod.core.economy.MarketDefs.MarketBook;
import com.thuvstu.hayatemod.core.life.MiningLevels.LevelRow;
import com.thuvstu.hayatemod.core.life.MiningLevels.MiningBook;
import com.thuvstu.hayatemod.core.life.FishingLevels.FishingBook;
import com.thuvstu.hayatemod.core.content.model.Models.ReferenceEntry;
import com.thuvstu.hayatemod.core.content.model.Models.SkillDef;
import com.thuvstu.hayatemod.core.content.model.Models.TimelineEntry;
import com.thuvstu.hayatemod.core.content.model.Models.Vocabulary;
import com.thuvstu.hayatemod.core.content.model.Models.WeaponCard;

/** One parse function per file kind. Null return = errors already recorded. */
final class Parsers {
    private Parsers() {
    }

    static WeaponCard weapon(Map<String, Object> m, String file, List<ContentError> errors) {
        unknownKeys(m, Set.of("id", "name", "family", "rarity", "item_level", "tags_extra", "role_hint",
                "skills", "design_note"), file, "<root>", errors);
        String id = reqStr(m, "id", file, "<root>", errors);
        String name = reqStr(m, "name", file, "<root>", errors);
        String family = reqStr(m, "family", file, "<root>", errors);
        String rarity = reqStr(m, "rarity", file, "<root>", errors);
        Integer itemLevel = reqInt(m, "item_level", file, "<root>", errors);
        Map<String, Object> skills = reqMap(m, "skills", file, "<root>", errors);
        if (id == null || name == null || family == null || rarity == null || itemLevel == null || skills == null) {
            return null;
        }
        Map<String, SkillDef> parsed = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : skills.entrySet()) {
            if (!(e.getValue() instanceof Map<?, ?> sm)) {
                errors.add(new ContentError(file, "skills." + e.getKey(), "expected mapping"));
                continue;
            }
            @SuppressWarnings("unchecked")
            SkillDef s = skill((Map<String, Object>) sm, file, "skills." + e.getKey(), errors);
            if (s != null) {
                parsed.put(e.getKey(), new SkillDef(s.core(), s.mods(), s.effects().stream()
                        .map(effect -> effect.identified("weapon:" + id, effect.id())).toList()));
            }
        }
        return new WeaponCard(id, name, family, rarity, itemLevel,
                strList(optList(m, "tags_extra", file, "<root>", errors), file, "tags_extra", errors),
                strList(optList(m, "role_hint", file, "<root>", errors), file, "role_hint", errors),
                parsed, optStr(m, "design_note", "", file, "<root>", errors));
    }

    static SkillDef skill(Map<String, Object> m, String file, String loc, List<ContentError> errors) {
        unknownKeys(m, Set.of("core", "mods", "effects"), file, loc, errors);
        String core = reqStr(m, "core", file, loc, errors);
        if (core == null) {
            return null;
        }
        List<EffectDef> effects = new ArrayList<>();
        for (Object o : optList(m, "effects", file, loc, errors)) {
            if (!(o instanceof Map<?, ?> em)) {
                errors.add(new ContentError(file, loc + ".effects", "expected mapping"));
                continue;
            }
            @SuppressWarnings("unchecked")
            EffectDef e = effect((Map<String, Object>) em, file, loc + ".effects[" + effects.size() + "]", errors);
            if (e != null) {
                effects.add(e.identified("", loc + "/" + String.format(java.util.Locale.ROOT, "%08d", effects.size())));
            }
        }

        return new SkillDef(core, mods(m, file, loc, errors), effects);
    }

    static EffectDef effect(Map<String, Object> m, String file, String loc, List<ContentError> errors) {
        unknownKeys(m, Set.of("trigger", "conditions", "actions", "flags", "scope", "radius", "id", "priority"), file,
                loc, errors);
        if (m.get("id") instanceof String id && id.isBlank()) {
            errors.add(new ContentError(file, loc + ".id", "effect id must not be blank"));
        }
        String trigger = reqStr(m, "trigger", file, loc, errors);
        List<Object> actions = reqList(m, "actions", file, loc, errors);
        if (trigger == null || actions == null) {
            return null;
        }
        List<ConditionDef> conds = new ArrayList<>();
        for (Object o : optList(m, "conditions", file, loc, errors)) {
            if (!(o instanceof Map<?, ?> cm)) {
                errors.add(new ContentError(file, loc + ".conditions", "expected mapping"));
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> c = (Map<String, Object>) cm;
            unknownKeys(c, Set.of("type", "status", "value", "name", "max"), file,
                    loc + ".conditions", errors);
            String type = reqStr(c, "type", file, loc + ".conditions", errors);
            if (type != null) {
                conds.add(new ConditionDef(type, optStr(c, "status", null, file, loc + ".conditions", errors),
                        optDouble(c, "value", 0.0, file, loc + ".conditions", errors), optStr(c, "name", "", file, loc + ".conditions", errors),
                        optDouble(c, "max", 0.0, file, loc + ".conditions", errors)));
            }
        }
        List<ActionDef> acts = new ArrayList<>();
        for (int i = 0; i < actions.size(); i++) {
            Object o = actions.get(i);
            if (!(o instanceof Map<?, ?> am)) {
                errors.add(new ContentError(file, loc + ".actions[" + i + "]", "expected mapping"));
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> a = (Map<String, Object>) am;
            unknownKeys(a, Set.of("type", "count", "damage_mult", "target", "radius", "inherit_tags",
                    "distance", "amount", "status", "effect", "ref", "formula", "duration_ticks", "shield_ratio"),
                    file, loc + ".actions", errors);
            String type = reqStr(a, "type", file, loc + ".actions", errors);
            if (type == null) {
                continue;
            }
            int duration = 0;
            double shieldRatio = 0;
            boolean requiresDuration = type.equals("grant_shield") || type.equals("heal_with_shield") || type.equals("reduce_next_cast");
            String actionLoc = loc + ".actions[" + i + "]";
            if (requiresDuration) {
                Integer parsed = reqInt(a, "duration_ticks", file, actionLoc, errors);
                duration = parsed == null ? 0 : parsed;
            } else if (a.containsKey("duration_ticks")) {
                errors.add(new ContentError(file, actionLoc + ".duration_ticks", "only shield or next-cast actions support duration_ticks"));
            }
            if (type.equals("heal_with_shield")) {
                Double parsed = reqDouble(a, "shield_ratio", file, actionLoc, errors);
                shieldRatio = parsed == null ? 0 : parsed;
            } else if (a.containsKey("shield_ratio")) {
                errors.add(new ContentError(file, actionLoc + ".shield_ratio", "only heal_with_shield supports shield_ratio"));
            }
            if (requiresDuration || type.equals("reduce_cooldown")) {
                reqDouble(a, "amount", file, actionLoc, errors);
            }
            // Formula amounts fail fast at load (validator re-checks names as V06).
            String formula = optStr(a, "formula", "", file, loc + ".actions", errors);
            if (!formula.isEmpty()) {
                try {
                    com.thuvstu.hayatemod.core.engine.Formula.parse(formula);
                } catch (com.thuvstu.hayatemod.core.engine.Formula.FormulaException e) {
                    errors.add(new ContentError(file, loc + ".actions",
                            "bad formula '" + formula + "': " + e.getMessage()));
                    continue;
                }
            }
            double mult = optDouble(a, "damage_mult", 0.0, file, loc + ".actions", errors);
            acts.add(new ActionDef(type, optInt(a, "count", 0, file, loc + ".actions", errors), mult, optStr(a, "target", "", file, loc + ".actions", errors),
                    optDouble(a, "radius", 0.0, file, loc + ".actions", errors),
                    strList(optList(a, "inherit_tags", file, loc + ".actions", errors), file, loc + ".actions.inherit_tags", errors),
                    optDouble(a, "distance", 0.0, file, loc + ".actions", errors), optDouble(a, "amount", 0.0, file, loc + ".actions", errors),
                    optStr(a, "status", "", file, loc + ".actions", errors), optStr(a, "effect", "", file, loc + ".actions", errors), optStr(a, "ref", "", file, loc + ".actions", errors),
                    optStr(a, "formula", "", file, loc + ".actions", errors), duration, shieldRatio));
        }
        Map<String, Object> flags = optMap(m, "flags", file, loc, errors);
        unknownKeys(flags, Set.of("prevent_recursive", "max_chain_depth"), file, loc + ".flags", errors);
        return new EffectDef(trigger, conds, acts,
                strList(optList(flags, "prevent_recursive", file, loc + ".flags", errors), file, loc + ".flags.prevent_recursive", errors),
                optInt(flags, "max_chain_depth", -1, file, loc + ".flags", errors), optStr(m, "scope", "", file, loc, errors),
                optDouble(m, "radius", 0.0, file, loc, errors),
                m.containsKey("id") ? reqStr(m, "id", file, loc, errors) : "", "",
                optInt(m, "priority", 0, file, loc, errors));
    }

    /** Typed modifier vocabulary shared by weapon skills and encounter abilities. */
    private static Map<String, Object> mods(Map<String, Object> parent, String file, String loc,
            List<ContentError> errors) {
        Map<String, Object> mods = optMap(parent, "mods", file, loc, errors);
        Set<String> strings = Set.of("element", "enemy", "resource");
        Set<String> numbers = Set.of("range", "width", "cooldown", "distance", "interval",
                "cast_time", "radius", "resource_cost");
        for (String key : mods.keySet()) {
            if (strings.contains(key)) {
                reqStr(mods, key, file, loc + ".mods", errors);
            } else if (numbers.contains(key)) {
                reqDouble(mods, key, file, loc + ".mods", errors);
            } else if (key.equals("count")) {
                reqInt(mods, key, file, loc + ".mods", errors);
            } else {
                errors.add(new ContentError(file, loc + ".mods." + key, "unknown modifier '" + key + "'"));
            }
        }
        return mods;
    }

    static EnemyData enemy(Map<String, Object> m, String file, List<ContentError> errors) {
        unknownKeys(m, Set.of("id", "name", "level", "rank", "species", "boss_body", "tags", "skills",
                "loot"),
                file, "<root>", errors);
        String id = reqStr(m, "id", file, "<root>", errors);
        String name = reqStr(m, "name", file, "<root>", errors);
        Integer level = reqInt(m, "level", file, "<root>", errors);
        String rank = reqStr(m, "rank", file, "<root>", errors);
        String species = reqStr(m, "species", file, "<root>", errors);
        List<Object> skills = reqList(m, "skills", file, "<root>", errors);
        String loot = reqStr(m, "loot", file, "<root>", errors);
        if (id == null || name == null || level == null || rank == null || species == null || skills == null
                || loot == null) {
            return null;
        }
        return new EnemyData(id, name, level, rank, species, optStr(m, "boss_body", "", file, "<root>", errors),
                strList(optList(m, "tags", file, "<root>", errors), file, "tags", errors),
                strList(skills, file, "skills", errors), loot);
    }

    static AbilityDef ability(String id, Map<String, Object> m, String file, String loc,
            List<ContentError> errors) {
        unknownKeys(m, Set.of("id", "name", "core", "mods", "target", "center", "split_damage", "signal",
                "telegraph", "lethal", "lethal_ratio"), file, loc, errors);
        String core = reqStr(m, "core", file, loc, errors);
        if (core == null) {
            return null;
        }
        return new AbilityDef(id, optStr(m, "name", id, file, loc, errors), core, mods(m, file, loc, errors),
                optStr(m, "target", "", file, loc, errors), optStr(m, "center", "", file, loc, errors), optBool(m, "split_damage", false, file, loc, errors),
                optStr(m, "signal", "", file, loc, errors), optBool(m, "telegraph", false, file, loc, errors), optBool(m, "lethal", false, file, loc, errors),
                m.containsKey("lethal_ratio") ? reqDouble(m, "lethal_ratio", file, loc, errors) : null);
    }

    static EncounterData encounter(Map<String, Object> m, String file, List<ContentError> errors) {
        unknownKeys(m, Set.of("id", "name", "party", "arena", "time_budget", "checkpoints", "phases",
                "abilities"), file, "<root>", errors);
        String id = reqStr(m, "id", file, "<root>", errors);
        String name = reqStr(m, "name", file, "<root>", errors);
        Map<String, Object> party = reqMap(m, "party", file, "<root>", errors);
        Map<String, Object> arena = reqMap(m, "arena", file, "<root>", errors);
        Map<String, Object> budget = reqMap(m, "time_budget", file, "<root>", errors);
        List<Object> phases = reqList(m, "phases", file, "<root>", errors);
        if (id == null || name == null || party == null || arena == null || budget == null || phases == null) {
            return null;
        }
        unknownKeys(party, Set.of("size", "required_roles", "required_capabilities"), file, "party", errors);
        unknownKeys(arena, Set.of("template", "markers"), file, "arena", errors);
        unknownKeys(budget, Set.of("target_minutes", "soft_enrage_minutes"), file, "time_budget", errors);
        Integer size = reqInt(party, "size", file, "party", errors);
        Map<String, Object> roles = reqMap(party, "required_roles", file, "party", errors);
        Map<String, Integer> reqRoles = new LinkedHashMap<>();
        if (roles != null) {
            for (Map.Entry<String, Object> e : roles.entrySet()) {
                if (e.getValue() instanceof Integer i) {
                    reqRoles.put(e.getKey(), i);
                } else {
                    errors.add(new ContentError(file, "party.required_roles." + e.getKey(), "expected int"));
                }
            }
        }
        String template = reqStr(arena, "template", file, "arena", errors);
        Integer targetMin = reqInt(budget, "target_minutes", file, "time_budget", errors);
        Integer softMin = reqInt(budget, "soft_enrage_minutes", file, "time_budget", errors);
        if (size == null || template == null || targetMin == null || softMin == null) {
            return null;
        }
        List<PhaseDef> parsedPhases = new ArrayList<>();
        for (int i = 0; i < phases.size(); i++) {
            Object o = phases.get(i);
            if (!(o instanceof Map<?, ?> pm)) {
                errors.add(new ContentError(file, "phases[" + i + "]", "expected mapping"));
                continue;
            }
            @SuppressWarnings("unchecked")
            PhaseDef p = phase((Map<String, Object>) pm, file, "phases[" + i + "]", errors);
            if (p != null) {
                parsedPhases.add(p);
            }
        }
        Map<String, AbilityDef> abilities = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : optMap(m, "abilities", file, "<root>", errors).entrySet()) {
            if (!(e.getValue() instanceof Map<?, ?> am)) {
                errors.add(new ContentError(file, "abilities." + e.getKey(), "expected mapping"));
                continue;
            }
            @SuppressWarnings("unchecked")
            AbilityDef a = ability(e.getKey(), (Map<String, Object>) am, file,
                    "abilities." + e.getKey(), errors);
            if (a != null) {
                abilities.put(e.getKey(), a);
            }
        }
        return new EncounterData(id, name, size, reqRoles,
                strList(optList(party, "required_capabilities", file, "party", errors), file, "party.required_capabilities", errors),
                template, optMap(arena, "markers", file, "arena", errors), targetMin, softMin,
                strList(optList(m, "checkpoints", file, "<root>", errors), file, "checkpoints", errors),
                parsedPhases, abilities);
    }

    static PhaseDef phase(Map<String, Object> m, String file, String loc, List<ContentError> errors) {
        unknownKeys(m, Set.of("id", "hp_range", "loop_period_seconds", "on_enter", "timeline"),
                file, loc, errors);
        String pid = reqStr(m, "id", file, loc, errors);
        List<Object> range = reqList(m, "hp_range", file, loc, errors);
        List<Object> timeline = reqList(m, "timeline", file, loc, errors);
        if (pid == null || range == null || timeline == null) {
            return null;
        }
        if (range.size() != 2 || !(range.get(0) instanceof Integer from)
                || !(range.get(1) instanceof Integer to)) {
            errors.add(new ContentError(file, loc + ".hp_range", "expected [int, int]"));
            return null;
        }
        List<TimelineEntry> entries = new ArrayList<>();
        for (int i = 0; i < timeline.size(); i++) {
            Object o = timeline.get(i);
            if (!(o instanceof Map<?, ?> tm)) {
                errors.add(new ContentError(file, loc + ".timeline[" + i + "]", "expected mapping"));
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> t = (Map<String, Object>) tm;
            unknownKeys(t, Set.of("at_seconds", "ability"), file, loc + ".timeline[" + i + "]", errors);
            Object at = t.get("at_seconds");
            Object ab = t.get("ability");
            if (!(at instanceof Integer sec) || !(ab instanceof String ability)) {
                errors.add(new ContentError(file, loc + ".timeline[" + i + "]",
                        "expected {at_seconds: int, ability: id}"));
                continue;
            }
            entries.add(new TimelineEntry(sec, ability));
        }
        List<String> onEnter = new ArrayList<>();
        for (Object o : optList(m, "on_enter", file, loc, errors)) {
            if (o instanceof Map<?, ?> em) {
                @SuppressWarnings("unchecked")
                Map<String, Object> e = (Map<String, Object>) em;
                unknownKeys(e, Set.of("ability"), file, loc + ".on_enter", errors);
                Object ab = e.get("ability");
                if (ab instanceof String s) {
                    onEnter.add(s);
                } else {
                    errors.add(new ContentError(file, loc + ".on_enter", "expected {ability: id}"));
                }
            } else {
                errors.add(new ContentError(file, loc + ".on_enter", "expected {ability: id}"));
            }
        }
        return new PhaseDef(pid, from, to, optInt(m, "loop_period_seconds", 0, file, loc, errors), onEnter, entries);
    }

    static LootTable loot(Map<String, Object> m, String file, List<ContentError> errors) {
        unknownKeys(m, Set.of("id", "direct", "pity", "materials", "runes"), file, "<root>", errors);
        String id = reqStr(m, "id", file, "<root>", errors);
        Map<String, Object> pity = reqMap(m, "pity", file, "<root>", errors);
        if (id == null || pity == null) {
            return null;
        }
        List<DirectDrop> direct = new ArrayList<>();
        for (Object o : optList(m, "direct", file, "<root>", errors)) {
            if (!(o instanceof Map<?, ?> dm)) {
                errors.add(new ContentError(file, "direct", "expected mapping"));
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> d = (Map<String, Object>) dm;
            unknownKeys(d, Set.of("item", "p"), file, "direct", errors);
            Object item = d.get("item");
            Object p = d.get("p");
            if (!(item instanceof String s) || !(p instanceof Number n)) {
                errors.add(new ContentError(file, "direct", "expected {item: id, p: number}"));
                continue;
            }
            direct.add(new DirectDrop(s, n.doubleValue()));
        }
        unknownKeys(pity, Set.of("currency", "per_kill", "exchange"), file, "pity", errors);
        // exchange: null explicitly disables pity exchange; other wrong types are errors.
        Map<String, Object> exchange = pity.get("exchange") == null ? null
                : reqMap(pity, "exchange", file, "pity", errors);
        Integer exchangeCost = null;
        String exchangeItem = null;
        if (exchange != null) {
            unknownKeys(exchange, Set.of("cost", "item"), file, "pity.exchange", errors);
            exchangeCost = reqInt(exchange, "cost", file, "pity.exchange", errors);
            exchangeItem = reqStr(exchange, "item", file, "pity.exchange", errors);
        }
        return new LootTable(id, direct, optStr(pity, "currency", "", file, "pity", errors),
                optInt(pity, "per_kill", 0, file, "pity", errors), exchangeCost, exchangeItem,
                materials(m, file, errors), runeDrops(m, file, errors));
    }

    private static java.util.List<com.thuvstu.hayatemod.core.content.model.Models.RuneDrop> runeDrops(
            Map<String, Object> m, String file, List<ContentError> errors) {
        java.util.List<com.thuvstu.hayatemod.core.content.model.Models.RuneDrop> out =
                new java.util.ArrayList<>();
        for (Object o : optList(m, "runes", file, "<root>", errors)) {
            if (!(o instanceof Map<?, ?> dm)) {
                errors.add(new ContentError(file, "runes", "expected mapping"));
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> d = (Map<String, Object>) dm;
            unknownKeys(d, Set.of("id", "p"), file, "runes", errors);
            Object rid = d.get("id");
            Object p = d.get("p");
            if (!(rid instanceof String s) || !(p instanceof Number n)) {
                errors.add(new ContentError(file, "runes", "expected {id: rune, p: number}"));
                continue;
            }
            out.add(new com.thuvstu.hayatemod.core.content.model.Models.RuneDrop(s, n.doubleValue()));
        }
        return out;
    }

    private static List<MaterialDrop> materials(
            Map<String, Object> m, String file, List<ContentError> errors) {
        List<MaterialDrop> out =
                new ArrayList<>();
        for (Object o : optList(m, "materials", file, "<root>", errors)) {
            if (!(o instanceof Map<?, ?> mm)) {
                errors.add(new ContentError(file, "materials", "expected mapping"));
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> d = (Map<String, Object>) mm;
            unknownKeys(d, Set.of("item", "count"), file, "materials", errors);
            Object item = d.get("item");
            Object count = d.get("count");
            if (!(item instanceof String s) || !(count instanceof List<?> range)
                    || range.size() != 2 || !(range.get(0) instanceof Integer lo)
                    || !(range.get(1) instanceof Integer hi)) {
                errors.add(new ContentError(file, "materials", "expected {item: id, count: [min, max]}"));
                continue;
            }
            out.add(new MaterialDrop(s, lo, hi));
        }
        return out;
    }

    static JobDef job(Map<String, Object> m, String file, List<ContentError> errors) {
        unknownKeys(m, Set.of("id", "name", "roles", "weapons", "description"), file, "<root>", errors);
        String id = reqStr(m, "id", file, "<root>", errors);
        String name = reqStr(m, "name", file, "<root>", errors);
        List<Object> roles = reqList(m, "roles", file, "<root>", errors);
        if (id == null || name == null || roles == null) {
            return null;
        }
        return new JobDef(id, name, strList(roles, file, "roles", errors),
                strList(optList(m, "weapons", file, "<root>", errors), file, "weapons", errors),
                optStr(m, "description", "", file, "<root>", errors));
    }

    static NpcData npc(Map<String, Object> m, String file, List<ContentError> errors) {
        unknownKeys(m, Set.of("id", "name", "role", "capabilities", "proficiency"), file, "<root>", errors);
        String id = reqStr(m, "id", file, "<root>", errors);
        String name = reqStr(m, "name", file, "<root>", errors);
        String role = reqStr(m, "role", file, "<root>", errors);
        if (id == null || name == null || role == null) {
            return null;
        }
        return new NpcData(id, name, role,
                strList(optList(m, "capabilities", file, "<root>", errors), file, "capabilities", errors),
                optDouble(m, "proficiency", 0.5, file, "<root>", errors));
    }

    static RuneDef rune(Map<String, Object> m, String file, List<ContentError> errors) {
        unknownKeys(m, Set.of("id", "name", "flavor", "effects"), file, "<root>", errors);
        String id = reqStr(m, "id", file, "<root>", errors);
        String name = reqStr(m, "name", file, "<root>", errors);
        List<Object> effects = reqList(m, "effects", file, "<root>", errors);
        if (id == null || name == null || effects == null) {
            return null;
        }
        List<EffectDef> parsed = new ArrayList<>();
        for (Object o : effects) {
            if (!(o instanceof Map<?, ?> em)) {
                errors.add(new ContentError(file, "effects", "expected mapping"));
                continue;
            }
            @SuppressWarnings("unchecked")
            EffectDef e = effect((Map<String, Object>) em, file, "effects[" + parsed.size() + "]", errors);
            if (e != null) {
                parsed.add(e.identified("rune:" + id, "effects/" + String.format(java.util.Locale.ROOT, "%08d", parsed.size())));
            }
        }
        return new RuneDef(id, name, optStr(m, "flavor", "", file, "<root>", errors), parsed);
    }

    static MaterialDef material(Map<String, Object> m, String file, List<ContentError> errors) {
        unknownKeys(m, Set.of("id", "name", "source"), file, "<root>", errors);
        String id = reqStr(m, "id", file, "<root>", errors);
        String name = reqStr(m, "name", file, "<root>", errors);
        if (id == null || name == null) {
            return null;
        }
        return new MaterialDef(id, name, optStr(m, "source", "", file, "<root>", errors));
    }

    static KeystoneDef keystone(Map<String, Object> m, String file, List<ContentError> errors) {
        unknownKeys(m, Set.of("id", "name", "desc", "chain_bonus", "damage_mult", "cooldown_mult",
                "melee_mult"), file, "<root>", errors);
        String id = reqStr(m, "id", file, "<root>", errors);
        String name = reqStr(m, "name", file, "<root>", errors);
        if (id == null || name == null) {
            return null;
        }
        return new KeystoneDef(id, name, optStr(m, "desc", "", file, "<root>", errors), optInt(m, "chain_bonus", 0, file, "<root>", errors),
                optDouble(m, "damage_mult", 1.0, file, "<root>", errors), optDouble(m, "cooldown_mult", 1.0, file, "<root>", errors),
                optDouble(m, "melee_mult", 1.0, file, "<root>", errors));
    }

    static MarketBook market(Map<String, Object> m, String file, List<ContentError> errors) {
        unknownKeys(m, Set.of("spread_default", "listings"), file, "<root>", errors);
        List<Object> listings = reqList(m, "listings", file, "<root>", errors);
        if (listings == null) {
            return null;
        }
        List<Listing> out = new java.util.ArrayList<>();
        for (int i = 0; i < listings.size(); i++) {
            Object o = listings.get(i);
            if (!(o instanceof Map<?, ?> lm)) {
                errors.add(new ContentError(file, "listings[" + i + "]", "expected mapping"));
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> l = (Map<String, Object>) lm;
            unknownKeys(l, Set.of("item", "base_price", "target_stock", "elasticity", "start_stock",
                    "daily_supply", "daily_demand"), file, "listings", errors);
            String item = reqStr(l, "item", file, "listings", errors);
            Double base = reqDouble(l, "base_price", file, "listings", errors);
            Double target = reqDouble(l, "target_stock", file, "listings", errors);
            Double elast = reqDouble(l, "elasticity", file, "listings", errors);
            if (item == null || base == null || target == null || elast == null) {
                continue;
            }
            out.add(new Listing(item, base, target, elast, optDouble(l, "start_stock", target, file, "listings", errors),
                    optDouble(l, "daily_supply", 0.0, file, "listings", errors), optDouble(l, "daily_demand", 0.0, file, "listings", errors)));
        }
        return new MarketBook(optDouble(m, "spread_default", 0.2, file, "<root>", errors), out);
    }

    static MiningBook mining(Map<String, Object> m, String file, List<ContentError> errors) {
        unknownKeys(m, Set.of("ores", "levels", "gem_bonus"), file, "<root>", errors);
        Map<String, Integer> ores = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> e : optMap(m, "ores", file, "<root>", errors).entrySet()) {
            if (e.getValue() instanceof Integer i) {
                ores.put(e.getKey(), i);
            } else {
                errors.add(new ContentError(file, "ores." + e.getKey(), "expected int xp"));
            }
        }
        List<LevelRow> levels = new java.util.ArrayList<>();
        for (Object o : optList(m, "levels", file, "<root>", errors)) {
            if (!(o instanceof Map<?, ?> rm)) {
                errors.add(new ContentError(file, "levels", "expected mapping"));
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> r = (Map<String, Object>) rm;
            unknownKeys(r, Set.of("level", "xp_required", "unlocks"), file, "levels", errors);
            Object lvl = r.get("level");
            Object xp = r.get("xp_required");
            if (!(lvl instanceof Integer li) || !(xp instanceof Integer xi)) {
                errors.add(new ContentError(file, "levels", "expected {level: int, xp_required: int}"));
                continue;
            }
            levels.add(new LevelRow(li, xi, strList(optList(r, "unlocks", file, "levels", errors), file, "levels", errors)));
        }
        levels.sort(java.util.Comparator.comparingInt(LevelRow::level));
        Map<String, Object> gem = optMap(m, "gem_bonus", file, "<root>", errors);
        unknownKeys(gem, Set.of("min_level", "ores", "bonus_materials"), file, "gem_bonus", errors);
        return new MiningBook(ores, levels, optInt(gem, "min_level", 15, file, "gem_bonus", errors),
                strList(optList(gem, "ores", file, "gem_bonus", errors), file, "gem_bonus.ores", errors),
                optInt(gem, "bonus_materials", 1, file, "gem_bonus", errors));
    }

    static FishingBook fishing(Map<String, Object> m, String file, List<ContentError> errors) {
        unknownKeys(m, Set.of("catch_xp", "levels"), file, "<root>", errors);
        List<LevelRow> levels = new java.util.ArrayList<>();
        for (Object o : optList(m, "levels", file, "<root>", errors)) {
            if (!(o instanceof Map<?, ?> rm)) {
                errors.add(new ContentError(file, "levels", "expected mapping"));
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> r = (Map<String, Object>) rm;
            unknownKeys(r, Set.of("level", "xp_required", "unlocks"), file, "levels", errors);
            Object lvl = r.get("level");
            Object xp = r.get("xp_required");
            if (!(lvl instanceof Integer li) || !(xp instanceof Integer xi)) {
                errors.add(new ContentError(file, "levels", "expected {level: int, xp_required: int}"));
                continue;
            }
            levels.add(new LevelRow(li, xi, strList(optList(r, "unlocks", file, "levels", errors), file, "levels", errors)));
        }
        levels.sort(java.util.Comparator.comparingInt(LevelRow::level));
        return new FishingBook(optInt(m, "catch_xp", 20, file, "<root>", errors), levels);
    }

    static WorldLayout worldLayout(Map<String, Object> m, String file, List<ContentError> errors) {
        unknownKeys(m, Set.of("arena", "town"), file, "<root>", errors);
        Map<String, Object> arena = optMap(m, "arena", file, "<root>", errors);
        Map<String, Object> town = optMap(m, "town", file, "<root>", errors);
        unknownKeys(arena, Set.of("x", "y", "z", "half_size"), file, "arena", errors);
        unknownKeys(town, Set.of("x", "y", "z", "half_size", "gate_radius"), file, "town", errors);
        return new WorldLayout(
                new PlacedBox(optInt(arena, "x", 10000, file, "arena", errors), optInt(arena, "y", 100, file, "arena", errors),
                        optInt(arena, "z", 10000, file, "arena", errors), optInt(arena, "half_size", 12, file, "arena", errors)),
                new PlacedBox(optInt(town, "x", 200, file, "town", errors), optInt(town, "y", 100, file, "town", errors),
                        optInt(town, "z", 200, file, "town", errors), optInt(town, "half_size", 12, file, "town", errors)),
                optDouble(town, "gate_radius", 64.0, file, "town", errors));
    }

    static StructTemplate structTemplate(Map<String, Object> m, String file,
            List<ContentError> errors) {
        unknownKeys(m, Set.of("id", "name", "blocks"), file, "<root>", errors);
        String id = reqStr(m, "id", file, "<root>", errors);
        List<Object> blocks = reqList(m, "blocks", file, "<root>", errors);
        if (id == null || blocks == null) {
            return null;
        }
        List<StructOp> ops = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            Object o = blocks.get(i);
            if (!(o instanceof Map<?, ?> bm)) {
                errors.add(new ContentError(file, "blocks[" + i + "]", "expected mapping"));
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> b = (Map<String, Object>) bm;
            unknownKeys(b, Set.of("op", "from", "to", "at", "block"), file, "blocks", errors);
            String op = reqStr(b, "op", file, "blocks", errors);
            String block = reqStr(b, "block", file, "blocks", errors);
            if (op == null || block == null) {
                continue;
            }
            List<Integer> from = intTriple(b.get("from"), file, "blocks[" + i + "].from", errors);
            List<Integer> to = intTriple(b.get("to"), file, "blocks[" + i + "].to", errors);
            Object at = b.get("at");
            if (at != null) {
                List<Integer> point = intTriple(at, file, "blocks[" + i + "].at", errors);
                if (point == null) {
                    continue;
                }
                from = point;
                to = point;
            }
            if (from == null || to == null) {
                errors.add(new ContentError(file, "blocks[" + i + "]",
                        "fill needs from+to, set needs at"));
                continue;
            }
            ops.add(new StructOp(op, from, to, block));
        }
        return new StructTemplate(id, optStr(m, "name", id, file, "<root>", errors), ops);
    }

    private static List<Integer> intTriple(Object v, String file, String loc,
            List<ContentError> errors) {
        if (v == null) {
            return null;
        }
        if (!(v instanceof List<?> list) || list.size() != 3) {
            errors.add(new ContentError(file, loc, "expected [x, y, z] ints"));
            return null;
        }
        List<Integer> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Integer i)) {
                errors.add(new ContentError(file, loc, "expected [x, y, z] ints"));
                return null;
            }
            out.add(i);
        }
        return out;
    }

    static Ruleset ruleset(Map<String, Object> m, String file, List<ContentError> errors) {
        unknownKeys(m, Set.of("id", "extends_global", "enemy", "rewards", "tag_overrides", "by_id"), file, "<root>", errors);
        String id = reqStr(m, "id", file, "<root>", errors);
        boolean inherits = optBool(m, "extends_global", true, file, "<root>", errors);
        var defaults = ruleMultipliers(m, file, "<root>", errors);
        var tags = new ArrayList<com.thuvstu.hayatemod.core.content.model.Models.TagRule>();
        int index = 0;
        for (Object value : optList(m, "tag_overrides", file, "<root>", errors)) {
            String loc = "tag_overrides[" + index++ + "]";
            if (!(value instanceof Map<?, ?> raw)) {
                errors.add(new ContentError(file, loc, "expected mapping"));
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> override = (Map<String, Object>) raw;
            unknownKeys(override, Set.of("tag", "enemy", "rewards"), file, loc, errors);
            String tag = reqStr(override, "tag", file, loc, errors);
            var patch = ruleMultipliers(override, file, loc, errors);
            if (tag != null) tags.add(new com.thuvstu.hayatemod.core.content.model.Models.TagRule(tag, patch));
        }
        var ids = new java.util.LinkedHashMap<String, com.thuvstu.hayatemod.core.content.model.Models.RulePatch>();
        for (var entry : optMap(m, "by_id", file, "<root>", errors).entrySet()) {
            String loc = "by_id." + entry.getKey();
            if (!(entry.getValue() instanceof Map<?, ?> raw)) {
                errors.add(new ContentError(file, loc, "expected mapping"));
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> override = (Map<String, Object>) raw;
            unknownKeys(override, Set.of("enemy", "rewards"), file, loc, errors);
            ids.put(entry.getKey(), ruleMultipliers(override, file, loc, errors));
        }
        return id == null ? null : new Ruleset(id, inherits, defaults, tags, ids);
    }

    private static com.thuvstu.hayatemod.core.content.model.Models.RulePatch ruleMultipliers(
            Map<String, Object> parent, String file, String loc, List<ContentError> errors) {
        var enemy = optMap(parent, "enemy", file, loc, errors);
        var rewards = optMap(parent, "rewards", file, loc, errors);
        unknownKeys(enemy, Set.of("hp_mult", "effective_dps_mult"), file, loc + ".enemy", errors);
        unknownKeys(rewards, Set.of("pity_shard_mult", "craft_material_mult"), file, loc + ".rewards", errors);
        return new com.thuvstu.hayatemod.core.content.model.Models.RulePatch(
                enemy.containsKey("hp_mult") ? reqDouble(enemy, "hp_mult", file, loc + ".enemy", errors) : null,
                enemy.containsKey("effective_dps_mult") ? reqDouble(enemy, "effective_dps_mult", file, loc + ".enemy", errors) : null,
                rewards.containsKey("pity_shard_mult") ? reqDouble(rewards, "pity_shard_mult", file, loc + ".rewards", errors) : null,
                rewards.containsKey("craft_material_mult") ? reqDouble(rewards, "craft_material_mult", file, loc + ".rewards", errors) : null);
    }

    static Vocabulary vocabulary(Map<String, Object> m, String file, List<ContentError> errors) {
        unknownKeys(m, Set.of("skill_cores", "triggers", "conditions", "actions", "statuses", "elements",
                "signals", "tag_namespaces", "limits"), file, "<root>", errors);
        Map<String, Object> limits = reqMap(m, "limits", file, "<root>", errors);
        if (limits == null) {
            return null;
        }
        unknownKeys(limits, Set.of("max_chain_depth", "max_projectiles", "max_radius", "max_effects_per_tick",
                "max_actions_per_tick", "max_tasks_per_tick", "max_pending_tasks", "max_shield_amount",
                "max_shield_duration_ticks", "max_cooldown_reduction_seconds", "max_cast_seconds", "max_cast_boost_duration_ticks"), file, "limits", errors);
        var defaults = ExecutionLimits.defaults();
        return new Vocabulary(
                Set.copyOf(strList(optList(m, "skill_cores", file, "<root>", errors), file, "skill_cores", errors)),
                Set.copyOf(strList(optList(m, "triggers", file, "<root>", errors), file, "triggers", errors)),
                Set.copyOf(strList(optList(m, "conditions", file, "<root>", errors), file, "conditions", errors)),
                Set.copyOf(strList(optList(m, "actions", file, "<root>", errors), file, "actions", errors)),
                Set.copyOf(strList(optList(m, "statuses", file, "<root>", errors), file, "statuses", errors)),
                Set.copyOf(strList(optList(m, "elements", file, "<root>", errors), file, "elements", errors)),
                Set.copyOf(strList(optList(m, "signals", file, "<root>", errors), file, "signals", errors)),
                Set.copyOf(strList(optList(m, "tag_namespaces", file, "<root>", errors), file, "tag_namespaces", errors)),
                optInt(limits, "max_chain_depth", 3, file, "limits", errors), optInt(limits, "max_projectiles", 8, file, "limits", errors),
                optDouble(limits, "max_radius", 12.0, file, "limits", errors),
                new ExecutionLimits(
                        optInt(limits, "max_effects_per_tick", defaults.effectsPerTick(), file, "limits", errors),
                        optInt(limits, "max_actions_per_tick", defaults.actionsPerTick(), file, "limits", errors),
                        optInt(limits, "max_tasks_per_tick", defaults.tasksPerTick(), file, "limits", errors),
                        optInt(limits, "max_pending_tasks", defaults.pendingTasks(), file, "limits", errors)),
                new com.thuvstu.hayatemod.core.content.model.Models.CombatLimits(
                        optDouble(limits, "max_shield_amount", 200, file, "limits", errors),
                        optInt(limits, "max_shield_duration_ticks", 1200, file, "limits", errors),
                        optDouble(limits, "max_cooldown_reduction_seconds", 30, file, "limits", errors)),
                new com.thuvstu.hayatemod.core.content.model.Models.CastingLimits(
                        optDouble(limits, "max_cast_seconds", 30, file, "limits", errors),
                        optInt(limits, "max_cast_boost_duration_ticks", 1200, file, "limits", errors)));
    }

    static List<ReferenceEntry> reference(Map<String, Object> m, String file, List<ContentError> errors) {
        unknownKeys(m, Set.of("reference", "tuning_notes"), file, "<root>", errors);
        Map<String, Object> notes = optMap(m, "tuning_notes", file, "<root>", errors);
        unknownKeys(notes, Set.of("role_dps_factor", "npc_proficiency_factor", "mechanic_uptime_factor"),
                file, "tuning_notes", errors);
        Map<String, Object> factors = optMap(notes, "role_dps_factor", file, "tuning_notes", errors);
        for (String role : factors.keySet()) {
            reqDouble(factors, role, file, "tuning_notes.role_dps_factor", errors);
        }
        optDouble(notes, "npc_proficiency_factor", 0.9, file, "tuning_notes", errors);
        optDouble(notes, "mechanic_uptime_factor", 0.85, file, "tuning_notes", errors);
        List<ReferenceEntry> out = new ArrayList<>();
        for (Object o : optList(m, "reference", file, "<root>", errors)) {
            if (!(o instanceof Map<?, ?> rm)) {
                errors.add(new ContentError(file, "reference", "expected mapping"));
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> r = (Map<String, Object>) rm;
            unknownKeys(r, Set.of("level", "hp_ref", "dps_ref", "mit_ref", "hps_ref"), file, "reference", errors);
            Object lvl = r.get("level");
            Object hp = r.get("hp_ref");
            Object dps = r.get("dps_ref");
            Object mit = r.get("mit_ref");
            Object hps = r.get("hps_ref");
            if (lvl instanceof Integer l && hp instanceof Number h && dps instanceof Number d
                    && mit instanceof Number mi && hps instanceof Number hs) {
                out.add(new ReferenceEntry(l, h.doubleValue(), d.doubleValue(), mi.doubleValue(),
                        hs.doubleValue()));
            } else {
                errors.add(new ContentError(file, "reference", "expected level/hp_ref/dps_ref/mit_ref/hps_ref"));
            }
        }
        return out;
    }

    static DamageTuning damageTuning(Map<String, Object> m, String file, List<ContentError> errors) {
        unknownKeys(m, Set.of("critical", "caps", "wild", "core_base", "rank_targets",
                "heroic_player_mult", "heroic_reference_level", "rounding"), file, "<root>", errors);
        String rounding = optStr(m, "rounding", "display_floor", file, "<root>", errors);
        if (!rounding.equals("display_floor")) {
            errors.add(new ContentError(file, "rounding", "only display_floor is supported"));
        }
        Map<String, Object> crit = optMap(m, "critical", file, "<root>", errors);
        Map<String, Object> caps = optMap(m, "caps", file, "<root>", errors);
        Map<String, Object> wild = optMap(m, "wild", file, "<root>", errors);
        unknownKeys(crit, Set.of("base_chance", "chance_cap", "base_multiplier", "multiplier_cap"), file, "critical", errors);
        unknownKeys(caps, Set.of("target_resistance_max", "armor_mitigation_max", "guard_reduction_max"), file, "caps", errors);
        unknownKeys(wild, Set.of("damage_mult", "hp_mult"), file, "wild", errors);
        Map<String, Double> coreBase = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : optMap(m, "core_base", file, "<root>", errors).entrySet()) {
            if (e.getValue() instanceof Number n) {
                coreBase.put(e.getKey(), n.doubleValue());
            } else {
                errors.add(new ContentError(file, "core_base." + e.getKey(), "expected number"));
            }
        }
        Map<String, RankTarget> rankTargets = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : optMap(m, "rank_targets", file, "<root>", errors).entrySet()) {
            if (e.getValue() instanceof Map<?, ?> rm) {
                @SuppressWarnings("unchecked")
                Map<String, Object> r = (Map<String, Object>) rm;
                unknownKeys(r, Set.of("fight_seconds", "damage_taken_ratio"), file, "rank_targets." + e.getKey(), errors);
                Object sec = r.get("fight_seconds");
                Object ratio = r.get("damage_taken_ratio");
                if (sec instanceof Integer s && ratio instanceof Number n) {
                    rankTargets.put(e.getKey(), new RankTarget(s, n.doubleValue()));
                } else {
                    errors.add(new ContentError(file, "rank_targets." + e.getKey(),
                            "expected {fight_seconds: int, damage_taken_ratio: number}"));
                }
            } else {
                errors.add(new ContentError(file, "rank_targets." + e.getKey(), "expected mapping"));
            }
        }
        return new DamageTuning(optDouble(crit, "base_chance", 0.05, file, "critical", errors), optDouble(crit, "chance_cap", 0.6, file, "critical", errors),
                optDouble(crit, "base_multiplier", 1.5, file, "critical", errors), optDouble(crit, "multiplier_cap", 2.5, file, "critical", errors),
                optDouble(caps, "target_resistance_max", 0.75, file, "caps", errors),
                optDouble(caps, "armor_mitigation_max", 0.8, file, "caps", errors),
                optDouble(caps, "guard_reduction_max", 0.9, file, "caps", errors), coreBase, rankTargets,
                optDouble(m, "heroic_player_mult", 10.0, file, "<root>", errors), optInt(m, "heroic_reference_level", 10, file, "<root>", errors),
                optDouble(wild, "damage_mult", 1.5, file, "wild", errors), optDouble(wild, "hp_mult", 2.0, file, "wild", errors));
    }
}
