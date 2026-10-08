package com.thuvstu.hayatemod.forge;

import java.util.LinkedHashMap;
import java.util.Map;

import com.thuvstu.hayatemod.content.ContentHolder;
import com.thuvstu.hayatemod.core.content.model.Models.SkillDef;
import com.thuvstu.hayatemod.core.content.model.Models.WeaponCard;

/**
 * Assembles runtime weapon cards for forged one-offs from donor skills.
 * Donors must exist in the Content Pack (checked at forge time).
 */
public final class ForgedCards {
    private ForgedCards() {
    }

    public static WeaponCard assemble(String id) {
        ForgedStore.Spec spec = ForgedStore.get(id);
        if (spec == null || !ContentHolder.ready()) {
            return null;
        }
        Map<String, WeaponCard> weapons = ContentHolder.get().weapons();
        WeaponCard style = weapons.get(spec.style);
        WeaponCard base = weapons.get(spec.base);
        if (style == null || base == null) {
            return null;
        }
        Map<String, SkillDef> skills = new LinkedHashMap<>();
        SkillDef primary = base.skills().get("primary");
        if (primary != null) {
            skills.put("primary", primary);
        }
        SkillDef special = donorSkill(weapons, spec.special, "special");
        if (special != null) {
            skills.put("special", special);
        } else if (base.skills().get("special") != null) {
            skills.put("special", base.skills().get("special"));
        }
        SkillDef heavy = donorSkill(weapons, spec.heavy, "heavy");
        if (heavy != null) {
            skills.put("heavy", heavy);
        } else if (base.skills().get("heavy") != null) {
            skills.put("heavy", base.skills().get("heavy"));
        }
        if (skills.isEmpty()) {
            return null;
        }
        String name = spec.name != null && !spec.name.isBlank() ? spec.name : style.name();
        return new WeaponCard(id, name, style.family(), style.rarity(), spec.itemLevel,
                style.tagsExtra(), style.roleHint(), Map.copyOf(skills), "鍛造品");
    }

    private static SkillDef donorSkill(Map<String, WeaponCard> weapons, String donorId,
            String slot) {
        if (donorId == null || donorId.isEmpty()) {
            return null;
        }
        WeaponCard donor = weapons.get(donorId);
        return donor != null ? donor.skills().get(slot) : null;
    }
}
