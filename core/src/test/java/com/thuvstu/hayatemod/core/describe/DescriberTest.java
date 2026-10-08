package com.thuvstu.hayatemod.core.describe;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.thuvstu.hayatemod.core.content.ContentPack;

class DescriberTest {
    @Test
    void emberBranchTooltip() {
        ContentPack.LoadedPack pack = ContentPack.load(com.thuvstu.hayatemod.core.TestContent.dir());
        assertTrue(pack.ok(), "loader errors: " + pack.errors());
        List<String> lines =
                Describer.describeWeapon(pack.set().weapons().get("solommo:ember_branch"));
        String joined = String.join("\n", lines);
        for (String expected : List.of("枝分かれする熾火の投槍", "ユニーク", "IL20", "突き", "単発投射",
                "命中時", "燃焼", "2発", "0.45", "CD3秒", "スタミナ")) {
            assertTrue(joined.contains(expected), "missing '" + expected + "' in:\n" + joined);
        }
    }
}
