package com.jarvis.server;

import com.jarvis.core.EventBus;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * GET /api/events — a Server-Sent Events stream. The browser keeps this
 * connection open and receives every agent event the moment it happens.
 */
public class EventStreamHandler implements HttpHandler {

    private final EventBus bus;

    public EventStreamHandler(EventBus bus) {
        this.bus = bus;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.getResponseHeaders().set("X-Accel-Buffering", "no");
        ex.sendResponseHeaders(200, 0);
        BlockingQueue<String> queue = bus.subscribe();
        try (OutputStream out = ex.getResponseBody()) {
            write(out, ": connected\nretry: 2000\n\n");
            while (true) {
                String event = queue.poll(15, TimeUnit.SECONDS);
                // A comment line every 15 s keeps the connection alive and detects closed tabs.
                write(out, event == null ? ": ping\n\n" : "data: " + event + "\n\n");
            }
        } catch (IOException | InterruptedException e) {
            // browser tab closed
        } finally {
            bus.unsubscribe(queue);
        }
    }

    private static void write(OutputStream out, String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}
