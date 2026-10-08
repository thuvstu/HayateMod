package com.thuvstu.hayatemod.core.build;

import java.util.ArrayList;
import java.util.List;

import com.thuvstu.hayatemod.core.content.model.Models.EffectDef;
import com.thuvstu.hayatemod.core.content.model.Models.SkillDef;

/**
 * Merges socketed rune effects into the cast skill. Runes augment whichever
 * skill is being cast (special or primary).
 */
public final class WeaponSkillMerger {
    private WeaponSkillMerger() {
    }

    public static SkillDef withRuneEffects(SkillDef base, List<EffectDef> runeEffects) {
        if (runeEffects.isEmpty()) {
            return base;
        }
        List<EffectDef> merged = new ArrayList<>(base.effects());
        merged.addAll(runeEffects);
        return new SkillDef(base.core(), base.mods(), List.copyOf(merged));
    }
}
