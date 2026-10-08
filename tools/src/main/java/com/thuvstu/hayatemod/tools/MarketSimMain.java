package com.thuvstu.hayatemod.tools;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.thuvstu.hayatemod.core.content.ContentPack;
import com.thuvstu.hayatemod.core.economy.MarketDefs.MarketBook;
import com.thuvstu.hayatemod.core.economy.MarketSim;

/**
 * {@code marketSim}: NPC-market projection (§8.3). Runs N days with an
 * optional scripted player dump, prints prices, flags pegging and round-trip
 * arbitrage. Usage: {@code marketSim [days] [dumpDay dumpItem dumpCount]}.
 */
public final class MarketSimMain {
    private MarketSimMain() {
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("usage: marketSim <contentDir> [days] [dumpDay dumpItem dumpCount]");
            System.exit(2);
        }
        int days = args.length > 1 ? Integer.parseInt(args[1]) : 30;
        ContentPack.LoadedPack pack = ContentPack.load(Path.of(args[0]));
        if (!pack.ok()) {
            for (var e : pack.errors()) {
                System.out.println("[LOAD-ERROR] " + e);
            }
            System.exit(1);
        }
        MarketBook book = pack.set().market();
        if (book == null) {
            System.out.println("no market book");
            System.exit(1);
        }
        Map<String, Double> stocks = MarketSim.initialStocks(book);
        boolean dumped = false;
        int dumpDay = args.length > 2 ? Integer.parseInt(args[2]) : -1;
        for (int day = 1; day <= days; day++) {
            if (dumpDay > 0 && !dumped && day >= dumpDay) {
                String item = args[3];
                double n = Double.parseDouble(args[4]);
                stocks.put(item, stocks.getOrDefault(item, 0.0) + n);
                System.out.println("day " + day + ": player dumps " + n + "x " + item);
                dumped = true;
            }
            stocks = MarketSim.executeDay(book, stocks);
            if (day % 5 == 0 || day == days) {
                System.out.println("day " + day + ": " + oneLine(book, stocks));
            }
        }
        List<String> findings = audit(book, stocks);
        for (String f : findings) {
            System.out.println(f);
        }
        if (findings.stream().anyMatch(f -> f.startsWith("[FAIL]"))) {
            System.exit(1);
        }
    }

    private static String oneLine(MarketBook book, Map<String, Double> stocks) {
        List<String> parts = new ArrayList<>();
        for (var l : book.listings()) {
            double stock = stocks.getOrDefault(l.item(), l.startStock());
            parts.add(l.item() + "=" + String.format("%.1f", MarketSim.ask(book, l, stock)));
        }
        return String.join(" ", parts);
    }

    static List<String> audit(MarketBook book, Map<String, Double> stocks) {
        List<String> out = new ArrayList<>();
        for (var l : book.listings()) {
            double stock = stocks.getOrDefault(l.item(), l.startStock());
            double ask = MarketSim.ask(book, l, stock);
            double bid = MarketSim.bid(book, l, stock);
            if (ask <= l.basePrice() * 0.31 || ask >= l.basePrice() * 2.99) {
                out.add("[WARN] " + l.item() + " pegged at clamp (" + String.format("%.1f", ask) + ")");
            } else {
                out.add("[OK] " + l.item() + " ask=" + String.format("%.1f", ask)
                        + " bid=" + String.format("%.1f", bid));
            }
            if (bid >= ask) {
                out.add("[FAIL] " + l.item() + " round-trip arbitrage (bid >= ask)");
            }
        }
        return out;
    }
}
