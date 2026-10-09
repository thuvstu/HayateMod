package com.thuvstu.hayatemod.rpg;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.google.gson.reflect.TypeToken;
import com.thuvstu.hayatemod.content.ContentHolder;
import com.thuvstu.hayatemod.core.content.model.Models.EnemyData;
import com.thuvstu.hayatemod.core.rules.RuleResolver;
import com.thuvstu.hayatemod.core.rules.RuleResolver.Multipliers;
import com.thuvstu.hayatemod.dungeon.EncounterRunner;
import com.thuvstu.hayatemod.save.SaveFiles;
import com.thuvstu.hayatemod.town.TownManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.slf4j.LoggerFactory;

/** World-wide solo difficulty. Spawn snapshots prevent switching/reloading reward exploits. */
public final class DifficultyState {
    private static final String PREFIX = "solommo_rules:";
    private static String selected = "solommo:normal";
    private static Path file;
    private static final Map<UUID, Long> combatUntil = new HashMap<>();
    private DifficultyState() { }

    public static void load(Path root) {
        file = root.resolve("hayatemod/difficulty.json");
        selected = "solommo:normal";
        combatUntil.clear();
        Map<String, String> data = SaveFiles.load(file, new TypeToken<Map<String, String>>() { }.getType(), "difficulty");
        if (data != null && data.get("ruleset") != null) selected = data.get("ruleset");
        if (ContentHolder.ready() && !ContentHolder.get().rulesets().containsKey(selected)) {
            LoggerFactory.getLogger("hayatemod/rules").warn("Missing saved ruleset {}; retained on disk, neutral rules until selection", selected);
        }
    }

    public static String selected() { return selected; }

    public static void markCombat(ServerPlayer player) {
        combatUntil.put(player.getUUID(), player.level().getServer().overworld().getGameTime() + 200);
    }

    public static boolean canUseTown(ServerPlayer player) {
        var server = player.level().getServer();
        return player.isAlive() && !player.isSpectator() && !EncounterRunner.isActive()
                && TownManager.isInTown(player)
                && combatUntil.getOrDefault(player.getUUID(), 0L) <= server.overworld().getGameTime();
    }

    public static boolean canChange(MinecraftServer server) {
        return !EncounterRunner.isActive() && server.getPlayerList().getPlayers().stream().allMatch(DifficultyState::canUseTown);
    }

    public static String change(MinecraftServer server, String id) {
        if (!ContentHolder.ready() || id.equals(RuleResolver.GLOBAL)
                || !ContentHolder.get().rulesets().containsKey(id)) return "不明な難易度: " + id;
        if (!canChange(server)) return "全員が街に戻り、戦闘終了から10秒待ってください（エンカウンター中は変更不可）";
        selected = id;
        if (file != null) SaveFiles.save(file, Map.of("ruleset", selected), "difficulty");
        return "難易度: " + selected + "（新しく出現する敵から適用）";
    }

    public static Multipliers resolve(EnemyData enemy) {
        if (!ContentHolder.ready() || !ContentHolder.get().rulesets().containsKey(selected)) return Multipliers.identity();
        return RuleResolver.resolve(ContentHolder.get().rulesets(), selected, enemy);
    }

    public static Multipliers resolveWild(String species) {
        if (!ContentHolder.ready() || !ContentHolder.get().rulesets().containsKey(selected)) return Multipliers.identity();
        return RuleResolver.resolve(ContentHolder.get().rulesets(), selected, "", Set.of("rank:normal", "species:" + species));
    }

    public static void bind(Entity entity, Multipliers value) {
        if (entity.getTags().stream().anyMatch(tag -> tag.startsWith(PREFIX))) return;
        entity.addTag(PREFIX + value.hp() + "," + value.dps() + "," + value.pity() + "," + value.materials());
    }

    public static Multipliers snapshot(Entity entity) {
        if (entity == null || entity.getTags().contains("solommo_summoned")) return Multipliers.identity();
        var tags = entity.getTags().stream().filter(tag -> tag.startsWith(PREFIX)).sorted().toList();
        if (tags.size() != 1) return Multipliers.identity(); // Legacy entities remain normal, never retroactively boosted.
        try {
            String[] parts = tags.get(0).substring(PREFIX.length()).split(",");
            if (parts.length != 4) return Multipliers.identity();
            return new Multipliers(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
        } catch (IllegalArgumentException ex) {
            return Multipliers.identity();
        }
    }
}
