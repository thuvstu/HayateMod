package com.thuvstu.hayatemod.core.build;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.thuvstu.hayatemod.core.content.model.Models.ActionDef;
import com.thuvstu.hayatemod.core.content.model.Models.EffectDef;
import com.thuvstu.hayatemod.core.content.model.Models.SkillDef;
import com.thuvstu.hayatemod.core.content.model.Models.WeaponCard;

/**
 * Tag derivation (§6.5) and MVP build signatures (§5.8).
 *
 * <pre>
 * item_tags = skill core delivery + mods element + effect action/status tags + tags_extra
 * build_tags = job + skill cores + rune ids + keystone ids
 * </pre>
 */
public final class TagDeriver {
    private TagDeriver() {
    }

    public static String deliveryForCore(String core) {
        return switch (core) {
            case "melee_thrust", "heavy_slam" -> "delivery:melee";
            case "projectile_single" -> "delivery:projectile";
            case "summon_minions" -> "delivery:summon";
            default -> "delivery:unknown";
        };
    }

    public static Set<String> itemTags(WeaponCard card) {
        Set<String> tags = new TreeSet<>(card.tagsExtra());
        for (SkillDef skill : card.skills().values()) {
            tags.add(deliveryForCore(skill.core()));
            Object element = skill.mods().get("element");
            if (element instanceof String e) {
                tags.add("element:" + e);
            }
            for (EffectDef eff : skill.effects()) {
                for (ActionDef a : eff.actions()) {
                    if (a.type().equals("spawn_projectiles")) {
                        tags.add("delivery:projectile");
                    }
                }
                for (var c : eff.conditions()) {
                    if (c.type().equals("target_has_status") && c.status() != null) {
                        tags.add("status:" + c.status());
                    }
                }
            }
        }
        return tags;
    }

    public static Set<String> buildTags(String jobId, Collection<String> skillCores,
            Collection<String> runeIds, Collection<String> keystoneIds) {
        Set<String> tags = new TreeSet<>();
        if (jobId != null && !jobId.isEmpty()) {
            tags.add(jobId);
        }
        tags.addAll(skillCores);
        tags.addAll(runeIds);
        tags.addAll(keystoneIds);
        return tags;
    }

    /** Candidate match count (§5.8: a hint, never an optimum claim). */
    public static int matchCount(Set<String> itemTags, Set<String> buildTags) {
        int n = 0;
        for (String t : itemTags) {
            if (buildTags.contains(t)) {
                n++;
            }
        }
        return n;
    }

    /**
     * Match set: the build signature plus delivery tags derived from the
     * active skill cores, so data-side {@code delivery:*} tags can meet them.
     */
    public static Set<String> matchSet(Set<String> buildTags, Collection<String> skillCores) {
        Set<String> out = new TreeSet<>(buildTags);
        for (String core : skillCores) {
            out.add(deliveryForCore(core));
        }
        return out;
    }

    public static Set<String> skillCoresOf(WeaponCard card) {
        Set<String> cores = new TreeSet<>();
        for (Map.Entry<String, SkillDef> e : card.skills().entrySet()) {
            cores.add(e.getValue().core());
        }
        return cores;
    }
}
