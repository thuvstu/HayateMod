package com.thuvstu.hayatemod.core.engine;

import java.util.Set;
import java.util.UUID;

/** Causal context carried by projectiles. Propagates through derived events (§6.6). */
public record CastContext(String skillId, int chainDepth, Set<String> preventRecursive, UUID owner,
        double damageMult, com.thuvstu.hayatemod.core.content.model.Models.BuildMods mods) {
    public CastContext child(Set<String> prevent, double mult) {
        return new CastContext(skillId, chainDepth + 1, prevent, owner, mult, mods);
    }
}
