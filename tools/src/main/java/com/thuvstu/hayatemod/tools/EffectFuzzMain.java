package com.thuvstu.hayatemod.tools;

import java.nio.file.Path;
import com.thuvstu.hayatemod.core.content.ContentPipeline;
import com.thuvstu.hayatemod.core.sim.EffectFuzzer;

public final class EffectFuzzMain {
    private EffectFuzzMain() { }
    public static void main(String[] args) {
        if (args.length < 1 || args.length > 4) throw new IllegalArgumentException("effectFuzz <content> [seed=42] [samples=256] [ticks=100]");
        var pack = ContentPipeline.load(Path.of(args[0]));
        if (!pack.ok()) throw new IllegalArgumentException("invalid content: " + pack.loaderErrors() + pack.issues());
        var report = EffectFuzzer.run(pack.set(), args.length > 1 ? Long.parseLong(args[1]) : 42,
                args.length > 2 ? Integer.parseInt(args[2]) : 256, args.length > 3 ? Integer.parseInt(args[3]) : 100);
        System.out.println("Core-only stress: synthetic triggers, 3 stationary targets, unlimited resources, up to 2 runes/3 keystones.");
        System.out.println(report);
        System.out.println("Unfinished samples retain queued tasks/projectiles at the horizon; this is not a convergence or Minecraft acceptance proof.");
    }
}
