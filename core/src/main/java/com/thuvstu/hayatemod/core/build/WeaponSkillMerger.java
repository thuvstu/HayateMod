package com.thuvstu.hayatemod.core.build;

import java.util.ArrayList;
import java.util.List;

import com.thuvstu.hayatemod.core.content.model.Models.EffectDef;
import com.thuvstu.hayatemod.core.content.model.Models.WeaponCard;
import com.thuvstu.hayatemod.core.content.model.Models.SkillDef;

/**
 * Merges socketed rune effects into the cast skill. Runes augment whichever
 * skill is being cast (special or primary).
 */
public final class WeaponSkillMerger {
    private WeaponSkillMerger() {
    }

    /** Attach passive rune effects once, not once per skill slot. */
    public static WeaponCard withPassiveRunes(WeaponCard card, List<EffectDef> effects) {
        if (effects.isEmpty() || card.skills().isEmpty()) return card;
        String slot = card.skills().containsKey("primary") ? "primary" : card.skills().keySet().stream().sorted().findFirst().orElseThrow();
        var skills = new java.util.TreeMap<>(card.skills());
        skills.put(slot, withRuneEffects(skills.get(slot), effects));
        return new WeaponCard(card.id(), card.name(), card.family(), card.rarity(), card.itemLevel(),
                card.tagsExtra(), card.roleHint(), java.util.Map.copyOf(skills), card.designNote());
    }

    public static SkillDef withRuneEffects(SkillDef base, List<EffectDef> runeEffects) {
        if (base == null || runeEffects.isEmpty()) {
            return base;
        }
        List<EffectDef> merged = new ArrayList<>(base.effects());
        merged.addAll(runeEffects);
        return new SkillDef(base.core(), base.mods(), List.copyOf(merged));
    }
}
