package com.thuvstu.hayatemod.core.content;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.thuvstu.hayatemod.core.TestContent;
import com.thuvstu.hayatemod.core.build.WeaponSkillMerger;

class EffectIdentityTest {
    @TempDir Path temp;
    private Map<String,Object> effect() { return new HashMap<>(Map.of("trigger","on_hit", "actions",List.of(Map.of("type","heal_self","amount",1)))); }
    @Test void parserBindsSourceToContentIdentityNotFilesystemPath() {
        var raw = Map.<String,Object>of("id","test:weapon", "name","Test", "family","sword", "rarity","rare", "item_level",1,
                "skills",Map.of("primary",Map.of("core","melee_thrust","effects",List.of(effect()))));
        var errors = new ArrayList<ContentError>();
        var a = Parsers.weapon(raw, "/first/path/card.yaml", errors);
        var b = Parsers.weapon(raw, "/other/root/renamed.yaml", errors);
        assertTrue(errors.isEmpty(), errors.toString());
        assertEquals(a.skills(), b.skills());
        var e = a.skills().get("primary").effects().getFirst();
        assertEquals("weapon:test:weapon", e.source());
        assertEquals("skills.primary/00000000", e.id());
    }
    @Test void runesRetainTheirOwnSourceAfterMerging() {
        var pack = ContentPipeline.load(TestContent.dir());
        var rune = pack.set().runes().get("solommo:spark_rune");
        var base = pack.set().weapons().get("solommo:cinder_aegis").skills().get("primary");
        var merged = WeaponSkillMerger.withRuneEffects(base, rune.effects());
        assertTrue(merged.effects().getFirst().source().startsWith("weapon:"));
        assertTrue(merged.effects().getLast().source().startsWith("rune:"));
    }
    @Test void sourceCannotBeSpoofedByYaml() {
        var raw = effect(); raw.put("source", "weapon:other");
        var errors = new ArrayList<ContentError>();
        Parsers.effect(raw,"test","effects",errors);
        assertTrue(errors.stream().anyMatch(e -> e.path().endsWith("source")));
    }
    @Test void explicitEmptyIdentityIsNotSilentlyReplaced() {
        var raw = effect(); raw.put("id", "");
        var errors = new ArrayList<ContentError>();
        Parsers.effect(raw,"test","effects",errors);
        assertFalse(errors.isEmpty());
    }
    @Test void fractionalPriorityIsRejected() {
        var raw = effect(); raw.put("priority", 1.5);
        var errors = new ArrayList<ContentError>();
        Parsers.effect(raw,"test","effects",errors);
        assertFalse(errors.isEmpty());
    }
    private ContentPipeline.Result edit(String old, String replacement) throws Exception {
        Path source = TestContent.dir();
        try (var paths = Files.walk(source)) {
            for (Path p : paths.toList()) {
                var dest = temp.resolve(source.relativize(p));
                if (Files.isDirectory(p)) Files.createDirectories(dest); else Files.copy(p,dest);
            }
        }
        Path file = temp.resolve("weapons/cinder_aegis.yaml");
        String text = Files.readString(file); assertTrue(text.contains(old));
        Files.writeString(file,text.replace(old,replacement));
        return ContentPipeline.load(temp);
    }
    @Test void duplicateIdsAcrossSlotsOfOneWeaponAreRejected() throws Exception {
        var result = edit("trigger: on_cast", "id: same\n        trigger: on_cast");
        assertFalse(result.ok());
        assertTrue(result.issues().stream().anyMatch(i -> i.code().equals("V01") && i.message().contains("duplicate")));
    }
    @Test void priorityOutsideSafetyRangeIsRejected() throws Exception {
        var result = edit("trigger: on_hit", "priority: 1001\n        trigger: on_hit");
        assertFalse(result.ok());
        assertTrue(result.issues().stream().anyMatch(i -> i.code().equals("V03")));
    }
    @Test void invalidIdentityTokenIsRejected() throws Exception {
        assertFalse(edit("trigger: on_hit", "id: 'contains spaces'\n        trigger: on_hit").ok());
    }
}
