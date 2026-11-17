package com.jarvis.core;

import com.jarvis.util.Json;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Publish/subscribe hub for live events (state changes, plan, tool steps,
 * approvals, reminders). Each open browser tab subscribes through
 * Server-Sent Events and receives every event as JSON.
 */
public class EventBus {

    private final List<BlockingQueue<String>> subscribers = new CopyOnWriteArrayList<>();

    public BlockingQueue<String> subscribe() {
        BlockingQueue<String> q = new LinkedBlockingQueue<>(1000);
        subscribers.add(q);
        return q;
    }

    public void unsubscribe(BlockingQueue<String> q) {
        subscribers.remove(q);
    }

    public int subscriberCount() {
        return subscribers.size();
    }

    /** Sends an event of the given type with the given fields to every subscriber. */
    public void publish(String type, Map<String, Object> fields) {
        StringBuilder json = new StringBuilder("{\"type\":").append(Json.quote(type));
        for (Map.Entry<String, Object> e : fields.entrySet()) {
            json.append(',').append(Json.quote(e.getKey())).append(':').append(Json.stringify(e.getValue()));
        }
        json.append('}');
        String event = json.toString();
        for (BlockingQueue<String> q : subscribers) {
            if (!q.offer(event)) {
                subscribers.remove(q); // a client that stopped reading
            }
        }
    }
}
