package com.jarvis.agent;

import com.jarvis.core.EventBus;
import com.jarvis.db.DataStore;
import com.jarvis.model.Reminder;
import com.jarvis.system.DesktopNotifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Background thread that checks the reminders table every few seconds and
 * fires any that are due. Reminders that came due while JARVIS was closed
 * fire on the next start, marked as late.
 */
public class ReminderService {

    private final DataStore store;
    private final EventBus bus;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "jarvis-reminders");
        t.setDaemon(true);
        return t;
    });

    private final DesktopNotifier notifier; // may be null (tests, or no desktop)

    public ReminderService(DataStore store, EventBus bus) {
        this(store, bus, null);
    }

    public ReminderService(DataStore store, EventBus bus, DesktopNotifier notifier) {
        this.store = store;
        this.bus = bus;
        this.notifier = notifier;
    }

    public void start() {
        timer.scheduleAtFixedRate(this::tick, 2, 5, TimeUnit.SECONDS);
    }

    private void tick() {
        try {
            long now = System.currentTimeMillis();
            for (Reminder r : store.reminders.dueBy(now)) {
                r.markFired();
                store.reminders.update(r);
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("reminder", r);
                e.put("late", now - r.getDueAt() > 60_000);
                bus.publish("reminder", e);
                bus.publish("data_changed", Map.of("category", "reminders", "tool", "reminder_service"));
                if (notifier != null) {
                    // A Windows notification + sound, so it reaches the user even when the JARVIS page is closed.
                    notifier.notify("JARVIS reminder" + (now - r.getDueAt() > 60_000 ? " (missed earlier)" : ""), r.getText());
                }
            }
        } catch (RuntimeException e) {
            System.err.println("Reminder check failed: " + e.getMessage());
        }
    }
}
