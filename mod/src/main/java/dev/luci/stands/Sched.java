package dev.luci.stands;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Server-tick scheduler (stand-in for the Bukkit scheduler). Everything runs on the server thread. */
public final class Sched {
    private record Task(long at, int period, Runnable once, BooleanSupplier repeat) { }

    private static final List<Task> tasks = new ArrayList<>();
    private static final List<Task> pending = new ArrayList<>();
    private static long now;

    private Sched() { }

    public static long now() { return now; }

    /** Run once after `ticks` ticks (0 = next tick). */
    public static void later(long ticks, Runnable r) { pending.add(new Task(now + Math.max(1, ticks), 0, r, null)); }

    /** Run every `period` ticks starting after `delay`, until the supplier returns false. */
    public static void timer(long delay, int period, BooleanSupplier keepGoing) {
        pending.add(new Task(now + Math.max(1, delay), Math.max(1, period), null, keepGoing));
    }

    static void tick() {
        now++;
        tasks.addAll(pending);
        pending.clear();
        for (int i = 0; i < tasks.size(); i++) {
            Task t = tasks.get(i);
            if (t.at > now) continue;
            tasks.remove(i--);
            try {
                if (t.once != null) t.once.run();
                else if (t.repeat.getAsBoolean()) pending.add(new Task(now + t.period, t.period, null, t.repeat));
            } catch (Exception ex) {
                StandsMod.LOG.error("Stand task failed", ex);
            }
        }
    }

    static void clear() { tasks.clear(); pending.clear(); }
}
