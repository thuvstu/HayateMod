package com.thuvstu.hayatemod.core.rules;

import com.thuvstu.hayatemod.core.content.model.Models.WeaponCard;

/** Shared deterministic recipe prices; tools must not invent cheaper acquisition paths. */
public final class CraftRules {
    private CraftRules() { }
    public record Cost(int materials, int pity) { }
    public static Cost cost(WeaponCard card) {
        if (card.itemLevel() < 1) throw new IllegalArgumentException("invalid item level");
        return new Cost(Math.multiplyExact(card.itemLevel(), 3), card.rarity().equals("unique") ? 10 : 0);
    }
    public static int salvageMaterials(WeaponCard card) { return Math.max(1, card.itemLevel() / 5 + 1); }
}
