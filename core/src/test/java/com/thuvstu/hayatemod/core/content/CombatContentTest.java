package com.thuvstu.hayatemod.core.content;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.thuvstu.hayatemod.core.TestContent;
import com.thuvstu.hayatemod.core.build.TagDeriver;
import com.thuvstu.hayatemod.core.describe.Describer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CombatContentTest {
    @TempDir
    Path temp;

    @Test
    void newWeaponsHaveValidatedDescriptionsDerivedTagsAndLoot() {
        var loaded = ContentPipeline.load(TestContent.dir());
        assertTrue(loaded.ok(), loaded.issues().toString());
        var set = loaded.set();
        var guardian = set.weapons().get("solommo:cinder_aegis");
        String text = String.join("\n", Describer.describeWeapon(guardian));
        for (String part : List.of("発動成功時", "耐久90", "5秒", "60%", "6秒", "加算なし")) {
            assertTrue(text.contains(part), "missing " + part + " in " + text);
        }
        assertTrue(TagDeriver.itemTags(guardian).containsAll(Set.of("delivery:self", "utility:shield", "utility:healing")));
        var reaver = set.weapons().get("solommo:echo_reaver");
        assertTrue(TagDeriver.itemTags(reaver).contains("utility:cooldown"));
        String reaverText = String.join("\n", Describer.describeWeapon(reaver));
        assertTrue(reaverText.contains("2秒短縮"));
        assertTrue(reaverText.contains("持ち越しなし"));
        assertTrue(set.loot().get("solommo:colossus_loot").direct().stream()
                .anyMatch(d -> d.item().equals(guardian.id())));
        assertTrue(set.loot().get("solommo:slag_brute_loot").direct().stream()
                .anyMatch(d -> d.item().equals(reaver.id())));
    }

    @Test
    void shieldActionRequiresDurationAndAmount() {
        var errors = new ArrayList<ContentError>();
        Parsers.effect(Map.of("trigger", "on_cast", "actions", List.of(Map.of("type", "grant_shield"))),
                "weapon", "effects[0]", errors);
        assertTrue(errors.stream().anyMatch(e -> e.path().endsWith("duration_ticks")));
        assertTrue(errors.stream().anyMatch(e -> e.path().endsWith("amount")));
    }

    @Test
    void healingConversionRequiresNumericRatioAndIntegralDuration() {
        var errors = new ArrayList<ContentError>();
        Parsers.effect(Map.of("trigger", "on_cast", "actions", List.of(Map.of("type", "heal_with_shield",
                "amount", 100, "shield_ratio", "half", "duration_ticks", 10.5))),
                "weapon", "effects[0]", errors);
        assertEquals(2, errors.size());
    }

    @Test
    void newFieldsCannotBeSilentlyIgnoredByOtherActions() {
        var errors = new ArrayList<ContentError>();
        Parsers.effect(Map.of("trigger", "on_hit", "actions", List.of(Map.of("type", "heal_self",
                "amount", 10, "shield_ratio", 0.5, "duration_ticks", 100))), "weapon", "effects[0]", errors);
        assertEquals(2, errors.size());
    }

    @Test
    void rejectsOutOfRangeShieldRatio() throws IOException {
        rejectEdit("weapons/cinder_aegis.yaml", "shield_ratio: 0.6", "shield_ratio: 1.1", "V03");
    }

    @Test
    void rejectsOversizedShield() throws IOException {
        rejectEdit("weapons/cinder_aegis.yaml", "amount: 90", "amount: 201", "V03");
    }

    @Test
    void rejectsNonPositiveDuration() throws IOException {
        rejectEdit("weapons/cinder_aegis.yaml", "duration_ticks: 100", "duration_ticks: 0", "V03");
    }

    @Test
    void rejectsOverlongDuration() throws IOException {
        rejectEdit("weapons/cinder_aegis.yaml", "duration_ticks: 100", "duration_ticks: 1201", "V03");
    }

    @Test
    void rejectsCooldownReductionAboveVocabularyCap() throws IOException {
        rejectEdit("weapons/echo_reaver.yaml", "amount: 2", "amount: 31", "V03");
    }

    @Test
    void rejectsNonexistentCooldownSlot() throws IOException {
        // Make a syntactically permitted ref point at a slot this weapon does not define.
        Path root = copyPack();
        Path file = root.resolve("weapons/echo_reaver.yaml");
        String text = Files.readString(file).replace("ref: special", "ref: heavy");
        int start = text.indexOf("  heavy:");
        assertTrue(start >= 0, "heavy slot not found");
        int end = text.indexOf("design_note:", start);
        assertTrue(end >= 0, "design_note not found");
        Files.writeString(file, text.substring(0, start) + text.substring(end));
        var result = ContentPipeline.load(root);
        assertFalse(result.ok());
        assertTrue(result.issues().stream().anyMatch(i -> i.code().equals("V04")), result.issues().toString());
    }

    @Test
    void rejectsFormulaForFixedAmountRecovery() throws IOException {
        rejectEdit("weapons/cinder_aegis.yaml", "amount: 90", "amount: 90\n            formula: 'mana*100'", "V06");
    }

    @Test
    void rejectsAreaScopedOwnerRecovery() throws IOException {
        rejectEdit("weapons/cinder_aegis.yaml", "- trigger: on_cast",
                "- trigger: on_cast\n        scope: area\n        radius: 4", "V06");
    }

    @Test
    void rejectsInvalidCombatLimits() throws IOException {
        rejectEdit("vocabulary/core.yaml", "max_shield_amount: 200", "max_shield_amount: 0", "V03");
    }

    @Test
    void failedEffectReloadKeepsWorkingContent() throws IOException {
        var repository = new ContentRepository();
        assertTrue(repository.reload(TestContent.dir()).ok());
        var before = repository.current();
        Path root = copyPack();
        Path file = root.resolve("weapons/cinder_aegis.yaml");
        Files.writeString(file, Files.readString(file).replace("duration_ticks: 100", "duration_ticks: null"));
        assertFalse(repository.reload(root).ok());
        assertSame(before, repository.current());
    }

    private void rejectEdit(String path, String oldText, String replacement, String code) throws IOException {
        Path root = copyPack();
        Path file = root.resolve(path);
        String text = Files.readString(file);
        assertTrue(text.contains(oldText));
        Files.writeString(file, text.replace(oldText, replacement));
        var result = ContentPipeline.load(root);
        assertTrue(result.loaderErrors().isEmpty(), result.loaderErrors().toString());
        assertFalse(result.ok());
        assertTrue(result.issues().stream().anyMatch(i -> i.code().equals(code)), result.issues().toString());
    }

    private Path copyPack() throws IOException {
        Path source = TestContent.dir();
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path destination = temp.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(path, destination);
                }
            }
        }
        return temp;
    }
}
