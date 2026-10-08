package com.thuvstu.hayatemod.core.describe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.thuvstu.hayatemod.core.content.model.Models.ActionDef;
import com.thuvstu.hayatemod.core.content.model.Models.ConditionDef;
import com.thuvstu.hayatemod.core.content.model.Models.EffectDef;
import com.thuvstu.hayatemod.core.content.model.Models.SkillDef;
import com.thuvstu.hayatemod.core.content.model.Models.WeaponCard;

/**
 * Generates in-game tooltip lines from structured effect definitions.
 * Hand-written flavor text never carries rules; if a template is missing for a
 * vocabulary word, {@link MissingTemplateException} is thrown (§9 V10) instead
 * of papering over it with free prose.
 */
public final class Describer {
    private Describer() {
    }

    public static List<String> describeRune(
            com.thuvstu.hayatemod.core.content.model.Models.RuneDef rune) {
        List<String> lines = new ArrayList<>();
        lines.add(rune.name());
        if (!rune.flavor().isEmpty()) {
            lines.add(rune.flavor());
        }
        for (EffectDef eff : rune.effects()) {
            lines.add(describeEffect(eff));
        }
        return lines;
    }

    public static List<String> describeKeystone(
            com.thuvstu.hayatemod.core.content.model.Models.KeystoneDef key) {
        List<String> lines = new ArrayList<>();
        lines.add(key.name());
        if (!key.desc().isEmpty()) {
            lines.add(key.desc());
        }
        List<String> mods = new ArrayList<>();
        if (key.chainBonus() != 0) {
            mods.add("連鎖上限+" + key.chainBonus());
        }
        if (key.damageMult() != 1.0) {
            mods.add("ダメージ×" + key.damageMult());
        }
        if (key.cooldownMult() != 1.0) {
            mods.add("クールダウン×" + key.cooldownMult());
        }
        if (key.meleeMult() != 1.0) {
            mods.add("近接×" + key.meleeMult());
        }
        if (!mods.isEmpty()) {
            lines.add("（" + String.join("、", mods) + "）");
        }
        return lines;
    }

    public static List<String> describeWeapon(WeaponCard w) {        List<String> lines = new ArrayList<>();
        lines.add(w.name());
        lines.add(rarityJp(w.rarity()) + w.family() + " / IL" + w.itemLevel());
        for (Map.Entry<String, SkillDef> e : w.skills().entrySet()) {
            lines.add(slotJp(e.getKey()) + describeSkill(e.getValue()));
            for (EffectDef eff : e.getValue().effects()) {
                lines.add("  " + describeEffect(eff));
            }
        }
        return lines;
    }

    public static String describeSkill(SkillDef s) {
        List<String> mods = new ArrayList<>();
        String resource = null;
        Object cost = null;
        for (Map.Entry<String, Object> m : s.mods().entrySet()) {
            if (m.getKey().equals("resource") && m.getValue() != null) {
                resource = m.getValue().toString();
            } else if (m.getKey().equals("resource_cost")) {
                cost = m.getValue();
            } else if (!(s.core().equals("summon_minions") && m.getKey().equals("count"))) {
                mods.add(modJp(m.getKey(), m.getValue()));
            }
        }
        if (resource != null || cost != null) {
            if (resource == null || cost == null) {
                throw new MissingTemplateException("mod", "resource without resource_cost");
            }
            mods.add("消費" + resourceJp(resource) + cost);
        }
        String body = coreJp(s.core(), s.mods());
        if (!mods.isEmpty()) {
            body += "（" + String.join("、", mods) + "）";
        }
        return body;
    }

    static String describeEffect(EffectDef e) {
        StringBuilder sb = new StringBuilder(triggerJp(e.trigger()) + ": ");
        if (e.scope().equals("area")) {
            sb.append("範囲（半径" + trim(e.radius()) + "m）内の対象全てに、");
        }
        List<String> conds = new ArrayList<>();
        for (ConditionDef c : e.conditions()) {
            conds.add(conditionJp(c));
        }
        if (!conds.isEmpty()) {
            sb.append(String.join("かつ", conds)).append("、");
        }
        List<String> acts = new ArrayList<>();
        for (ActionDef a : e.actions()) {
            acts.add(actionJp(a));
        }
        sb.append(String.join("、", acts));
        return sb.toString();
    }

    // ---- vocabulary templates ----

    static String triggerJp(String trigger) {
        return switch (trigger) {
            case "on_hit" -> "命中時";
            case "on_kill" -> "撃破時";
            case "on_damaged" -> "被弾時";
            case "on_break" -> "採掘時";
            case "on_death" -> "死亡時";
            case "on_timer" -> "定期";
            case "on_join" -> "参加時";
            default -> throw new MissingTemplateException("trigger", trigger);
        };
    }

    static String conditionJp(ConditionDef c) {
        return switch (c.type()) {
            case "target_has_status" -> "対象が" + statusJp(c.status()) + "状態の場合";
            case "chance" -> "確率" + trim(c.value() * 100) + "%の場合";
            case "sneaking" -> "スニーク中の場合";
            case "health_below" -> "対象HPが" + trim(c.value() * 100) + "%未満の場合";
            case "holding" -> "装備が" + c.name() + "の場合";
            case "has_effect" -> "対象が" + effectJp(c.name()) + "状態の場合";
            case "variable" -> "変数" + c.name() + "が" + trim(c.value())
                    + (c.max() > 0 ? "～" + trim(c.max()) : "以上") + "の場合";
            case "altitude" -> "高度" + trim(c.value()) + "以上の場合";
            case "burning" -> "対象が燃焼中の場合";
            case "day" -> "昼の場合";
            case "night" -> "夜の場合";
            case "raining" -> "雨天の場合";
            case "entity_type" -> "対象が" + c.name() + "の場合";
            default -> throw new MissingTemplateException("condition", c.type());
        };
    }

    static String actionJp(ActionDef a) {
        String amount = amt(a);
        return switch (a.type()) {
            case "spawn_projectiles" -> "追加の投射物を" + a.count() + "発放つ（威力×" + trim(a.damageMult())
                    + "、半径" + trim(a.radius()) + "m以内" + targetJp(a.target()) + inheritJp(a.inheritTags()) + "）";
            case "dash" -> "前方" + trim(a.distance()) + "mへ突進する";
            case "heal_self" -> "自身を" + amount + "回復する";
            case "apply_status" -> "対象に" + statusJp(a.status()) + "を付与する";
            case "blink" -> "対象の位置へ瞬間移動する";
            case "leap" -> "前方へ跳躍する（" + trim(a.distance()) + "m）";
            case "knockback" -> "対象を吹き飛ばす（×" + trim(a.damageMult()) + "）";
            case "strike" -> "対象へ直接ダメージ（威力×" + trim(a.damageMult()) + "）";
            case "ignite" -> "対象を" + amount + "秒炎上させる";
            case "extinguish" -> "対象の火を消す";
            case "potion" -> "対象に" + effectJp(a.effect()) + "を" + amount + "秒付与する";
            case "cleanse" -> "対象の状態異常を解除する";
            case "summon" -> a.ref() + "を召喚する";
            case "lightning" -> "対象へ落雷（威力×" + trim(a.damageMult()) + "）";
            case "explosion" -> "対象位置で爆発する（地形破壊なし、威力"
                    + trim(a.damageMult() > 0 ? a.damageMult() : 2) + "）";
            case "particles" -> "パーティクル" + a.effect() + "を出す";
            case "sound" -> "サウンド" + a.effect() + "を鳴らす";
            case "message" -> "メッセージ「" + a.ref() + "」を表示する";
            case "feed" -> "満腹度を" + amount + "回復する";
            case "xp" -> "経験値を" + amount + "得る";
            case "dropitem" -> a.ref() + "を" + Math.max(1, a.count()) + "個落とす";
            case "giveitem" -> a.ref() + "を" + Math.max(1, a.count()) + "個得る";
            case "setvar" -> "変数" + a.ref() + "を" + amount + "にする";
            case "addvar" -> "変数" + a.ref() + "に" + amount + "を加える";
            case "cast" -> a.ref() + "を連動発動する";
            case "delay" -> amount + "tick後に後続を実行する";
            case "missile" -> "誘導弾を" + a.count() + "発放つ（威力×" + trim(a.damageMult())
                    + "、半径" + trim(a.radius()) + "m以内" + targetJp(a.target()) + "を追尾"
                    + inheritJp(a.inheritTags()) + "）";
            case "repeat" -> "直前を" + a.count() + "回・" + amount + "tick間隔で繰り返す";
            default -> throw new MissingTemplateException("action", a.type());
        };
    }

    private static String amt(ActionDef a) {
        if (a.formula() != null && !a.formula().isEmpty()) {
            return a.formula();
        }
        return trim(a.amount());
    }

    static String coreJp(String core, Map<String, Object> mods) {
        return switch (core) {
            case "melee_thrust" -> "突き";
            case "projectile_single" -> "単発投射";
            case "heavy_slam" -> "強打";
            case "summon_minions" -> "手下を" + mods.getOrDefault("count", "?") + "体召喚";
            case "dash" -> "突進";
            default -> throw new MissingTemplateException("skill core", core);
        };
    }

    static String modJp(String key, Object value) {
        String v = value == null ? "?" : value.toString();
        return switch (key) {
            case "element" -> elementJp(v);
            case "range" -> "射程" + v;
            case "width" -> "幅" + v;
            case "cooldown" -> "CD" + v + "秒";
            case "distance" -> "距離" + v + "m";
            case "interval" -> "間隔" + v + "秒";
            case "cast_time" -> "詠唱" + v + "秒";
            case "radius" -> "半径" + v;
            default -> throw new MissingTemplateException("mod", key);
        };
    }

    static String elementJp(String element) {
        return switch (element) {
            case "fire" -> "火";
            default -> throw new MissingTemplateException("element", element);
        };
    }

    static String statusJp(String status) {
        if (status == null) {
            throw new MissingTemplateException("status", "null");
        }
        return switch (status) {
            case "ignite" -> "燃焼";
            default -> throw new MissingTemplateException("status", status);
        };
    }

    static String effectJp(String effect) {
        if (effect == null) {
            throw new MissingTemplateException("effect", "null");
        }
        return switch (effect) {
            case "speed" -> "移動速度上昇";
            case "slowness" -> "移動速度低下";
            case "haste" -> "採掘速度上昇";
            case "strength" -> "攻撃力上昇";
            case "regeneration" -> "再生能力";
            case "resistance" -> "耐性";
            case "fire_resistance" -> "火炎耐性";
            case "weakness" -> "弱体化";
            case "poison" -> "毒";
            case "absorption" -> "ダメージ吸収";
            case "glowing" -> "発光";
            case "night_vision" -> "暗視";
            default -> throw new MissingTemplateException("effect", effect);
        };
    }

    static String rarityJp(String rarity) {
        return switch (rarity) {
            case "unique" -> "ユニーク";
            case "rare" -> "レア";
            case "magic" -> "マジック";
            default -> throw new MissingTemplateException("rarity", rarity);
        };
    }

    private static String slotJp(String slot) {
        return switch (slot) {
            case "primary" -> "[通常攻撃] ";
            case "special" -> "[特殊] ";
            default -> "[" + slot + "] ";
        };
    }

    private static String targetJp(String target) {
        return switch (target) {
            case "nearest_enemy_in_radius" -> "の敵";
            case "attacker" -> "の攻撃者";
            case "look" -> "（視線方向へ直進）";
            case "party" -> "のパーティ";
            case "" -> "";
            default -> throw new MissingTemplateException("target", target);
        };
    }

    private static String inheritJp(List<String> tags) {
        if (tags.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (String t : tags) {
            int colon = t.indexOf(':');
            String ns = colon < 0 ? "" : t.substring(0, colon);
            String val = colon < 0 ? t : t.substring(colon + 1);
            parts.add(switch (ns) {
                case "element" -> elementJp(val) + "属性";
                case "status" -> statusJp(val);
                default -> throw new MissingTemplateException("tag namespace", ns);
            });
        }
        return "、" + String.join("・", parts) + "継承";
    }

    private static String resourceJp(String resource) {
        return switch (resource) {
            case "stamina" -> "スタミナ";
            case "mana" -> "マナ";
            default -> throw new MissingTemplateException("resource", resource);
        };
    }

    private static String trim(double d) {
        if (d == Math.rint(d)) {
            return String.valueOf((long) d);
        }
        return String.valueOf(d);
    }
}
