package com.thuvstu.hayatemod.core.economy;

import java.util.List;

/** NPC-market definitions (IMPLEMENTATION.md §5.14). Currency is vanilla emeralds. */
public final class MarketDefs {
    private MarketDefs() {
    }

    public record Listing(String item, double basePrice, double targetStock, double elasticity,
            double startStock, double dailySupply, double dailyDemand) {
    }

    public record MarketBook(double spreadDefault, List<Listing> listings) {
    }
}
