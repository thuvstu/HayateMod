package com.thuvstu.hayatemod.dungeon;

import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * S3 signal test driver: pushes a signal to the session party without a boss
 * cast ({@code /s4 signal <name>}). Requires an active session.
 */
public final class SignalDebug {
    private SignalDebug() {
    }

    public static void send(String signal) {
        if (!EncounterRunner.isActive()) {
            return;
        }
        ServerLevel level = EncounterRunner.sessionLevel();
        if (level == null) {
            return;
        }
        Vec3 center = EncounterRunner.signalPoint(signal);
        NpcParty.orderSignal(level, signal, center,
                EncounterRunner.sessionBoss(), EncounterRunner.isLethalSignal(signal));
    }

    /** Safe-spot list for MOVE_TO_SAFE_SPOT tests. */
    static List<Vec3> safeSpots() {
        return EncounterRunner.safeSpotWorlds();
    }
}
