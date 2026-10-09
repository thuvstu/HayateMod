package com.thuvstu.hayatemod.core.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Strict map accessors. Every type mismatch or unknown key becomes a
 * {@link ContentError} instead of an exception. Nullable returns mean
 * "already reported, skip downstream checks".
 */
public final class Maps {
    private Maps() {
    }

    public static void unknownKeys(Map<String, Object> map, Set<String> allowed, String file, String loc,
            List<ContentError> errors) {
        for (String key : map.keySet()) {
            if (!allowed.contains(key)) {
                errors.add(new ContentError(file, loc + "." + key, "unknown key '" + key + "'"));
            }
        }
    }

    public static String reqStr(Map<String, Object> map, String key, String file, String loc,
            List<ContentError> errors) {
        Object v = map.get(key);
        if (v instanceof String s) {
            return s;
        }
        errors.add(new ContentError(file, loc + "." + key, "expected string for '" + key + "'"));
        return null;
    }

    public static String optStr(Map<String, Object> map, String key, String def,
            String file, String loc, List<ContentError> errors) {
        return optional(map, key, String.class, def, file, loc, errors, "string");
    }

    public static Integer reqInt(Map<String, Object> map, String key, String file, String loc,
            List<ContentError> errors) {
        Object v = map.get(key);
        if (v instanceof Integer i) {
            return i;
        }
        errors.add(new ContentError(file, loc + "." + key, "expected int for '" + key + "'"));
        return null;
    }

    public static Double reqDouble(Map<String, Object> map, String key, String file, String loc,
            List<ContentError> errors) {
        Object v = map.get(key);
        if (v instanceof Number n && Double.isFinite(n.doubleValue())) {
            return n.doubleValue();
        }
        errors.add(new ContentError(file, loc + "." + key, "expected finite number for '" + key + "'"));
        return null;
    }

    public static double optDouble(Map<String, Object> map, String key, double def,
            String file, String loc, List<ContentError> errors) {
        if (!map.containsKey(key)) {
            return def;
        }
        Double value = reqDouble(map, key, file, loc, errors);
        return value == null ? def : value;
    }

    public static int optInt(Map<String, Object> map, String key, int def,
            String file, String loc, List<ContentError> errors) {
        return optional(map, key, Integer.class, def, file, loc, errors, "int");
    }

    public static boolean optBool(Map<String, Object> map, String key, boolean def,
            String file, String loc, List<ContentError> errors) {
        return optional(map, key, Boolean.class, def, file, loc, errors, "boolean");
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> reqMap(Map<String, Object> map, String key, String file, String loc,
            List<ContentError> errors) {
        Object v = map.get(key);
        if (v instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        errors.add(new ContentError(file, loc + "." + key, "expected mapping for '" + key + "'"));
        return null;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> optMap(Map<String, Object> map, String key,
            String file, String loc, List<ContentError> errors) {
        return optional(map, key, Map.class, Map.of(), file, loc, errors, "mapping");
    }

    public static List<Object> reqList(Map<String, Object> map, String key, String file, String loc,
            List<ContentError> errors) {
        Object v = map.get(key);
        if (v instanceof List<?> l) {
            return new ArrayList<>((List<Object>) l);
        }
        errors.add(new ContentError(file, loc + "." + key, "expected list for '" + key + "'"));
        return null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> optList(Map<String, Object> map, String key,
            String file, String loc, List<ContentError> errors) {
        return new ArrayList<>(optional(map, key, List.class, List.of(), file, loc, errors, "list"));
    }

    /** Only absence selects a default silently; explicit null is a type error. */
    private static <T> T optional(Map<String, Object> map, String key, Class<T> type, T def,
            String file, String loc, List<ContentError> errors, String expected) {
        if (!map.containsKey(key)) {
            return def;
        }
        Object value = map.get(key);
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        errors.add(new ContentError(file, loc + "." + key, "expected " + expected + " for '" + key + "'"));
        return def;
    }

    public static List<String> strList(List<Object> list, String file, String loc, List<ContentError> errors) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Object v = list.get(i);
            if (v instanceof String s) {
                out.add(s);
            } else {
                errors.add(new ContentError(file, loc + "[" + i + "]", "expected string"));
            }
        }
        return out;
    }
}
