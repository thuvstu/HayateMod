package com.thuvstu.hayatemod.core.economy;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import com.thuvstu.hayatemod.core.economy.MarketDefs.*;

class TradeRulesTest {
    private final Listing listing = new Listing("iron", 10, 100, 1.5, 100, 0, 0);
    private final MarketBook book = new MarketBook(.2, List.of(listing));
    @Test void rejectsNegativeZeroAndOversizedPacketCounts() {
        for (int n : new int[] {Integer.MIN_VALUE, -1, 0, 65, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> TradeRules.buy(book, listing, 100, n));
            assertThrows(IllegalArgumentException.class, () -> TradeRules.sell(book, listing, 100, n));
        }
    }
    @Test void insufficientStockCannotGenerateGoods() {
        assertThrows(IllegalArgumentException.class, () -> TradeRules.buy(book, listing, 7.9, 8));
    }
    @Test void marginalBulkQuotesEqualSequentialSingleQuotes() {
        int buy = 0, sell = 0;
        for (int i = 0; i < 8; i++) {
            buy += TradeRules.buy(book, listing, 100 - i, 1);
            sell += TradeRules.sell(book, listing, 100 + i, 1);
        }
        assertEquals(buy, TradeRules.buy(book, listing, 100, 8));
        assertEquals(sell, TradeRules.sell(book, listing, 100, 8));
    }
    @Test void seededRoundTripsNeverCreateCurrencyInEitherDirection() {
        Random random = new Random(20261009);
        for (int i = 0; i < 10000; i++) {
            int count = 1 + random.nextInt(64);
            double stock = count + random.nextDouble() * 1000;
            var l = new Listing("goods", .01 + random.nextDouble() * 10, 10 + random.nextDouble() * 1000,
                    random.nextDouble() * 3, stock, 0, 0);
            var b = new MarketBook(random.nextDouble() * .5, List.of(l));
            assertTrue(TradeRules.buy(b, l, stock, count) >= TradeRules.sell(b, l, stock - count, count));
            assertTrue(TradeRules.sell(b, l, stock, count) <= TradeRules.buy(b, l, stock + count, count));
        }
    }
    @Test void tinyPricesDoNotBecomeFreePurchases() {
        var tiny = new Listing("tiny", .01, 100, .1, 100, 0, 0);
        assertEquals(1, TradeRules.buy(book, tiny, 100, 1));
        assertEquals(0, TradeRules.sell(book, tiny, 99, 1));
    }
    @Test void invalidStocksAndUnboundedCurrencyFail() {
        for (double stock : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> TradeRules.sell(book, listing, stock, 1));
        }
        var high = new Listing("expensive", 100000, 100, 1, 100, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> TradeRules.buy(book, high, 100, 1));
    }
    @Test void continuousSellingLowersOrPreservesEachSubsequentBid() {
        int before = TradeRules.sell(book, listing, 100, 1);
        for (int stock = 101; stock < 500; stock++) {
            int after = TradeRules.sell(book, listing, stock, 1);
            assertTrue(after <= before);
            before = after;
        }
    }
}
