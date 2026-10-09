package com.thuvstu.hayatemod.core.economy;

import com.thuvstu.hayatemod.core.economy.MarketDefs.*;

/** Bounded server-authoritative marginal quotes; reverse trades cannot manufacture currency. */
public final class TradeRules {
    public static final int MAX_TOTAL = 4096;
    private TradeRules() { }
    private static void check(double stock, int count) {
        if (!Double.isFinite(stock) || stock < 0 || count < 1 || count > 64) {
            throw new IllegalArgumentException("count must be 1..64 and stock finite/nonnegative");
        }
    }
    public static int buy(MarketBook book, Listing listing, double stock, int count) {
        check(stock, count);
        if (stock < count) throw new IllegalArgumentException("insufficient market stock");
        double total = 0;
        for (int i = 0; i < count; i++) total += Math.ceil(MarketSim.ask(book, listing, stock - i));
        return checkedTotal(total);
    }
    public static int sell(MarketBook book, Listing listing, double stock, int count) {
        check(stock, count);
        double total = 0;
        // Price after each unit is returned: exactly the reverse inventory path of buying it.
        for (int i = 0; i < count; i++) total += Math.floor(MarketSim.bid(book, listing, stock + i + 1));
        return checkedTotal(total);
    }
    private static int checkedTotal(double total) {
        if (!Double.isFinite(total) || total < 0 || total > MAX_TOTAL) {
            throw new IllegalArgumentException("trade exceeds currency limit " + MAX_TOTAL);
        }
        return (int) total;
    }
}
