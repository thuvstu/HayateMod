package com.thuvstu.hayatemod.client.screen;

import java.util.List;

import com.thuvstu.hayatemod.net.UiPayloads.CraftMake;
import com.thuvstu.hayatemod.net.UiPayloads.CraftRow;
import com.thuvstu.hayatemod.net.UiPayloads.SalvageHeld;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Workshop: deterministic crafting plus salvage of the held item. */
public class CraftScreen extends Screen {
    private final List<CraftRow> rows;
    private final String heldName;

    public CraftScreen(List<CraftRow> rows, String heldName) {
        super(Component.literal("工房"));
        this.rows = rows;
        this.heldName = heldName;
    }

    @Override
    protected void init() {
        int y = 52;
        for (CraftRow row : rows) {
            int rowY = y;
            Button craft = Button.builder(Component.literal("作る"),
                    b -> ClientPlayNetworking.send(new CraftMake(row.weaponId())))
                    .bounds(width / 2 + 120, rowY, 80, 20).build();
            craft.active = row.affordable();
            addRenderableWidget(craft);
            y += 24;
        }
        addRenderableWidget(Button.builder(Component.literal("手持ちを分解"),
                b -> ClientPlayNetworking.send(new SalvageHeld()))
                .bounds(width / 2 - 100, height - 56, 200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("閉じる"), b -> onClose())
                .bounds(width / 2 - 100, height - 30, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, "工房（手持ち: " + heldName + "）", width / 2, 15, 0xFFFFFF);
        int y = 56;
        for (CraftRow row : rows) {
            graphics.drawString(font,
                    row.name() + " IL" + row.itemLevel() + " (素材" + row.matCost()
                            + (row.pityCost() > 0 ? "＋欠片" + row.pityCost() : "") + ")",
                    width / 2 - 220, y, row.affordable() ? 0xDDDDDD : 0x888888, false);
            y += 24;
        }
    }
}
