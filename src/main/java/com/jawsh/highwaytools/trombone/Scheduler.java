package com.jawsh.highwaytools.trombone;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Replacement for the coroutine delays of the original plugin.
 * Runnables are executed on the main client thread after a tick based delay.
 */
public class Scheduler {
    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "HighwayTools-Scheduler");
        t.setDaemon(true);
        return t;
    });

    public static void schedule(long ticks, Runnable runnable) {
        if (ticks <= 0) {
            mc.execute(runnable);
            return;
        }
        EXECUTOR.schedule(() -> mc.execute(runnable), ticks * 50L, TimeUnit.MILLISECONDS);
    }

    public static void scheduleMillis(long millis, Runnable runnable) {
        EXECUTOR.schedule(() -> mc.execute(runnable), millis, TimeUnit.MILLISECONDS);
    }
}
