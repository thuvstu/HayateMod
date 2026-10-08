package com.thuvstu.hayatemod.rpg;

import com.thuvstu.hayatemod.build.PlayerBuilds;
import com.thuvstu.hayatemod.content.ContentHolder;
import com.thuvstu.hayatemod.core.build.WeaponSkillMerger;
import com.thuvstu.hayatemod.core.content.model.Models.SkillDef;
import com.thuvstu.hayatemod.core.content.model.Models.WeaponCard;
import com.thuvstu.hayatemod.core.describe.Describer;
import com.thuvstu.hayatemod.core.engine.CastContext;
import com.thuvstu.hayatemod.core.engine.EffectEngine.CastOutcome;
import com.thuvstu.hayatemod.core.engine.EffectEngine.CastResult;
import com.thuvstu.hayatemod.net.UiServer;
import com.thuvstu.hayatemod.item.WeaponStack;
import com.thuvstu.hayatemod.net.UiServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.UUID;

/** Entry points from input (G key) and vanilla-attack replacement into the engine. */
public final class RpgSkills {
    private static final Logger LOGGER = LoggerFactory.getLogger("hayatemod/rpg");

    private RpgSkills() {
    }

    public static void castHeldSpecial(ServerPlayer player) {
        castHeldSlot(player, "special");
    }

    /** Casts a named weapon slot ("special" or "heavy"). Unknown slots are rejected. */
    public static void castHeldSlot(ServerPlayer player, String slot) {
        if (!slot.equals("special") && !slot.equals("heavy")) {
            return;
        }
        if (McAdapter.engine() == null || !ContentHolder.ready()) {
            player.sendSystemMessage(Component.literal("[RPG] engine not ready"));
            return;
        }
        var held = player.getMainHandItem();
        WeaponCard card = WeaponStack.resolve(held);
        if (card == null) {
            player.sendSystemMessage(Component.literal("[RPG] 武器を持って (" + slot + ")"));
            return;
        }
        var mods = PlayerBuilds.mods(player.getUUID());
        var skill = WeaponSkillMerger.withRuneEffects(card.skills().get(slot),
                PlayerBuilds.runeEffects(player));
        if (skill == null) {
            player.sendSystemMessage(Component.literal("[RPG] この武器に " + slot + " がない"));
            return;
        }
        CastOutcome out = McAdapter.engine().castSkill(player.getUUID(),
                withSkill(card, slot, skill), slot, mods);
        if (out.result() == CastResult.OK) {
            LOGGER.info("[RPG][CAST] {} {} {}", player.getScoreboardName(), slot, card.id());
            var effective = withSkill(card, slot, skill);
            long cd = McAdapter.engine().cooldownTicks(effective, slot, mods);
            UiServer.sendSkill(player, card.name(), Describer.describeSkill(skill), (int) cd,
                    (int) cd);
            var adapter = McAdapter.adapter();
            if (adapter != null) {
                adapter.ringParticles(player.getUUID(), adapter.pos(player.getUUID()), 0.5);
            }
            player.level().playSound(null, player.blockPosition(),
                    net.minecraft.sounds.SoundEvents.FIRECHARGE_USE,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
            player.sendSystemMessage(Component.literal("[発動] " + card.name()), true);
        }
        report(player, out);
    }

    public static void meleeWith(ServerPlayer player, WeaponCard card) {
        if (McAdapter.engine() == null) {
            return;
        }
        SkillDef primary = card.skills().get("primary");
        if (primary == null) {
            return;
        }
        UUID id = player.getUUID();
        var mods = PlayerBuilds.mods(id);
        var skill = WeaponSkillMerger.withRuneEffects(primary, PlayerBuilds.runeEffects(player));
        McAdapter.engine().meleeStrike(player.getUUID(), withSkill(card, "primary", skill), skill,
                new CastContext(card.id() + ":primary", 0, Set.of(), id, 1.0, mods), mods);
        LOGGER.info("[RPG][MELEE] {} with {}", player.getScoreboardName(), card.id());
    }

    private static WeaponCard withSkill(WeaponCard card, String slot, SkillDef skill) {
        var skills = new java.util.LinkedHashMap<>(card.skills());
        skills.put(slot, skill);
        return new WeaponCard(card.id(), card.name(), card.family(), card.rarity(), card.itemLevel(),
                card.tagsExtra(), card.roleHint(), java.util.Map.copyOf(skills), card.designNote());
    }

    private static void report(ServerPlayer player, CastOutcome out) {
        if (out.result() == CastResult.ON_COOLDOWN) {
            player.sendSystemMessage(Component.literal(
                    "[RPG] CD中 (残り約" + out.remainingTicks() / 20.0 + "秒)"));
        } else if (out.result() == CastResult.NO_RESOURCE) {
            player.sendSystemMessage(Component.literal("[RPG] リソースが足りない"));
        } else if (out.result() == CastResult.UNSUPPORTED_CORE) {
            player.sendSystemMessage(Component.literal("[RPG] この武器の special は未対応の核です"));
        }
    }
}
