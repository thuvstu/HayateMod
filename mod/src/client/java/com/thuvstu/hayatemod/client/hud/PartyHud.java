package com.thuvstu.hayatemod.client.hud;

import com.thuvstu.hayatemod.net.UiPayloads.PartyMember;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/** Party frames (name, role, HP) synced from the session every second. */
public final class PartyHud {
    private PartyHud() {
    }

    public static void register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR,
                Identifier.fromNamespaceAndPath("hayatemod", "party"),
                (context, tickCounter) -> render(context));
    }

    static void render(GuiGraphics context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) {
            return;
        }
        var members = HudState.party();
        if (members.isEmpty()) {
            return;
        }
        int x = 4;
        int y = 24;
        context.drawString(client.font, "YOU " + (int) (HudState.playerFrac() * 100) + "%", x, y,
                0xFFFFFF, false);
        y += 10;
        for (PartyMember m : members) {
            int color = m.downed() ? 0xFF5555 : 0x55FF55;
            context.drawString(client.font,
                    (m.downed() ? "[DOWN] " : "") + m.name() + " <" + m.role() + "> "
                            + (int) (m.hpFrac() * 100) + "%",
                    x, y, color, false);
            y += 10;
        }
    }
}
