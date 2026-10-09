package com.thuvstu.hayatemod.core.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import com.thuvstu.hayatemod.core.content.model.Models.EffectDef;

/** One ordering contract for execution, delayed continuations, descriptions and diagnostics. */
public final class EffectOrder {
    private EffectOrder() { }
    public record Key(int priority, String source, String id, String slot, int ordinal) { }
    public static final Key SYSTEM = new Key(0, "system", "", "", 0);
    // Preserve the traditional weapon-before-rune default; priority always wins first.
    private static int sourceKind(String source) {
        return source.startsWith("weapon:") ? 0 : source.startsWith("rune:") ? 1 : 2;
    }
    public static final Comparator<Key> COMPARATOR = Comparator.comparingInt(Key::priority)
            .thenComparingInt(k -> sourceKind(k.source())).thenComparing(Key::source)
            .thenComparing(Key::id).thenComparing(Key::slot).thenComparingInt(Key::ordinal);

    public static Key key(EffectDef effect, String skillId, int ordinal) {
        int colon = skillId.lastIndexOf(':');
        String source = effect.source().isEmpty() ? "weapon:" + (colon < 0 ? skillId : skillId.substring(0, colon)) : effect.source();
        String slot = colon < 0 ? skillId : skillId.substring(colon + 1);
        String id = effect.id().isEmpty() ? slot + "/" + String.format(Locale.ROOT, "%08d", ordinal) : effect.id();
        return new Key(effect.priority(), source, id, slot, ordinal);
    }

    public static List<EffectDef> ordered(List<EffectDef> effects, String skillId) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < effects.size(); i++) indices.add(i);
        indices.sort((a, b) -> COMPARATOR.compare(key(effects.get(a), skillId, a), key(effects.get(b), skillId, b)));
        return indices.stream().map(effects::get).toList();
    }
}
