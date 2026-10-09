package com.thuvstu.hayatemod.core.engine;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import com.thuvstu.hayatemod.core.content.model.Models.*;

/** Capture an accepted loadout once; delayed delivery must not re-read mutable equipment/content. */
public final class SkillSnapshot {
    private SkillSnapshot() { }
    public static WeaponCard copy(WeaponCard card) {
        Map<String, SkillDef> skills = new TreeMap<>();
        card.skills().forEach((slot, skill) -> skills.put(slot, new SkillDef(skill.core(), Map.copyOf(skill.mods()),
                skill.effects().stream().map(e -> new EffectDef(e.trigger(), List.copyOf(e.conditions()),
                        e.actions().stream().map(a -> new ActionDef(a.type(), a.count(), a.damageMult(), a.target(),
                                a.radius(), List.copyOf(a.inheritTags()), a.distance(), a.amount(), a.status(),
                                a.effect(), a.ref(), a.formula(), a.durationTicks(), a.shieldRatio())).toList(),
                        List.copyOf(e.preventRecursive()), e.maxChainDepth(), e.scope(), e.radius(), e.id(), e.source(), e.priority())).toList())));
        return new WeaponCard(card.id(), card.name(), card.family(), card.rarity(), card.itemLevel(),
                List.copyOf(card.tagsExtra()), List.copyOf(card.roleHint()), Map.copyOf(skills), card.designNote());
    }
}
