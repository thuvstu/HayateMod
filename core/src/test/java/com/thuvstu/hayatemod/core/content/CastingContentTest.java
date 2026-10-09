package com.thuvstu.hayatemod.core.content;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.thuvstu.hayatemod.core.TestContent;
import com.thuvstu.hayatemod.core.build.TagDeriver;
import com.thuvstu.hayatemod.core.describe.Describer;

class CastingContentTest {
    @TempDir Path temp;
    private ContentPipeline.Result edit(String path, String old, String value) throws Exception {
        Path source = TestContent.dir();
        try (var paths = Files.walk(source)) {
            for (Path p : paths.toList()) {
                Path to = temp.resolve(source.relativize(p));
                if (Files.isDirectory(p)) Files.createDirectories(to); else Files.copy(p, to);
            }
        }
        Path p = temp.resolve(path);
        String text = Files.readString(p);
        assertTrue(text.contains(old));
        Files.writeString(p, text.replace(old, value));
        return ContentPipeline.load(temp);
    }
    @Test void negativeCastingTimeIsRejected() throws Exception {
        assertFalse(edit("weapons/ashen_chant.yaml", "cast_time: 1.5", "cast_time: -1").ok());
    }
    @Test void oversizedCastingTimeIsRejected() throws Exception {
        assertFalse(edit("weapons/ashen_chant.yaml", "cast_time: 1.5", "cast_time: 31").ok());
    }
    @Test void invalidBoostDurationIsRejected() throws Exception {
        assertFalse(edit("weapons/ashen_chant.yaml", "duration_ticks: 200", "duration_ticks: 0").ok());
    }
    @Test void danglingBoostSlotIsRejected() throws Exception {
        assertFalse(edit("weapons/ashen_chant.yaml", "ref: special", "ref: nonexistent").ok());
    }
    @Test void missingBoostDurationIsRejected() throws Exception {
        var r = edit("weapons/ashen_chant.yaml", "duration_ticks: 200", "");
        assertFalse(r.ok());
        assertFalse(r.loaderErrors().isEmpty());
    }
    @Test void unboundedVocabularyLimitIsRejected() throws Exception {
        assertFalse(edit("vocabulary/core.yaml", "max_cast_seconds: 30", "max_cast_seconds: 61").ok());
    }
    @Test void realCardDescribesCastingAndOneShotExpiration() {
        var pack = ContentPipeline.load(TestContent.dir());
        assertTrue(pack.ok());
        var card = pack.set().weapons().get("solommo:ashen_chant");
        var lines = String.join("\n", Describer.describeWeapon(card));
        assertTrue(lines.contains("詠唱1.5秒"));
        assertTrue(lines.contains("10秒以内に1回"));
        assertTrue(TagDeriver.itemTags(card).contains("utility:casting"));
        assertTrue(pack.set().loot().get("solommo:pyre_watcher_loot").direct().stream().anyMatch(d -> d.item().equals(card.id())));
    }
}
