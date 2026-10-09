package com.thuvstu.hayatemod.core.content;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlSafetyTest {
    @TempDir
    Path temp;

    @Test
    void rejectsDuplicateKeys() throws IOException {
        rejected("id: first\nid: second\n", "YAML error");
    }

    @Test
    void rejectsNestedNonStringKeys() throws IOException {
        rejected("skills:\n  primary:\n    42: invalid\n", "non-string key");
    }

    @Test
    void rejectsNonStringKeysInsideLists() throws IOException {
        rejected("effects:\n  - 42: invalid\n", "non-string key");
    }

    @Test
    void rejectsNonFiniteNumbers() throws IOException {
        for (String number : new String[] {".nan", ".inf", "-.inf"}) {
            rejected("mods:\n  damage: " + number + "\n", "finite number");
        }
    }

    @Test
    void rejectsRecursiveAlias() throws IOException {
        rejected("effects: &effects\n  - *effects\n", "recursive YAML alias");
    }

    @Test
    void allowsNonRecursiveAliases() throws IOException {
        Path file = temp.resolve("shared.yaml");
        Files.writeString(file, "primary: &skill\n  core: melee_thrust\nheavy: *skill\n");
        var errors = new ArrayList<ContentError>();
        assertNotNull(ContentPack.readMapping(file, "shared.yaml", errors));
        assertTrue(errors.isEmpty(), errors.toString());
    }

    @Test
    void rejectsArbitraryObjectTags() throws IOException {
        rejected("value: !!java.net.URL [https://example.com]\n", "YAML error");
    }

    private void rejected(String yaml, String message) throws IOException {
        Path file = temp.resolve("invalid.yaml");
        Files.writeString(file, yaml);
        var errors = new ArrayList<ContentError>();
        assertNull(ContentPack.readMapping(file, "invalid.yaml", errors));
        assertTrue(errors.stream().anyMatch(e -> e.toString().contains(message)), errors.toString());
    }
}
