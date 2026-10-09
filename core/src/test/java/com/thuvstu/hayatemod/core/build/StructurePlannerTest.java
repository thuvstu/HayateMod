package com.thuvstu.hayatemod.core.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.thuvstu.hayatemod.core.content.ContentPack;
import com.thuvstu.hayatemod.core.content.model.Models.StructOp;
import com.thuvstu.hayatemod.core.content.model.Models.StructTemplate;

class StructurePlannerTest {
    @Test
    void materializesFills() {
        var template = new StructTemplate("test:box", "Box", List.of(
                new StructOp("fill", List.of(0, 0, 0), List.of(1, 0, 1), "minecraft:bedrock"),
                new StructOp("set", List.of(5, 5, 5), List.of(5, 5, 5), "minecraft:torch")));
        var blocks = StructurePlanner.materialize(template);
        assertEquals(4 + 1, blocks.size());
        assertTrue(blocks.contains(
                new StructurePlanner.PlacedBlock(1, 0, 1, "minecraft:bedrock")));
        assertTrue(blocks.contains(
                new StructurePlanner.PlacedBlock(5, 5, 5, "minecraft:torch")));
    }

    @Test
    void repoTemplatesValidate() {
        var pack = ContentPack.load(com.thuvstu.hayatemod.core.TestContent.dir());
        assertTrue(pack.ok(), "loader errors: " + pack.errors());
        assertTrue(pack.set().structures().containsKey("solommo:flame_arena"));
        assertTrue(pack.set().structures().containsKey("solommo:hamlet"));
        var issues = com.thuvstu.hayatemod.core.validate.Validator.validate(pack.set());
        var errors = issues.stream()
                .filter(i -> i.severity() == com.thuvstu.hayatemod.core.validate.Issue.Severity.ERROR)
                .toList();
        assertTrue(errors.isEmpty(), "validator errors: " + errors);
    }
}
