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
                parsed.put(e.getKey(), s);
            }
        }
        return new WeaponCard(id, name, family, rarity, itemLevel,
                strList(optList(m, "tags_extra"), file, "tags_extra", errors),
                strList(optList(m, "role_hint"), file, "role_hint", errors),
                parsed, optStr(m, "design_note", ""));
    }

    static SkillDef skill(Map<String, Object> m, String file, String loc, List<ContentError> errors) {
        unknownKeys(m, Set.of("core", "mods", "effects"), file, loc, errors);
        String core = reqStr(m, "core", file, loc, errors);
        if (core == null) {
            return null;
        }
        List<EffectDef> effects = new ArrayList<>();
        for (Object o : optList(m, "effects")) {
            if (!(o instanceof Map<?, ?> em)) {
                errors.add(new ContentError(file, loc + ".effects", "expected mapping"));
                continue;
            }
            @SuppressWarnings("unchecked")
            EffectDef e = effect((Map<String, Object>) em, file, loc + ".effects", errors);
            if (e != null) {
                effects.add(e);
            }
        }
        return new SkillDef(core, optMap(m, "mods"), effects);
    }

    static EffectDef effect(Map<String, Object> m, String file, String loc, List<ContentError> errors) {
        unknownKeys(m, Set.of("trigger", "conditions", "actions", "flags", "scope", "radius"), file,
                loc, errors);
        String trigger = reqStr(m, "trigger", file, loc, errors);
        List<Object> actions = reqList(m, "actions", file, loc, errors);
        if (trigger == null || actions == null) {
            return null;
        }
        List<ConditionDef> conds = new ArrayList<>();
        for (Object o : optList(m, "conditions")) {
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
                conds.add(new ConditionDef(type, optStr(c, "status", null),
                        optDouble(c, "value", 0.0), optStr(c, "name", ""),
                        optDouble(c, "max", 0.0)));
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
                    "distance", "amount", "status", "effect", "ref", "formula"),
                    file, loc + ".actions", errors);
            String type = reqStr(a, "type", file, loc + ".actions", errors);
            if (type == null) {
                continue;
            }
            // Formula amounts fail fast at load (validator re-checks names as V06).
            String formula = optStr(a, "formula", "");
            if (!formula.isEmpty()) {
                try {
                    com.thuvstu.hayatemod.core.engine.Formula.parse(formula);
                } catch (com.thuvstu.hayatemod.core.engine.Formula.FormulaException e) {
                    errors.add(new ContentError(file, loc + ".actions",
                            "bad formula '" + formula + "': " + e.getMessage()));
                    continue;
                }
            }
            double mult = optDouble(a, "damage_mult", 0.0);
            acts.add(new ActionDef(type, optInt(a, "count", 0), mult, optStr(a, "target", ""),
                    optDouble(a, "radius", 0.0),
                    strList(optList(a, "inherit_tags"), file, loc + ".actions.inherit_tags", errors),
                    optDouble(a, "distance", 0.0), optDouble(a, "amount", 0.0),
                    optStr(a, "status", ""), optStr(a, "effect", ""), optStr(a, "ref", ""),
                    optStr(a, "formula", "")));
        }
        Map<String, Object> flags = optMap(m, "flags");
        return new EffectDef(trigger, conds, acts,
                strList(optList(flags, "prevent_recursive"), file, loc + ".flags.prevent_recursive", errors),
                optInt(flags, "max_chain_depth", -1), optStr(m, "scope", ""),
                optDouble(m, "radius", 0.0));
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
        return new EnemyData(id, name, level, rank, species, optStr(m, "boss_body", ""),
                strList(optList(m, "tags"), file, "tags", errors),
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
        return new AbilityDef(id, optStr(m, "name", id), core, optMap(m, "mods"),
                optStr(m, "target", ""), optStr(m, "center", ""), optBool(m, "split_damage", false),
                optStr(m, "signal", ""), optBool(m, "telegraph", false), optBool(m, "lethal", false),
                m.get("lethal_ratio") instanceof Number n ? n.doubleValue() : null);
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
        for (Map.Entry<String, Object> e : optMap(m, "abilities").entrySet()) {
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
                strList(optList(party, "required_capabilities"), file, "party.required_capabilities", errors),
                template, optMap(arena, "markers"), targetMin, softMin,
                strList(optList(m, "checkpoints"), file, "checkpoints", errors),
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
        for (Object o : optList(m, "on_enter")) {
            if (o instanceof Map<?, ?> em) {
                @SuppressWarnings("unchecked")
                Map<String, Object> e = (Map<String, Object>) em;
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
        return new PhaseDef(pid, from, to, optInt(m, "loop_period_seconds", 0), onEnter, entries);
    }

    static LootTable loot(Map<String, Object> m, String file, List<ContentError> errors) {
        unknownKeys(m, Set.of("id", "direct", "pity", "materials", "runes"), file, "<root>", errors);
        String id = reqStr(m, "id", file, "<root>", errors);
        Map<String, Object> pity = reqMap(m, "pity", file, "<root>", errors);
        if (id == null || pity == null) {
            return null;
        }
        List<DirectDrop> direct = new ArrayList<>();
        for (Object o : optList(m, "direct")) {
            if (!(o instanceof Map<?, ?> dm)) {
                errors.add(new ContentError(file, "direct", "expected mapping"));
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> d = (Map<String, Object>) dm;
            Object item = d.get("item");
            Object p = d.get("p");
            if (!(item instanceof String s) || !(p instanceof Number n)) {
                errors.add(new ContentError(file, "direct", "expected {item: id, p: number}"));
                continue;
            }
            direct.add(new DirectDrop(s, n.doubleValue()));
        }
        Object perKill = pity.get("per_kill");
        Object cost = pity.get("cost");
        // pity: {currency, per_kill, exchange: {cost, item} | null}
        Map<String, Object> exchange = null;
        Object ex = pity.get("exchange");
        if (ex instanceof Map<?, ?> em) {
            @SuppressWarnings("unchecked")
            Map<String, Object> x = (Map<String, Object>) em;
            exchange = x;
        }
        return new LootTable(id, direct, optStr(pity, "currency", ""),
                perKill instanceof Integer i ? i : 0,
                exchange != null && exchange.get("cost") instanceof Integer c ? c : null,
                exchange != null ? optStr(exchange, "item", null) : null,
                materials(m, file, errors), runeDrops(m, file, errors));
    }

    private static java.util.List<com.thuvstu.hayatemod.core.content.model.Models.RuneDrop> runeDrops(
            Map<String, Object> m, String file, List<ContentError> errors) {
        java.util.List<com.thuvstu.hayatemod.core.content.model.Models.RuneDrop> out =
                new java.util.ArrayList<>();
        for (Object o : optList(m, "runes")) {
            if (!(o instanceof Map<?, ?> dm)) {
                errors.add(new ContentError(file, "runes", "expected mapping"));
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> d = (Map<String, Object>) dm;
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
        for (Object o : optList(m, "materials")) {
            if (!(o instanceof Map<?, ?> mm)) {
                errors.add(new ContentError(file, "materials", "expected mapping"));
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> d = (Map<String, Object>) mm;
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
                strList(optList(m, "weapons"), file, "weapons", errors),
                optStr(m, "description", ""));
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
                strList(optList(m, "capabilities"), file, "capabilities", errors),
                optDouble(m, "proficiency", 0.5));
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
            EffectDef e = effect((Map<String, Object>) em, file, "effects", errors);
            if (e != null) {
                parsed.add(e);
            }
        }
        return new RuneDef(id, name, optStr(m, "flavor", ""), parsed);
    }

    static MaterialDef material(Map<String, Object> m, String file, List<ContentError> errors) {
        unknownKeys(m, Set.of("id", "name", "source"), file, "<root>", errors);
        String id = reqStr(m, "id", file, "<root>", errors);
        String name = reqStr(m, "name", file, "<root>", errors);
        if (id == null || name == null) {
            return null;
        }
        return new MaterialDef(id, name, optStr(m, "source", ""));
    }

    static KeystoneDef keystone(Map<String, Object> m, String file, List<ContentError> errors) {
        unknownKeys(m, Set.of("id", "name", "desc", "chain_bonus", "damage_mult", "cooldown_mult",
                "melee_mult"), file, "<root>", errors);
        String id = reqStr(m, "id", file, "<root>", errors);
        String name = reqStr(m, "name", file, "<root>", errors);
        if (id == null || name == null) {
            return null;
        }
        return new KeystoneDef(id, name, optStr(m, "desc", ""), optInt(m, "chain_bonus", 0),
                optDouble(m, "damage_mult", 1.0), optDouble(m, "cooldown_mult", 1.0),
                optDouble(m, "melee_mult", 1.0));
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
            out.add(new Listing(item, base, target, elast, optDouble(l, "start_stock", target),
                    optDouble(l, "daily_supply", 0.0), optDouble(l, "daily_demand", 0.0)));
        }
        return new MarketBook(optDouble(m, "spread_default", 0.2), out);
    }

    static MiningBook mining(Map<String, Object> m, String file, List<ContentError> errors) {
        unknownKeys(m, Set.of("ores", "levels", "gem_bonus"), file, "<root>", errors);
        Map<String, Integer> ores = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> e : optMap(m, "ores").entrySet()) {
            if (e.getValue() instanceof Integer i) {
                ores.put(e.getKey(), i);
            } else {
                errors.add(new ContentError(file, "ores." + e.getKey(), "expected int xp"));
            }
        }
        List<LevelRow> levels = new java.util.ArrayList<>();
        for (Object o : optList(m, "levels")) {
            if (!(o instanceof Map<?, ?> rm)) {
                errors.add(new ContentError(file, "levels", "expected mapping"));
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> r = (Map<String, Object>) rm;
            Object lvl = r.get("level");
            Object xp = r.get("xp_required");
            if (!(lvl instanceof Integer li) || !(xp instanceof Integer xi)) {
                errors.add(new ContentError(file, "levels", "expected {level: int, xp_required: int}"));
                continue;
            }
            levels.add(new LevelRow(li, xi, strList(optList(r, "unlocks"), file, "levels", errors)));
        }
        levels.sort(java.util.Comparator.comparingInt(LevelRow::level));
        Map<String, Object> gem = optMap(m, "gem_bonus");
        return new MiningBook(ores, levels, optInt(gem, "min_level", 15),
                strList(optList(gem, "ores"), file, "gem_bonus.ores", errors),
                optInt(gem, "bonus_materials", 1));
    }

    static FishingBook fishing(Map<String, Object> m, String file, List<ContentError> errors) {
        unknownKeys(m, Set.of("catch_xp", "levels"), file, "<root>", errors);
        List<LevelRow> levels = new java.util.ArrayList<>();
        for (Object o : optList(m, "levels")) {
            if (!(o instanceof Map<?, ?> rm)) {
                errors.add(new ContentError(file, "levels", "expected mapping"));
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> r = (Map<String, Object>) rm;
            Object lvl = r.get("level");
            Object xp = r.get("xp_required");
            if (!(lvl instanceof Integer li) || !(xp instanceof Integer xi)) {
                errors.add(new ContentError(file, "levels", "expected {level: int, xp_required: int}"));
                continue;
            }
            levels.add(new LevelRow(li, xi, strList(optList(r, "unlocks"), file, "levels", errors)));
        }
        levels.sort(java.util.Comparator.comparingInt(LevelRow::level));
        return new FishingBook(optInt(m, "catch_xp", 20), levels);
    }

    static WorldLayout worldLayout(Map<String, Object> m, String file, List<ContentError> errors) {
        unknownKeys(m, Set.of("arena", "town"), file, "<root>", errors);
        Map<String, Object> arena = optMap(m, "arena");
        Map<String, Object> town = optMap(m, "town");
        return new WorldLayout(
                new PlacedBox(optInt(arena, "x", 10000), optInt(arena, "y", 100),
                        optInt(arena, "z", 10000), optInt(arena, "half_size", 12)),
                new PlacedBox(optInt(town, "x", 200), optInt(town, "y", 100),
                        optInt(town, "z", 200), optInt(town, "half_size", 12)),
                optDouble(town, "gate_radius", 64.0));
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
        return new StructTemplate(id, optStr(m, "name", id), ops);
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

    static Vocabulary vocabulary(Map<String, Object> m, String file, List<ContentError> errors) {
        unknownKeys(m, Set.of("skill_cores", "triggers", "conditions", "actions", "statuses", "elements",
                "signals", "tag_namespaces", "limits"), file, "<root>", errors);
        Map<String, Object> limits = reqMap(m, "limits", file, "<root>", errors);
        if (limits == null) {
            return null;
        }
        return new Vocabulary(
                Set.copyOf(strList(optList(m, "skill_cores"), file, "skill_cores", errors)),
                Set.copyOf(strList(optList(m, "triggers"), file, "triggers", errors)),
                Set.copyOf(strList(optList(m, "conditions"), file, "conditions", errors)),
                Set.copyOf(strList(optList(m, "actions"), file, "actions", errors)),
                Set.copyOf(strList(optList(m, "statuses"), file, "statuses", errors)),
                Set.copyOf(strList(optList(m, "elements"), file, "elements", errors)),
                Set.copyOf(strList(optList(m, "signals"), file, "signals", errors)),
                Set.copyOf(strList(optList(m, "tag_namespaces"), file, "tag_namespaces", errors)),
                optInt(limits, "max_chain_depth", 3), optInt(limits, "max_projectiles", 8),
                optDouble(limits, "max_radius", 12.0));
    }

    static List<ReferenceEntry> reference(Map<String, Object> m, String file, List<ContentError> errors) {
        List<ReferenceEntry> out = new ArrayList<>();
        for (Object o : optList(m, "reference")) {
            if (!(o instanceof Map<?, ?> rm)) {
                errors.add(new ContentError(file, "reference", "expected mapping"));
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> r = (Map<String, Object>) rm;
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
        Map<String, Object> crit = optMap(m, "critical");
        Map<String, Object> caps = optMap(m, "caps");
        Map<String, Object> wild = optMap(m, "wild");
        Map<String, Double> coreBase = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : optMap(m, "core_base").entrySet()) {
            if (e.getValue() instanceof Number n) {
                coreBase.put(e.getKey(), n.doubleValue());
            } else {
                errors.add(new ContentError(file, "core_base." + e.getKey(), "expected number"));
            }
        }
        Map<String, RankTarget> rankTargets = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : optMap(m, "rank_targets").entrySet()) {
            if (e.getValue() instanceof Map<?, ?> rm) {
                @SuppressWarnings("unchecked")
                Map<String, Object> r = (Map<String, Object>) rm;
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
        return new DamageTuning(optDouble(crit, "base_chance", 0.05), optDouble(crit, "chance_cap", 0.6),
                optDouble(crit, "base_multiplier", 1.5), optDouble(crit, "multiplier_cap", 2.5),
                optDouble(caps, "target_resistance_max", 0.75),
                optDouble(caps, "armor_mitigation_max", 0.8),
                optDouble(caps, "guard_reduction_max", 0.9), coreBase, rankTargets,
                optDouble(m, "heroic_player_mult", 10.0), optInt(m, "heroic_reference_level", 10),
                optDouble(wild, "damage_mult", 1.5), optDouble(wild, "hp_mult", 2.0));
    }
}
