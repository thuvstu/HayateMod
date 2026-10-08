package com.thuvstu.hayatemod.core.rules;

import java.util.List;

import com.thuvstu.hayatemod.core.content.model.Models.PhaseDef;

/**
 * Pure phase resolution for encounters (§5.12). {@code hp_range: [high, low]}
 * owns fractions in {@code (low, high]}; the first matching phase wins.
 */
public final class PhaseLogic {
    private PhaseLogic() {
    }

    public static PhaseDef phaseFor(List<PhaseDef> phases, double hpFraction) {
        double pct = hpFraction * 100.0;
        for (PhaseDef p : phases) {
            if (pct <= p.hpFrom() && pct > p.hpTo()) {
                return p;
            }
        }
        return phases.isEmpty() ? null : phases.get(phases.size() - 1);
    }
}
