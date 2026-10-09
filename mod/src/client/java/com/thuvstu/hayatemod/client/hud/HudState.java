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
    private static volatile float shield;
    private static volatile int shieldTicks;
    private static volatile int heavyLeft, heavyTotal;
    private static volatile float mana, stamina;
    private static volatile com.thuvstu.hayatemod.net.UiPayloads.CastingState casting;
    public static void casting(com.thuvstu.hayatemod.net.UiPayloads.CastingState value) { casting = value; }
    public static com.thuvstu.hayatemod.net.UiPayloads.CastingState casting() { return casting; }
    public static int heavyLeft() { return heavyLeft; }
    public static int heavyTotal() { return heavyTotal; }
    public static float mana() { return mana; }
    public static float stamina() { return stamina; }

    public static void combat(com.thuvstu.hayatemod.net.UiPayloads.CombatState payload) {
        shield = payload.shield();
        shieldTicks = payload.shieldTicks();
        heavyLeft = payload.heavyLeft(); heavyTotal = payload.heavyTotal();
        mana = payload.mana(); stamina = payload.stamina();
    }

    public static float shield() {
        return shieldTicks > 0 ? shield : 0;
    }

    public static int shieldTicks() {
        return shieldTicks;
    }

    public static void clear() {
        skill = null;
        casting = null;
        heavyLeft = heavyTotal = 0;
        mana = stamina = 0;
        shield = 0;
        shieldTicks = 0;
        party = List.of();
        playerFrac = 1;
    }
    private static volatile List<PartyMember> party = List.of();
    private static volatile float playerFrac = 1.0F;

    public static void skill(SkillState payload) {
        skill = payload.weaponName().isEmpty() ? null
                : new Skill(payload.weaponName(), payload.specialName(), payload.left(), payload.total());
    }

    public static void tickSkill() {
        if (heavyLeft > 0) heavyLeft--;
        var cast = casting;
        if (cast != null && cast.remaining() > 0) {
            casting = new com.thuvstu.hayatemod.net.UiPayloads.CastingState(cast.slot(), cast.remaining() - 1, cast.total());
        }
        if (shieldTicks > 0) {
            shieldTicks--;
        }
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
