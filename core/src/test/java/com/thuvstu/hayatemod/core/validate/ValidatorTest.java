package com.thuvstu.hayatemod.core.validate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.thuvstu.hayatemod.core.content.ContentPack;
import com.thuvstu.hayatemod.core.validate.Issue.Severity;

class ValidatorTest {
    @Test
    void repoContentHasNoErrors() {
        ContentPack.LoadedPack pack = ContentPack.load(com.thuvstu.hayatemod.core.TestContent.dir());
        assertTrue(pack.ok(), "loader errors: " + pack.errors());
        List<Issue> issues = Validator.validate(pack.set());
        List<Issue> errors =
                issues.stream().filter(i -> i.severity() == Severity.ERROR).toList();
        assertTrue(errors.isEmpty(), "validator errors: " + errors);
    }

    @Test
    void badPackProducesExpectedCodes() {
        ContentPack.LoadedPack pack =
                ContentPack.load(com.thuvstu.hayatemod.core.TestContent.dir().getParent().resolve("core/src/test/resources/badpack"));
        assertTrue(pack.errors().stream().anyMatch(e -> e.toString().contains("unknown key")),
                "loader errors: " + pack.errors());
        // Validator runs on whatever parsed despite loader errors.
        List<Issue> issues = Validator.validate(pack.set());
        Set<String> codes =
                issues.stream().filter(i -> i.severity() == Severity.ERROR)
                        .map(Issue::code).collect(Collectors.toSet());
        for (String expected : List.of("V02", "V03", "V04", "V06", "V07", "V08", "V09", "V12")) {
            assertTrue(codes.contains(expected), "missing " + expected + " in " + codes);
        }
        assertEquals(8, codes.size(), "unexpected codes: " + codes);
    }
}
