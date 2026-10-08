package com.thuvstu.hayatemod.client.hud;

import com.thuvstu.hayatemod.client.hud.HudState.Skill;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/** Skill bar line: held weapon special + cooldown. Server validates; this only displays. */
public final class SkillHud {
    private SkillHud() {
    }

    public static void register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,
                Identifier.fromNamespaceAndPath("hayatemod", "skill"),
                (context, tickCounter) -> render(context));
    }

    static void render(GuiGraphics context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) {
            return;
        }
        Skill skill = HudState.skill();
        if (skill == null) {
            return;
        }
        String text = skill.left() > 0
                ? "G " + skill.specialName() + " [" + String.format("%.1f", skill.left() / 20.0) + "s]"
                : "G " + skill.specialName() + " [READY]";
        int x = 4;
        int y = context.guiHeight() - 28;
        context.drawString(client.font, text, x, y, skill.left() > 0 ? 0xFF8888 : 0x88FF88, false);
        if (skill.total() > 0 && skill.left() > 0) {
            int w = 120;
            int filled = (int) (w * (1.0 - (double) skill.left() / skill.total()));
            context.fill(x, y + 10, x + w, y + 12, 0xFF333333);
            context.fill(x, y + 10, x + filled, y + 12, 0xFF66AAFF);
        }
    }
}
