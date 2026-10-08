package com.thuvstu.hayatemod.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class FormulaTest {
    private static double eval(String text, Map<String, Double> bindings) throws Exception {
        return Formula.parse(text).eval(bindings);
    }

    @Test
    void arithmeticAndPrecedence() throws Exception {
        assertEquals(7.0, eval("1+2*3", Map.of()));
        assertEquals(9.0, eval("(1+2)*3", Map.of()));
        assertEquals(2.5, eval("5/2", Map.of()));
        assertEquals(1.0, eval("5%2", Map.of()));
        assertEquals(-2.0, eval("-5+3", Map.of()));
        assertEquals(0.0, eval("1/0", Map.of()), "division by zero is contained");
    }

    @Test
    void bindingsAndFunctions() throws Exception {
        var b = Map.of("lastDamage", 20.0, "mana", 50.0, "var_rage", 3.0);
        assertEquals(10.0, eval("lastDamage*0.5", b));
        assertEquals(20.0, eval("10+mana*0.2", b));
        assertEquals(3.0, eval("max(var_rage, 1)", b));
        assertEquals(5.0, eval("clamp(var_rage*10, 0, 5)", b));
        assertEquals(1.0, eval("var_missing+1", Map.of()), "unknown vars default to 0");
    }

    @Test
    void rejectsGarbage() {
        assertThrows(Formula.FormulaException.class, () -> Formula.parse(""));
        assertThrows(Formula.FormulaException.class, () -> Formula.parse("1+"));
        assertThrows(Formula.FormulaException.class, () -> Formula.parse("system.exit()"));
        assertThrows(Formula.FormulaException.class, () -> Formula.parse("bogus*2"));
        assertThrows(Formula.FormulaException.class, () -> Formula.parse("min()"));
        assertThrows(Formula.FormulaException.class, () -> Formula.parse("clamp(1,2)"));
    }

    @Test
    void identifiersListed() throws Exception {
        var ids = Formula.identifiers("lastDamage*0.5+var_rage");
        assertTrue(ids.contains("lastDamage"));
        assertTrue(ids.contains("var_rage"));
        assertEquals(2, ids.size());
    }
}
