package com.thuvstu.hayatemod.core.content;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import com.thuvstu.hayatemod.core.content.model.Models.ExecutionLimits;
import com.thuvstu.hayatemod.core.content.model.Models.AbilityDef;
import com.thuvstu.hayatemod.core.content.model.Models.ArenaData;
import com.thuvstu.hayatemod.core.content.model.Models.DamageTuning;
import com.thuvstu.hayatemod.core.content.model.Models.EnemyData;
import com.thuvstu.hayatemod.core.content.model.Models.EncounterData;
import com.thuvstu.hayatemod.core.content.model.Models.JobDef;
import com.thuvstu.hayatemod.core.content.model.Models.LootTable;
import com.thuvstu.hayatemod.core.content.model.Models.NpcData;
import com.thuvstu.hayatemod.core.content.model.Models.RuneDef;
import com.thuvstu.hayatemod.core.content.model.Models.KeystoneDef;
import com.thuvstu.hayatemod.core.content.model.Models.ReferenceEntry;
import com.thuvstu.hayatemod.core.content.model.Models.Ruleset;
import com.thuvstu.hayatemod.core.content.model.Models.Vocabulary;
import com.thuvstu.hayatemod.core.content.model.Models.WeaponCard;

/**
 * Loads {@code content/} into a {@link ContentSet}. Safe YAML loader, duplicate
 * keys rejected, no custom tags or code execution. All problems are collected
 * as {@link ContentError}s; files that fail to parse contribute no data.
 */
public final class ContentPack {
    private ContentPack() {
    }

    public record LoadedPack(ContentSet set, List<ContentError> errors) {
        public boolean ok() {
            return errors.isEmpty();
        }
    }

    public static LoadedPack load(Path root) {
        List<ContentError> errors = new ArrayList<>();
        Map<String, WeaponCard> weapons = loadDir(root, "weapons", errors, Parsers::weapon);
        Map<String, EnemyData> enemies = loadDir(root, "enemies", errors, Parsers::enemy);
        Map<String, EncounterData> encounters = loadDir(root, "encounters", errors, Parsers::encounter);
        Map<String, AbilityDef> skills = new LinkedHashMap<>();
        loadSkills(root, errors, skills);
        Map<String, LootTable> loot = loadDir(root, "loot", errors, Parsers::loot);
        Map<String, JobDef> jobs = loadDir(root, "jobs", errors, Parsers::job);
        Map<String, NpcData> npcs = loadDir(root, "npcs", errors, Parsers::npc);
        Map<String, ArenaData> arenas = loadDir(root, "arenas", errors, ContentPack::arena);
        Map<String, RuneDef> runes = loadDir(root, "runes", errors, Parsers::rune);
        Map<String, KeystoneDef> keystones = loadDir(root, "keystones", errors, Parsers::keystone);
        Map<String, com.thuvstu.hayatemod.core.content.model.Models.MaterialDef> materials =
                loadDir(root, "materials", errors, Parsers::material);
        Vocabulary vocabulary = loadSingle(root, "vocabulary/core.yaml", errors, Parsers::vocabulary);
        List<ReferenceEntry> reference =
                loadSingle(root, "balance/reference.yaml", errors, Parsers::reference);
        DamageTuning tuning = loadSingle(root, "balance/damage.yaml", errors, Parsers::damageTuning);
        Map<String, Ruleset> rulesets = loadDir(root, "rulesets", errors, Parsers::ruleset);
        var market = loadSingle(root, "economy/market.yaml", errors, Parsers::market);
        var mining = loadSingle(root, "life_skills/mining.yaml", errors, Parsers::mining);
        var fishing = loadSingle(root, "life_skills/fishing.yaml", errors, Parsers::fishing);
        var world = loadSingle(root, "world.yaml", errors, Parsers::worldLayout);
        Map<String, com.thuvstu.hayatemod.core.content.model.Models.StructTemplate> structures =
                loadDir(root, "structures", errors, Parsers::structTemplate);
        if (vocabulary == null) {
            errors.add(new ContentError("vocabulary/core.yaml", "<root>", "vocabulary is required"));
            vocabulary = new Vocabulary(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                    Set.of(), Set.of(), 3, 8, 12.0,
                    ExecutionLimits.defaults(),
                    com.thuvstu.hayatemod.core.content.model.Models.CombatLimits.defaults(),
                    com.thuvstu.hayatemod.core.content.model.Models.CastingLimits.defaults());
        }
        ContentSet set = new ContentSet(weapons, enemies, encounters, skills, loot, jobs, npcs, arenas,
                runes, keystones,
                vocabulary, reference != null ? reference : List.of(), tuning, rulesets, market, mining,
                fishing, world, structures, materials);
        return new LoadedPack(set, errors);
    }

    // ---- per-kind wiring ----

    @FunctionalInterface
    private interface FileParser<T> {
        T parse(Map<String, Object> map, String file, List<ContentError> errors);
    }

    private static <T> Map<String, T> loadDir(Path root, String dir, List<ContentError> errors,
            FileParser<T> parser) {
        Map<String, T> out = new LinkedHashMap<>();
        Path dirPath = root.resolve(dir);
        if (!Files.isDirectory(dirPath)) {
            return out;
        }
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath, "*.yaml")) {
            for (Path p : stream) {
                if (!Files.isDirectory(p)) {
                    files.add(p);
                }
            }
        } catch (IOException e) {
            errors.add(new ContentError(dir, "<root>", "cannot list directory: " + e.getMessage()));
            return out;
        }
        files.sort(null);
        for (Path p : files) {
            String rel = root.relativize(p).toString().replace('\\', '/');
            Map<String, Object> map = readMapping(p, rel, errors);
            if (map == null) {
                continue;
            }
            T value = parser.parse(map, rel, errors);
            if (value == null) {
                continue;
            }
            String id = idOf(map, rel, errors);
            if (id == null) {
                continue;
            }
            if (putUnique(out, id, value, rel, errors)) {
                // stored
            }
        }
        return out;
    }

    private static void loadSkills(Path root, List<ContentError> errors, Map<String, AbilityDef> out) {
        Path dirPath = root.resolve("skills");
        if (!Files.isDirectory(dirPath)) {
            return;
        }
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath, "*.yaml")) {
            for (Path p : stream) {
                if (!Files.isDirectory(p)) {
                    files.add(p);
                }
            }
        } catch (IOException e) {
            errors.add(new ContentError("skills", "<root>", "cannot list directory: " + e.getMessage()));
            return;
        }
        files.sort(null);
        for (Path p : files) {
            String rel = root.relativize(p).toString().replace('\\', '/');
            Map<String, Object> map = readMapping(p, rel, errors);
            if (map == null) {
                continue;
            }
            String id = idOf(map, rel, errors);
            if (id == null) {
                continue;
            }
            AbilityDef a = Parsers.ability(id, map, rel, "<root>", errors);
            if (a != null) {
                putUnique(out, id, a, rel, errors);
            }
        }
    }

    private static <T> T loadSingle(Path root, String rel, List<ContentError> errors, FileParser<T> parser) {
        Path p = root.resolve(rel);
        if (!Files.isRegularFile(p)) {
            errors.add(new ContentError(rel, "<root>", "required file is missing"));
            return null;
        }
        Map<String, Object> map = readMapping(p, rel, errors);
        if (map == null) {
            return null;
        }
        return parser.parse(map, rel, errors);
    }

    private static ArenaData arena(Map<String, Object> m, String file, List<ContentError> errors) {
        Maps.unknownKeys(m, Set.of("id", "name", "structure", "markers"), file, "<root>", errors);
        String id = Maps.reqStr(m, "id", file, "<root>", errors);
        String name = Maps.reqStr(m, "name", file, "<root>", errors);
        if (id == null || name == null) {
            return null;
        }
        return new ArenaData(id, name, Maps.optStr(m, "structure", "", file, "<root>", errors),
                Maps.optMap(m, "markers", file, "<root>", errors));
    }

    private static String idOf(Map<String, Object> map, String file, List<ContentError> errors) {
        Object id = map.get("id");
        if (id instanceof String s && !s.isBlank()) {
            return s;
        }
        errors.add(new ContentError(file, "id", "expected non-empty string id"));
        return null;
    }

    private static <T> boolean putUnique(Map<String, T> out, String id, T value, String file,
            List<ContentError> errors) {
        if (out.containsKey(id)) {
            errors.add(new ContentError(file, "id", "duplicate id '" + id + "'"));
            return false;
        }
        out.put(id, value);
        return true;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> readMapping(Path path, String rel, List<ContentError> errors) {
        LoaderOptions opts = new LoaderOptions();
        opts.setAllowDuplicateKeys(false);
        Yaml yaml = new Yaml(new SafeConstructor(opts));
        Object doc;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            doc = yaml.load(reader);
        } catch (YAMLException e) {
            errors.add(new ContentError(rel, "<root>", "YAML error: " + firstLine(e.getMessage())));
            return null;
        } catch (IOException e) {
            errors.add(new ContentError(rel, "<root>", "cannot read file: " + e.getMessage()));
            return null;
        }
        if (!(doc instanceof Map<?, ?> map)) {
            errors.add(new ContentError(rel, "<root>", "expected a YAML mapping at document root"));
            return null;
        }
        int before = errors.size();
        checkTree(map, rel, "<root>", errors,
                Collections.newSetFromMap(new IdentityHashMap<>()),
                Collections.newSetFromMap(new IdentityHashMap<>()));
        if (errors.size() != before) {
            return null;
        }
        return (Map<String, Object>) map;
    }

    /** Validate nested keys too, before parsers cast maps to Map<String, Object>. */
    private static void checkTree(Object value, String file, String loc, List<ContentError> errors,
            Set<Object> active, Set<Object> visited) {
        if (value instanceof Number number && !Double.isFinite(number.doubleValue())) {
            errors.add(new ContentError(file, loc, "expected finite number"));
            return;
        }
        if (!(value instanceof Map<?, ?>) && !(value instanceof List<?>)) {
            return;
        }
        if (active.contains(value)) {
            errors.add(new ContentError(file, loc, "recursive YAML alias is not allowed"));
            return;
        }
        if (!visited.add(value)) {
            return;
        }
        active.add(value);
        if (value instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    errors.add(new ContentError(file, loc, "non-string key in mapping"));
                } else {
                    checkTree(entry.getValue(), file, loc + "." + key, errors, active, visited);
                }
            }
        } else if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                checkTree(list.get(i), file, loc + "[" + i + "]", errors, active, visited);
            }
        }
        active.remove(value);
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "unknown YAML error";
        }
        int i = message.indexOf('\n');
        return i < 0 ? message : message.substring(0, i);
    }
}
