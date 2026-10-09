package com.thuvstu.hayatemod.build;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.thuvstu.hayatemod.codex.CodexStore;
import com.thuvstu.hayatemod.content.ContentHolder;
import com.thuvstu.hayatemod.core.build.TagDeriver;
import com.thuvstu.hayatemod.item.WeaponStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Build signature and candidate matching (§5.8). Single implementation shared
 * by the {@code /solommo build} chat readout, the codex screen and the
 * loadout screen.
 */
public final class BuildOps {
    private BuildOps() {
    }

    public record Candidate(String id, String name, int match) {
    }

    /** Full match set: signature plus delivery tags from the active cores. */
    public static Set<String> matchSet(ServerPlayer player) {
        var keys = PlayerBuilds.keystones(player.getUUID());
        var card = WeaponStack.resolve(player.getMainHandItem());
        var cores = card != null ? TagDeriver.skillCoresOf(card) : Set.<String>of();
        List<String> runes = card != null
                ? new ArrayList<>(WeaponStack.getRunes(player.getMainHandItem()))
                : List.of();
        var sig = TagDeriver.buildTags("solommo:knight", cores,
                runes.stream().filter(s -> !s.isEmpty()).toList(), keys);
        return TagDeriver.matchSet(sig, cores);
    }

    public static String signatureLine(ServerPlayer player) {
        var keys = PlayerBuilds.keystones(player.getUUID());
        var card = WeaponStack.resolve(player.getMainHandItem());
        var cores = card != null ? TagDeriver.skillCoresOf(card) : Set.<String>of();
        List<String> runes = card != null
                ? new ArrayList<>(WeaponStack.getRunes(player.getMainHandItem()))
                : List.of();
        return TagDeriver.buildTags("solommo:knight", cores,
                runes.stream().filter(s -> !s.isEmpty()).toList(), keys).toString();
    }

    public static List<Candidate> candidates(ServerPlayer player) {
        List<Candidate> out = new ArrayList<>();
        if (!ContentHolder.ready()) {
            return out;
        }
        Set<String> matched = matchSet(player);
        for (String id : CodexStore.list(player.getUUID())) {
            var card = ContentHolder.get().weapons().get(id);
            if (card == null) {
                continue;
            }
            out.add(new Candidate(id, card.name(),
                    TagDeriver.matchCount(TagDeriver.itemTags(card), matched)));
        }
        return out;
    }

    public static void printBuild(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("[solommo] ビルド: " + signatureLine(player)));
        for (Candidate c : candidates(player)) {
            player.sendSystemMessage(Component.literal("  " + c.name() + " : 一致" + c.match()));
        }
    }
}
