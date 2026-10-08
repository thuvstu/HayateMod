package com.thuvstu.hayatemod.tools;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import com.thuvstu.hayatemod.core.content.ContentPack;
import com.thuvstu.hayatemod.core.content.ContentSet;
import com.thuvstu.hayatemod.core.describe.Describer;

/**
 * {@code contentReport}: id usage index, unused vocabulary (§9 V14), weapon
 * tooltips. Usage: {@code contentReport <contentDir>}.
 */
public final class ReportMain {
    private ReportMain() {
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("usage: contentReport <contentDir>");
            System.exit(2);
        }
        ContentPack.LoadedPack pack = ContentPack.load(Path.of(args[0]));
        if (!pack.ok()) {
            for (var e : pack.errors()) {
                System.out.println("[LOAD-ERROR] " + e);
            }
            System.exit(1);
        }
        ContentSet set = pack.set();
        Map<String, List<String>> usedBy = new TreeMap<>();
        java.util.function.BiConsumer<String, String> use = (id, by) ->
                usedBy.computeIfAbsent(id, k -> new ArrayList<>()).add(by);
        for (var e : set.enemies().values()) {
            for (String s : e.skills()) {
                use.accept(s, "enemies:" + e.id());
            }
            use.accept(e.loot(), "enemies:" + e.id());
        }
        for (var e : set.encounters().values()) {
            use.accept(e.arenaTemplate(), "encounters:" + e.id());
            for (var p : e.phases()) {
                for (var t : p.timeline()) {
                    use.accept(t.ability(), "encounters:" + e.id() + ".timeline");
                }
                for (String a : p.onEnter()) {
                    use.accept(a, "encounters:" + e.id() + ".on_enter");
                }
            }
        }
        for (var t : set.loot().values()) {
            for (var d : t.direct()) {
                use.accept(d.item(), "loot:" + t.id());
            }
        }
        System.out.println("== counts ==");
        System.out.println("weapons=" + set.weapons().size() + " enemies=" + set.enemies().size()
                + " encounters=" + set.encounters().size() + " skills=" + set.skills().size()
                + " loot=" + set.loot().size() + " jobs=" + set.jobs().size()
                + " npcs=" + set.npcs().size() + " arenas=" + set.arenas().size()
                + " runes=" + set.runes().size() + " keystones=" + set.keystones().size());
        System.out.println("== dangling ids (referenced but undefined) ==");
        var defined = new TreeSet<String>();
        defined.addAll(set.weapons().keySet());
        defined.addAll(set.enemies().keySet());
        defined.addAll(set.encounters().keySet());
        defined.addAll(set.skills().keySet());
        defined.addAll(set.loot().keySet());
        defined.addAll(set.jobs().keySet());
        defined.addAll(set.npcs().keySet());
        defined.addAll(set.arenas().keySet());
        for (var e : usedBy.entrySet()) {
            if (!defined.contains(e.getKey())) {
                System.out.println(e.getKey() + " <- " + e.getValue());
            }
        }
        System.out.println("== unused vocabulary (V14) ==");
        var v = set.vocabulary();
        reportUnused("core", v.cores(), collectCores(set));
        reportUnused("trigger", v.triggers(), collectTriggers(set));
        reportUnused("condition", v.conditions(), collectConditions(set));
        reportUnused("action", v.actions(), collectActions(set));
        reportUnused("signal", v.signals(), collectSignals(set));
        System.out.println("== weapon tooltips ==");
        for (var w : set.weapons().values()) {
            for (String line : Describer.describeWeapon(w)) {
                System.out.println("  " + line);
            }
        }
    }

    private static void reportUnused(String kind, java.util.Set<String> vocab, java.util.Set<String> used) {
        var unused = new TreeSet<>(vocab);
        unused.removeAll(used);
        System.out.println(kind + ": unused=" + unused);
    }

    private static java.util.Set<String> collectCores(ContentSet set) {
        var out = new TreeSet<String>();
        for (var w : set.weapons().values()) {
            for (var s : w.skills().values()) {
                out.add(s.core());
            }
        }
        for (var a : set.skills().values()) {
            out.add(a.core());
        }
        for (var e : set.encounters().values()) {
            for (var a : e.abilities().values()) {
                out.add(a.core());
            }
        }
        return out;
    }

    private static java.util.Set<String> collectTriggers(ContentSet set) {
        var out = new TreeSet<String>();
        for (var w : set.weapons().values()) {
            for (var s : w.skills().values()) {
                for (var e : s.effects()) {
                    out.add(e.trigger());
                }
            }
        }
        return out;
    }

    private static java.util.Set<String> collectActions(ContentSet set) {
        var out = new TreeSet<String>();
        for (var w : set.weapons().values()) {
            for (var s : w.skills().values()) {
                for (var e : s.effects()) {
                    for (var a : e.actions()) {
                        out.add(a.type());
                    }
                }
            }
        }
        return out;
    }

    private static java.util.Set<String> collectConditions(ContentSet set) {
        var out = new TreeSet<String>();
        for (var w : set.weapons().values()) {
            for (var s : w.skills().values()) {
                for (var e : s.effects()) {
                    for (var c : e.conditions()) {
                        out.add(c.type());
                    }
                }
            }
        }
        for (var r : set.runes().values()) {
            for (var e : r.effects()) {
                for (var c : e.conditions()) {
                    out.add(c.type());
                }
            }
        }
        return out;
    }

    private static java.util.Set<String> collectSignals(ContentSet set) {
        var out = new TreeSet<String>();
        for (var e : set.encounters().values()) {
            for (var a : e.abilities().values()) {
                if (a.signal() != null && !a.signal().isEmpty()) {
                    out.add(a.signal());
                }
            }
        }
        for (var a : set.skills().values()) {
            if (a.signal() != null && !a.signal().isEmpty()) {
                out.add(a.signal());
            }
        }
        return out;
    }
}
