package com.thuvstu.hayatemod.client.screen;

import java.util.List;

import com.thuvstu.hayatemod.net.UiPayloads.TavernEnter;
import com.thuvstu.hayatemod.net.UiPayloads.TavernNpc;
import com.thuvstu.hayatemod.net.UiPayloads.TavernQuest;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Tavern roster: who fights with you, the hire fee, and one dive button per quest. */
public class TavernScreen extends Screen {
    private final List<TavernNpc> npcs;
    private final int fee;
    private final boolean inSession;
    private final List<TavernQuest> quests;

    public TavernScreen(List<TavernNpc> npcs, int fee, boolean inSession, List<TavernQuest> quests) {
        super(Component.literal("酒場"));
        this.npcs = npcs;
        this.fee = fee;
        this.inSession = inSession;
        this.quests = quests;
    }

    @Override
    protected void init() {
        int y = height - 56 - Math.max(0, quests.size() - 1) * 24;
        for (TavernQuest q : quests) {
            String id = q.id();
            Button enter = Button.builder(
                    Component.literal("討伐: " + q.name() + " (Lv" + q.enemyLevel() + "・目安"
                            + q.targetMinutes() + "分・雇用費" + fee + "em)"),
                    b -> {
                        ClientPlayNetworking.send(new TavernEnter(id));
                        onClose();
                    }).bounds(width / 2 - 150, y, 300, 20).build();
            enter.active = !inSession;
            addRenderableWidget(enter);
            y += 24;
        }
        addRenderableWidget(Button.builder(Component.literal("閉じる"), b -> onClose())
                .bounds(width / 2 - 100, height - 30, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, "酒場名簿", width / 2, 15, 0xFFFFFF);
        int y = 40;
        for (TavernNpc npc : npcs) {
            graphics.drawString(font,
                    npc.name() + " <" + npc.role() + "> 熟練度" + npc.proficiency(), width / 2 - 150, y,
                    0xDDDDDD, false);
            y += 14;
        }
    }
}
