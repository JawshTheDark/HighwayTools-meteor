package com.jawsh.highwaytools.trombone.util;

/**
 * Wall-clock based tick timer, equivalent to Lambda's {@code TickTimer(TimeUnit.TICKS)}.
 */
public class TickTimer {
    private long time = 0L;

    /**
     * @return true if at least {@code ticks} ticks (50ms each) elapsed since the last reset.
     */
    public boolean tick(long ticks, boolean resetIfRun) {
        long current = System.currentTimeMillis();
        if (current - time >= ticks * 50L) {
            if (resetIfRun) time = current;
            return true;
        }
        return false;
    }

    public boolean tick(long ticks) {
        return tick(ticks, true);
    }

    public void reset() {
        time = System.currentTimeMillis();
    }
}
