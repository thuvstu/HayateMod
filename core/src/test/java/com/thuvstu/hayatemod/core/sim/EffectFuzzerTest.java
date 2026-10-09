package com.thuvstu.hayatemod.core.sim;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.thuvstu.hayatemod.core.TestContent;
import com.thuvstu.hayatemod.core.content.ContentPipeline;

class EffectFuzzerTest {
    @Test void seededReportsAreReproducibleAndWithinBudgets() {
        var set = ContentPipeline.load(TestContent.dir()).set();
        var a = EffectFuzzer.run(set, 42, 24, 60);
        var b = EffectFuzzer.run(set, 42, 24, 60);
        assertEquals(a, b);
        assertTrue(a.effectsPerTick().max() <= set.vocabulary().executionLimits().effectsPerTick());
        assertTrue(a.actionsPerTick().max() <= set.vocabulary().executionLimits().actionsPerTick());
        assertTrue(a.tasksPerTick().max() <= set.vocabulary().executionLimits().tasksPerTick());
        assertTrue(a.chainDepth().max() <= 3);
        assertTrue(a.actionsPerTick().max() > 0);
    }
    @Test void oversizedRunsFailBeforeAllocatingTrials() {
        var set = ContentPipeline.load(TestContent.dir()).set();
        assertThrows(IllegalArgumentException.class, () -> EffectFuzzer.run(set, 42, 1000000, 2000));
        assertThrows(IllegalArgumentException.class, () -> EffectFuzzer.run(set, 42, 1, 0));
    }
}
