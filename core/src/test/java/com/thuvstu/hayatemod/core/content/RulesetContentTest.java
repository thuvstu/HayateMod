package com.thuvstu.hayatemod.core.content;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.thuvstu.hayatemod.core.TestContent;

class RulesetContentTest {
    @TempDir Path temp;
    private ContentPipeline.Result load(String rules) throws Exception {
        Path source = TestContent.dir();
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path out = temp.resolve(source.relativize(path));
                if (Files.isDirectory(path)) Files.createDirectories(out); else Files.copy(path, out);
            }
        }
        Files.writeString(temp.resolve("rulesets/custom.yaml"), "id: test:custom\n" + rules);
        return ContentPipeline.load(temp);
    }
    @Test void rejectsZeroMultiplier() throws Exception {
        var result = load("enemy: {hp_mult: 0}\n");
        assertFalse(result.ok());
        assertTrue(result.issues().stream().anyMatch(i -> i.code().equals("V03")));
    }
    @Test void rejectsUnknownIndividualEnemy() throws Exception {
        var result = load("by_id:\n  test:missing:\n    enemy: {hp_mult: 2}\n");
        assertFalse(result.ok());
        assertTrue(result.issues().stream().anyMatch(i -> i.code().equals("V04")));
    }
    @Test void rejectsDuplicateTagOverrides() throws Exception {
        var result = load("tag_overrides:\n  - tag: rank:boss\n    enemy: {hp_mult: 2}\n  - tag: rank:boss\n    enemy: {hp_mult: 3}\n");
        assertFalse(result.ok());
        assertTrue(result.issues().stream().anyMatch(i -> i.code().equals("V01")));
    }
    @Test void rejectsMisspelledTags() throws Exception {
        var result = load("tag_overrides:\n  - tag: rank:bosss\n    enemy: {hp_mult: 2}\n");
        assertFalse(result.ok());
    }
    @Test void rejectsNullPartialOverrideRatherThanInheritingIt() throws Exception {
        var result = load("enemy: {hp_mult: null}\n");
        assertFalse(result.ok());
        assertFalse(result.loaderErrors().isEmpty());
    }
    @Test void rejectsUnknownOverrideKey() throws Exception {
        var result = load("by_id:\n  solommo:flame_golem:\n    rewards: {typo_mult: 2}\n");
        assertFalse(result.loaderErrors().isEmpty());
    }
}
