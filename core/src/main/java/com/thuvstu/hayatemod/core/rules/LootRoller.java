package com.thuvstu.hayatemod.core.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.function.IntUnaryOperator;
import com.thuvstu.hayatemod.core.content.model.Models.LootTable;
import com.thuvstu.hayatemod.core.content.model.Models.DirectDrop;

/** One exclusive equipment draw, independent rune rolls, unconditional persistent pity (§5.10). */
public final class LootRoller {
    private LootRoller() { }
    public record Material(String id, int count) { }
    public record Roll(String equipment, int pity, List<Material> materials, List<String> runes) {
        public Roll { materials = List.copyOf(materials); runes = List.copyOf(runes); }
    }

    public static String equipment(List<DirectDrop> drops, double draw) {
        if (!Double.isFinite(draw) || draw < 0 || draw >= 1) throw new IllegalArgumentException("draw outside [0,1)");
        double total = 0;
        String selected = "";
        for (DirectDrop drop : drops) {
            if (!Double.isFinite(drop.p()) || drop.p() < 0 || drop.p() > 1) throw new IllegalArgumentException("invalid drop rate");
            total += drop.p();
            if (selected.isEmpty() && draw < total) selected = drop.item();
        }
        if (total > 1 + 1e-9) throw new IllegalArgumentException("exclusive rates exceed 1");
        return selected;
    }

    public static Roll roll(LootTable table, RuleResolver.Multipliers rules,
            DoubleSupplier random, IntUnaryOperator boundedRandom) {
        String equipment = equipment(table.direct(), random.getAsDouble());
        int pity = RuleResolver.rewardCount(table.perKill(), rules.pity(), true);
        var materials = new ArrayList<Material>();
        for (var material : table.materials()) {
            if (material.min() < 0 || material.max() < material.min() || material.max() > 4096) {
                throw new IllegalArgumentException("material range outside [0,4096]");
            }
            int count = material.min() + boundedRandom.applyAsInt(material.max() - material.min() + 1);
            materials.add(new Material(material.item(), RuleResolver.rewardCount(count, rules.materials(), false)));
        }
        var runes = new ArrayList<String>();
        for (var rune : table.runes()) if (random.getAsDouble() < rune.p()) runes.add(rune.id());
        return new Roll(equipment, pity, materials, runes);
    }
}
