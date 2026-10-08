package com.thuvstu.hayatemod.core.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.thuvstu.hayatemod.core.content.ContentPack;
import com.thuvstu.hayatemod.core.economy.MarketDefs.MarketBook;

class MarketSimTest {
    private MarketBook book() {
        var pack = ContentPack.load(com.thuvstu.hayatemod.core.TestContent.dir());
        assertTrue(pack.ok(), "loader errors: " + pack.errors());
        return pack.set().market();
    }

    @Test
    void dumpingLowersPrice() {
        MarketBook book = book();
        var listing = book.listings().stream()
                .filter(l -> l.item().equals("solommo:craft_material")).findFirst().orElseThrow();
        double calm = MarketSim.ask(book, listing, listing.targetStock());
        assertEquals(listing.basePrice(), calm, 1e-9);
        double dumped = MarketSim.ask(book, listing, listing.targetStock() + 64);
        assertTrue(dumped < calm, "dumped=" + dumped + " calm=" + calm);
    }

    @Test
    void clampsAndSpread() {
        MarketBook book = book();
        var listing = book.listings().get(0);
        double floor = MarketSim.ask(book, listing, 1e9);
        assertEquals(listing.basePrice() * 0.3, floor, 1e-9);
        double ceiling = MarketSim.ask(book, listing, 0.0);
        assertEquals(listing.basePrice() * 3.0, ceiling, 1e-9);
        double bid = MarketSim.bid(book, listing, listing.targetStock());
        assertTrue(bid < listing.basePrice(), "spread must make bid < ask");
    }

    @Test
    void dayExecutesWithoutPegging() {
        MarketBook book = book();
        Map<String, Double> stocks = MarketSim.initialStocks(book);
        for (int i = 0; i < 30; i++) {
            stocks = MarketSim.executeDay(book, stocks);
        }
        Map<String, Double> finalStocks = stocks;
        for (var l : book.listings()) {
            double ask = MarketSim.ask(book, l, finalStocks.getOrDefault(l.item(), l.startStock()));
            assertTrue(ask > l.basePrice() * 0.3 && ask < l.basePrice() * 3.0, l.item() + "=" + ask);
        }
    }
}
