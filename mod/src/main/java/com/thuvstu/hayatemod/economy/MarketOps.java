package com.thuvstu.hayatemod.economy;

import com.thuvstu.hayatemod.content.ContentHolder;
import com.thuvstu.hayatemod.core.economy.MarketDefs.Listing;
import com.thuvstu.hayatemod.core.economy.MarketSim;
import com.thuvstu.hayatemod.item.WeaponStack;
import com.thuvstu.hayatemod.life.MiningHooks;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** NPC market trading (Phase 6). Server-revalidated; used by chat and screen packets. */
public final class MarketOps {
    private MarketOps() {
    }

    public static Listing findListing(String item) {
        for (var l : ContentHolder.get().market().listings()) {
            if (l.item().equals(item)) {
                return l;
            }
        }
        return null;
    }

    public static int list(ServerPlayer player) {
        if (!ContentHolder.ready() || ContentHolder.get().market() == null) {
            return 0;
        }
        var book = ContentHolder.get().market();
        player.sendSystemMessage(Component.literal("[市場] 買値/売値 (エメラルド) 在庫:"));
        for (var l : book.listings()) {
            double stock = MarketStore.stockOf(l.item());
            long ask = Math.round(MarketSim.ask(book, l, stock));
            long bid = Math.round(MarketSim.bid(book, l, stock));
            player.sendSystemMessage(Component.literal(
                    "  " + l.item() + " 買" + ask + " / 売" + bid + " (在庫" + (int) stock + ")"));
        }
        return 1;
    }

    public static int buy(ServerPlayer player, String item, int count) {
        if (!ContentHolder.ready() || ContentHolder.get().market() == null) {
            return 0;
        }
        var listing = findListing(item);
        if (listing == null) {
            player.sendSystemMessage(Component.literal("[市場] 取扱なし: " + item));
            return 0;
        }
        var goods = MiningHooks.tradables().get(item);
        if (goods == null) {
            return 0;
        }
        var book = ContentHolder.get().market();
        long total = Math.round(
                MarketSim.ask(book, listing, MarketStore.stockOf(item)) * count);
        if (WeaponStack.countOf(player.getInventory(), net.minecraft.world.item.Items.EMERALD)
                < total) {
            player.sendSystemMessage(Component.literal("[市場] エメラルド不足 (要" + total + ")"));
            return 0;
        }
        WeaponStack.removeItems(player.getInventory(), net.minecraft.world.item.Items.EMERALD,
                (int) total);
        player.getInventory().add(new ItemStack(goods, count));
        MarketStore.adjust(item, -count);
        player.sendSystemMessage(
                Component.literal("[市場] 購入: " + item + " x" + count + " (" + total + "em)"));
        return 1;
    }

    public static int sell(ServerPlayer player, String item, int count) {
        if (!ContentHolder.ready() || ContentHolder.get().market() == null) {
            return 0;
        }
        var listing = findListing(item);
        if (listing == null) {
            player.sendSystemMessage(Component.literal("[市場] 取扱なし: " + item));
            return 0;
        }
        var goods = MiningHooks.tradables().get(item);
        if (goods == null || !(goods instanceof net.minecraft.world.item.Item goodsItem)) {
            return 0;
        }
        if (WeaponStack.countOf(player.getInventory(), goodsItem) < count) {
            player.sendSystemMessage(Component.literal("[市場] 所持不足: " + item));
            return 0;
        }
        var book = ContentHolder.get().market();
        long total = Math.round(
                MarketSim.bid(book, listing, MarketStore.stockOf(item)) * count);
        WeaponStack.removeItems(player.getInventory(), goodsItem, count);
        player.getInventory().add(new ItemStack(net.minecraft.world.item.Items.EMERALD, (int) total));
        MarketStore.adjust(item, count);
        player.sendSystemMessage(
                Component.literal("[市場] 売却: " + item + " x" + count + " (" + total + "em)"));
        return 1;
    }
}
