package com.jarvis.ai;

import java.util.concurrent.Future;

/** Lets the user stop a running request: cancels the in-flight HTTP call and flags the agent loop. */
public class CancelToken {

    private volatile boolean cancelled;
    private volatile Future<?> current;

    public boolean isCancelled() {
        return cancelled;
    }

    public void cancel() {
        cancelled = true;
        Future<?> f = current;
        if (f != null) {
            f.cancel(true);
        }
    }

    /** Registers the request currently in flight so {@link #cancel()} can abort it. */
    public void track(Future<?> future) {
        this.current = future;
        if (cancelled) {
            future.cancel(true);
        }
    }
}
