package com.thuvstu.hayatemod.core.economy;

import java.util.HashMap;
import java.util.Map;

import com.thuvstu.hayatemod.core.economy.MarketDefs.Listing;
import com.thuvstu.hayatemod.core.economy.MarketDefs.MarketBook;

/**
 * NPC stock market simulation (§5.14, §8.3).
 *
 * <pre>
 * price = base × (target / max(stock,1)) ^ elasticity, clamped to [0.3, 3.0] × base
 * ask (player buys) = price; bid (player sells) = price × (1 - spread)
 * </pre>
 */
public final class MarketSim {
    private MarketSim() {
    }

    public static double price(Listing l, double stock) {
        double ratio = l.targetStock() / Math.max(stock, 1.0);
        double p = l.basePrice() * Math.pow(ratio, l.elasticity());
        return clamp(p, l.basePrice() * 0.3, l.basePrice() * 3.0);
    }

    public static double ask(MarketBook book, Listing l, double stock) {
        return price(l, stock);
    }

    public static double bid(MarketBook book, Listing l, double stock) {
        return price(l, stock) * (1.0 - book.spreadDefault());
    }

    /** One market day: NPC adventurers supply, artisans/residents consume. */
    public static Map<String, Double> executeDay(MarketBook book, Map<String, Double> stocks) {
        Map<String, Double> next = new HashMap<>(stocks);
        for (Listing l : book.listings()) {
            double stock = next.getOrDefault(l.item(), l.startStock());
            stock = Math.max(0.0, stock + l.dailySupply() - l.dailyDemand());
            next.put(l.item(), stock);
        }
        return next;
    }

    public static Map<String, Double> initialStocks(MarketBook book) {
        Map<String, Double> stocks = new HashMap<>();
        for (Listing l : book.listings()) {
            stocks.put(l.item(), l.startStock());
        }
        return stocks;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
