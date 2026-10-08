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

    public static String optStr(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return v instanceof String s ? s : def;
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
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        errors.add(new ContentError(file, loc + "." + key, "expected number for '" + key + "'"));
        return null;
    }

    public static double optDouble(Map<String, Object> map, String key, double def) {
        Object v = map.get(key);
        return v instanceof Number n ? n.doubleValue() : def;
    }

    public static int optInt(Map<String, Object> map, String key, int def) {
        Object v = map.get(key);
        return v instanceof Integer i ? i : def;
    }

    public static boolean optBool(Map<String, Object> map, String key, boolean def) {
        Object v = map.get(key);
        return v instanceof Boolean b ? b : def;
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
    public static Map<String, Object> optMap(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
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

    public static List<Object> optList(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof List<?> l) {
            return new ArrayList<>((List<Object>) l);
        }
        return List.of();
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
