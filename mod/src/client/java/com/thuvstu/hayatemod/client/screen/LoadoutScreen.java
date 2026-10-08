package com.thuvstu.hayatemod.client.screen;

import java.util.List;

import com.thuvstu.hayatemod.net.UiPayloads.KeystoneRow;
import com.thuvstu.hayatemod.net.UiPayloads.KeystoneToggle;
import com.thuvstu.hayatemod.net.UiPayloads.LoadoutApply;
import com.thuvstu.hayatemod.net.UiPayloads.LoadoutSave;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Loadout studio: keystone toggles, save/apply, build signature readout. */
public class LoadoutScreen extends Screen {
    private final List<KeystoneRow> keystones;
    private final List<String> loadouts;
    private final String buildSig;
    private EditBox nameBox;

    public LoadoutScreen(List<KeystoneRow> keystones, List<String> loadouts, String buildSig) {
        super(Component.literal("ロードアウト"));
        this.keystones = keystones;
        this.loadouts = loadouts;
        this.buildSig = buildSig;
    }

    @Override
    protected void init() {
        int y = 52;
        for (KeystoneRow key : keystones) {
            int rowY = y;
            addRenderableWidget(Button.builder(
                    Component.literal((key.equipped() ? "[ON] " : "[--] ") + key.name()),
                    b -> ClientPlayNetworking.send(new KeystoneToggle(key.id())))
                    .bounds(width / 2 - 220, rowY, 220, 20).build());
            y += 24;
        }
        y += 8;
        for (String loadout : loadouts) {
            int rowY = y;
            addRenderableWidget(Button.builder(Component.literal("適用: " + loadout),
                    b -> ClientPlayNetworking.send(new LoadoutApply(loadout)))
                    .bounds(width / 2 - 220, rowY, 160, 20).build());
            y += 24;
        }
        nameBox = new EditBox(font, width / 2 - 220, y, 160, 20, Component.literal("名前"));
        addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(Component.literal("保存"),
                b -> ClientPlayNetworking.send(new LoadoutSave(nameBox.getValue())))
                .bounds(width / 2 - 52, y, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("閉じる"), b -> onClose())
                .bounds(width / 2 - 100, height - 30, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, "ロードアウト（街での切替が有効）", width / 2, 15, 0xFFFFFF);
        graphics.drawCenteredString(font, buildSig, width / 2, 30, 0x888888);
        int y = 56;
        for (KeystoneRow key : keystones) {
            graphics.drawString(font, key.desc(), width / 2 + 8, y, 0x888888, false);
            y += 24;
        }
    }
}
