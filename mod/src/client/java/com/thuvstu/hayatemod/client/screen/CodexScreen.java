package com.thuvstu.hayatemod.client.screen;

import java.util.List;

import com.thuvstu.hayatemod.net.UiPayloads.CodexEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Minimal codex: owned weapons with build-match hints. */
public class CodexScreen extends Screen {
    private final List<CodexEntry> entries;

    public CodexScreen(List<CodexEntry> entries) {
        super(Component.literal("図鑑"));
        this.entries = entries;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("閉じる"), b -> onClose())
                .bounds(width / 2 - 100, height - 30, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, "図鑑 (" + entries.size() + ")", width / 2, 15, 0xFFFFFF);
        int y = 40;
        for (CodexEntry e : entries) {
            graphics.drawString(font, e.name() + " [" + e.rarity() + " IL" + e.itemLevel() + "] : 一致"
                    + e.match(), width / 2 - 150, y, 0xDDDDDD, false);
            y += 14;
            if (y > height - 40) {
                break;
            }
        }
    }
}
