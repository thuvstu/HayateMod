package com.thuvstu.hayatemod.build;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.thuvstu.hayatemod.content.ContentHolder;
import com.thuvstu.hayatemod.core.content.model.Models.BuildMods;
import com.thuvstu.hayatemod.core.content.model.Models.EffectDef;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-player build state (Phase 5): equipped keystones (max 3) and named
 * loadouts. Persisted as JSON in the world folder, next to the codex.
 */
public final class PlayerBuilds {
    private static final Logger LOGGER = LoggerFactory.getLogger("hayatemod/build");
    private static final Gson GSON = new Gson();
    private static final int MAX_KEYSTONES = 3;

    private static Path file;
    private static final Map<String, StoredBuild> DATA = new HashMap<>();

    private PlayerBuilds() {
    }

    static final class StoredBuild {
        List<String> keystones = new ArrayList<>();
        List<String> ownedKeystones = new ArrayList<>();
        Map<String, Loadout> loadouts = new HashMap<>();
        int sp;
        int spDmg;
        int spCd;
        int spMelee;
    }

    public static final class Loadout {
        public String weapon = "";
        public List<String> keystones = new ArrayList<>();
    }

    public static void load(Path worldRoot) {
        file = worldRoot.resolve("hayatemod/builds.json");
        DATA.clear();
        Map<String, StoredBuild> loaded = com.thuvstu.hayatemod.save.SaveFiles.load(file,
                new TypeToken<Map<String, StoredBuild>>() {
                }.getType(),
                "build");
        if (loaded != null) {
            DATA.putAll(loaded);
        }
        LOGGER.info("[build] loaded {} players from {}", DATA.size(), file);
    }

    private static StoredBuild stored(UUID player) {
        StoredBuild b = DATA.computeIfAbsent(player.toString(), k -> new StoredBuild());
        if (b.keystones == null) {
            b.keystones = new ArrayList<>();
        }
        if (b.ownedKeystones == null) {
            b.ownedKeystones = new ArrayList<>();
        }
        if (b.loadouts == null) {
            b.loadouts = new HashMap<>();
        }
        return b;
    }

    private static void save() {
        if (file == null) {
            return;
        }
        com.thuvstu.hayatemod.save.SaveFiles.save(file, DATA, "build");
    }

    /** Combined keystone modifiers plus skill-point training for casting. */
    public static BuildMods mods(UUID player) {
        if (!ContentHolder.ready()) {
            return BuildMods.neutral();
        }
        List<com.thuvstu.hayatemod.core.content.model.Models.KeystoneDef> keys = new ArrayList<>();
        StoredBuild b = stored(player);
        for (String id : b.keystones) {
            var k = ContentHolder.get().keystones().get(id);
            if (k != null) {
                keys.add(k);
            }
        }
        return BuildMods.withPoints(BuildMods.combine(keys), b.spDmg, b.spCd, b.spMelee);
    }

    /** Rune effects socketed in the player's main-hand weapon. */
    public static List<EffectDef> runeEffects(ServerPlayer player) {
        List<EffectDef> out = new ArrayList<>();
        if (!ContentHolder.ready()) {
            return out;
        }
        for (String runeId : com.thuvstu.hayatemod.item.WeaponStack.getRunes(player.getMainHandItem())) {
            if (runeId.isEmpty()) {
                continue;
            }
            var rune = ContentHolder.get().runes().get(runeId);
            if (rune != null) {
                out.addAll(rune.effects());
            }
        }
        return out;
    }

    public static List<String> keystones(UUID player) {
        return List.copyOf(stored(player).keystones);
    }

    public static void toggleOffAll(UUID player) {
        stored(player).keystones.clear();
        save();
    }

    /** Toggles a keystone; returns a status message. Requires ownership. */
    public static String toggleKeystone(UUID player, String id) {
        if (ContentHolder.ready() && !ContentHolder.get().keystones().containsKey(id)) {
            return "unknown keystone '" + id + "'";
        }
        List<String> equipped = stored(player).keystones;
        if (equipped.contains(id)) {
            equipped.remove(id);
            save();
            return "解除: " + id;
        }
        if (!stored(player).ownedKeystones.contains(id)) {
            return "未取得: " + id + "（ダンジョンボス撃破で入手）";
        }
        if (equipped.size() >= MAX_KEYSTONES) {
            return "キーストーンは最大" + MAX_KEYSTONES + "つまで";
        }
        equipped.add(id);
        save();
        return "装備: " + id;
    }

    /** Grants skill points (boss victory, watcher pity). */
    public static void grantSp(UUID player, int points) {
        if (points <= 0) {
            return;
        }
        StoredBuild b = stored(player);
        b.sp += points;
        save();
    }

    public static String spStatus(UUID player) {
        StoredBuild b = stored(player);
        return "SP残り" + b.sp + "（攻撃+" + b.spDmg + " 短縮+" + b.spCd + " 近接+" + b.spMelee + "）";
    }

    /** Spends one skill point on damage/cooldown/melee. Returns a status message. */
    public static String spendSp(UUID player, String stat) {
        StoredBuild b = stored(player);
        if (b.sp <= 0) {
            return "SPがありません（ボス・ウォッチャー討伐で入手）";
        }
        int cap;
        switch (stat) {
            case "damage" -> cap = 25;
            case "cooldown" -> cap = 20;
            case "melee" -> cap = 25;
            default -> {
                return "不明なstat '" + stat + "'（damage/cooldown/melee）";
            }
        }
        int have = switch (stat) {
            case "damage" -> b.spDmg;
            case "cooldown" -> b.spCd;
            default -> b.spMelee;
        };
        if (have >= cap) {
            return stat + "は上限（" + cap + "）です";
        }
        switch (stat) {
            case "damage" -> b.spDmg++;
            case "cooldown" -> b.spCd++;
            default -> b.spMelee++;
        }
        b.sp--;
        save();
        return spStatus(player);
    }

    /** Grants a random unowned keystone (boss victory). Returns the id or null. */
    public static String grantRandomKeystone(UUID player) {
        if (!ContentHolder.ready()) {
            return null;
        }
        List<String> owned = stored(player).ownedKeystones;
        List<String> candidates = new ArrayList<>();
        for (String id : ContentHolder.get().keystones().keySet()) {
            if (!owned.contains(id)) {
                candidates.add(id);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        String pick = candidates.get((int) (Math.random() * candidates.size()));
        owned.add(pick);
        save();
        return pick;
    }

    public static void saveLoadout(UUID player, String name, String weaponId) {
        Loadout l = new Loadout();
        l.weapon = weaponId != null ? weaponId : "";
        l.keystones = new ArrayList<>(stored(player).keystones);
        stored(player).loadouts.put(name, l);
        save();
    }

    public static Loadout loadout(UUID player, String name) {
        return stored(player).loadouts.get(name);
    }

    public static Set<String> loadoutNames(UUID player) {
        return new HashSet<>(stored(player).loadouts.keySet());
    }
}
