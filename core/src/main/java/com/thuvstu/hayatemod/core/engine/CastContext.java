package com.thuvstu.hayatemod.core.engine;

import java.util.Set;
import java.util.UUID;

/** Causal context carried by projectiles. Propagates through derived events (§6.6). */
public record CastContext(String skillId, int chainDepth, Set<String> preventRecursive, UUID owner,
        double damageMult, com.thuvstu.hayatemod.core.content.model.Models.BuildMods mods,
        com.thuvstu.hayatemod.core.content.model.Models.WeaponCard snapshot) {
    public CastContext {
        preventRecursive = Set.copyOf(preventRecursive);
    }

    public CastContext(String skillId, int chainDepth, Set<String> preventRecursive, UUID owner,
            double damageMult, com.thuvstu.hayatemod.core.content.model.Models.BuildMods mods) {
        this(skillId, chainDepth, preventRecursive, owner, damageMult, mods, null);
    }

    public CastContext withSnapshot(com.thuvstu.hayatemod.core.content.model.Models.WeaponCard card) {
        return new CastContext(skillId, chainDepth, preventRecursive, owner, damageMult, mods, card);
    }

    /** A proc creates one causal edge. The caller enforces the absolute depth ceiling. */
    public CastContext causedBy(String action) {
        var history = new java.util.HashSet<>(preventRecursive);
        history.add(action);
        return new CastContext(skillId, chainDepth + 1, history, owner, damageMult, mods, snapshot);
    }

    public CastContext child(Set<String> prevent, double mult) {
        return new CastContext(skillId, chainDepth + 1, prevent, owner, mult, mods, snapshot);
    }
}
