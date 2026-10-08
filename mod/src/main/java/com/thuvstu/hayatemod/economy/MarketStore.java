package com.thuvstu.hayatemod.economy;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.thuvstu.hayatemod.content.ContentHolder;
import com.thuvstu.hayatemod.core.economy.MarketSim;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NPC stock market state (Phase 6). Stocks persist in the world folder;
 * one market day passes every 12,000 server ticks (~10 min at 20 TPS),
 * tracked by a dedicated counter (never {@code dayTime}, no offline progress).
 */
public final class MarketStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("hayatemod/economy");
    private static final Gson GSON = new Gson();
    static final long TICKS_PER_DAY = 12_000L;

    private static Path file;
    private static final Map<String, Double> STOCKS = new HashMap<>();
    private static long ticks;

    private MarketStore() {
    }

    static final class Saved {
        Map<String, Double> stocks = new HashMap<>();
        long ticks;
    }

    public static void load(Path worldRoot) {
        file = worldRoot.resolve("hayatemod/market.json");
        STOCKS.clear();
        ticks = 0;
        Saved saved = com.thuvstu.hayatemod.save.SaveFiles.load(file,
                new TypeToken<Saved>() {
                }.getType(),
                "market");
        if (saved != null) {
            STOCKS.putAll(saved.stocks);
            ticks = saved.ticks;
        }
        if (ContentHolder.ready() && ContentHolder.get().market() != null) {
            for (var l : ContentHolder.get().market().listings()) {
                STOCKS.putIfAbsent(l.item(), l.startStock());
            }
        }
        LOGGER.info("[market] loaded {} listings, day counter {}", STOCKS.size(), ticks / TICKS_PER_DAY);
    }

    public static void registerTick() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!ContentHolder.ready() || ContentHolder.get().market() == null) {
                return;
            }
            ticks++;
            if (ticks % TICKS_PER_DAY == 0) {
                var book = ContentHolder.get().market();
                Map<String, Double> next = MarketSim.executeDay(book, STOCKS);
                STOCKS.clear();
                STOCKS.putAll(next);
                save();
                LOGGER.info("[market] day {} closed", ticks / TICKS_PER_DAY);
            }
        });
    }

    public static double stockOf(String item) {
        return STOCKS.getOrDefault(item, 0.0);
    }

    public static void adjust(String item, double delta) {
        STOCKS.put(item, Math.max(0.0, stockOf(item) + delta));
        save();
    }

    private static void save() {
        if (file == null) {
            return;
        }
        Saved saved = new Saved();
        saved.stocks.putAll(STOCKS);
        saved.ticks = ticks;
        com.thuvstu.hayatemod.save.SaveFiles.save(file, saved, "market");
    }
}
