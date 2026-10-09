package com.thuvstu.hayatemod.core.engine;

import java.util.ArrayDeque;
import java.util.UUID;

/** Synchronous adapter boundary. Nested damage restores its parent even when a callback throws. */
public final class DamageScope {
    private record Frame(UUID target, CastContext context) { }
    private final ThreadLocal<ArrayDeque<Frame>> frames = new ThreadLocal<>();

    public void run(UUID target, CastContext context, Runnable action) {
        var stack = frames.get();
        if (stack == null) { stack = new ArrayDeque<>(); frames.set(stack); }
        stack.push(new Frame(target, context));
        try { action.run(); }
        finally { stack.pop(); if (stack.isEmpty()) frames.remove(); }
    }

    public CastContext current() {
        var stack = frames.get();
        return stack == null || stack.isEmpty() ? null : stack.peek().context();
    }

    public boolean appliesTo(UUID target) {
        var stack = frames.get();
        return stack != null && !stack.isEmpty() && target.equals(stack.peek().target());
    }

    public CastContext currentFor(UUID target) { return appliesTo(target) ? current() : null; }
}
