package com.thuvstu.hayatemod.client.screen;

import java.util.List;

import com.thuvstu.hayatemod.net.UiPayloads.MarketBuy;
import com.thuvstu.hayatemod.net.UiPayloads.MarketRow;
import com.thuvstu.hayatemod.net.UiPayloads.MarketSell;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** NPC market (Phase 6): live prices, buy/sell in stacks. Server revalidates everything. */
public class MarketScreen extends Screen {
    private final List<MarketRow> rows;

    public MarketScreen(List<MarketRow> rows) {
        super(Component.literal("市場"));
        this.rows = rows;
    }

    @Override
    protected void init() {
        int y = 40;
        for (MarketRow row : rows) {
            int rowY = y;
            addRenderableWidget(Button.builder(Component.literal("買1"),
                    b -> ClientPlayNetworking.send(new MarketBuy(row.item(), 1)))
                    .bounds(width / 2 + 60, rowY, 52, 20).build());
            addRenderableWidget(Button.builder(Component.literal("買8"),
                    b -> ClientPlayNetworking.send(new MarketBuy(row.item(), 8)))
                    .bounds(width / 2 + 116, rowY, 52, 20).build());
            addRenderableWidget(Button.builder(Component.literal("売1"),
                    b -> ClientPlayNetworking.send(new MarketSell(row.item(), 1)))
                    .bounds(width / 2 + 172, rowY, 52, 20).build());
            addRenderableWidget(Button.builder(Component.literal("売8"),
                    b -> ClientPlayNetworking.send(new MarketSell(row.item(), 8)))
                    .bounds(width / 2 + 228, rowY, 52, 20).build());
            y += 30;
        }
        addRenderableWidget(Button.builder(Component.literal("閉じる"), b -> onClose())
                .bounds(width / 2 - 100, height - 30, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, "市場（次の1個の価格・まとめ買いは単価変動）", width / 2, 15, 0xFFFFFF);
        int y = 46;
        for (MarketRow row : rows) {
            graphics.drawString(font,
                    row.name() + " 買" + row.ask() + " / 売" + row.bid() + " (在庫" + row.stock() + ")",
                    width / 2 - 220, y, 0xDDDDDD, false);
            y += 30;
        }
    }
}
