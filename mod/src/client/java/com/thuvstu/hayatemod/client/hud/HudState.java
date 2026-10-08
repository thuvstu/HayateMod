package com.thuvstu.hayatemod.client.hud;

import java.util.List;

import com.thuvstu.hayatemod.net.UiPayloads.PartyMember;
import com.thuvstu.hayatemod.net.UiPayloads.SkillState;

/** Client-side HUD state fed by S2C packets. Screens and HUD read from here. */
public final class HudState {
    private HudState() {
    }

    public record Skill(String weaponName, String specialName, int left, int total) {
    }

    private static volatile Skill skill;
    private static volatile List<PartyMember> party = List.of();
    private static volatile float playerFrac = 1.0F;

    public static void skill(SkillState payload) {
        skill = new Skill(payload.weaponName(), payload.specialName(), payload.left(), payload.total());
    }

    public static void tickSkill() {
        Skill s = skill;
        if (s != null && s.left() > 0) {
            skill = new Skill(s.weaponName(), s.specialName(), s.left() - 1, s.total());
        }
    }

    public static Skill skill() {
        return skill;
    }

    public static void party(List<PartyMember> members, float frac) {
        party = List.copyOf(members);
        playerFrac = frac;
    }

    public static List<PartyMember> party() {
        return party;
    }

    public static float playerFrac() {
        return playerFrac;
    }
}
