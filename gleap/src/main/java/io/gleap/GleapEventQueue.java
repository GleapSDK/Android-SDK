package io.gleap;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * The events (trackEvent, page views, session start) waiting for the next ping, at most
 * {@link #MAX_EVENTS}. Thread-safe: the app adds events on any thread while a ping reads them in
 * the background.
 */
final class GleapEventQueue {
    static final int MAX_EVENTS = 500;

    /**
     * The oldest queued events for one ping.
     */
    static final class Batch {
        final List<JSONObject> events;
        // More events were queued than the batch takes.
        final boolean hasMore;

        Batch(List<JSONObject> events, boolean hasMore) {
            this.events = events;
            this.hasMore = hasMore;
        }

        boolean isEmpty() {
            return events.isEmpty();
        }

        JSONArray toJSONArray() {
            JSONArray result = new JSONArray();
            for (JSONObject event : events) {
                result.put(event);
            }
            return result;
        }
    }

    private static final class Entry {
        final JSONObject event;
        final boolean sessionStart;

        Entry(JSONObject event, boolean sessionStart) {
            this.event = event;
            this.sessionStart = sessionStart;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    /**
     * Adds an event; when the queue is full, the oldest event (other than a session start) is
     * dropped first.
     */
    synchronized void add(JSONObject event) {
        while (entries.size() >= MAX_EVENTS) {
            dropOldest();
        }
        entries.add(new Entry(event, false));
    }

    /**
     * Adds an event of a session start. It is kept when the queue is full: the oldest other event
     * makes room instead.
     */
    synchronized void addSessionStart(JSONObject event) {
        entries.add(new Entry(event, true));
        while (entries.size() > MAX_EVENTS) {
            dropOldest();
        }
    }

    // The oldest event that is not a session start, or the oldest one when all are.
    private void dropOldest() {
        for (Iterator<Entry> it = entries.iterator(); it.hasNext(); ) {
            if (!it.next().sessionStart) {
                it.remove();
                return;
            }
        }
        entries.remove(0);
    }

    synchronized boolean isEmpty() {
        return entries.isEmpty();
    }

    synchronized int size() {
        return entries.size();
    }

    /**
     * The queued events, oldest first.
     */
    synchronized JSONArray toJSONArray() {
        JSONArray result = new JSONArray();
        for (Entry entry : entries) {
            result.put(entry.event);
        }
        return result;
    }

    /**
     * The queued events, oldest first.
     */
    synchronized List<JSONObject> snapshot() {
        List<JSONObject> result = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            result.add(entry.event);
        }
        return result;
    }

    /**
     * The oldest events to send in one ping: at most {@code maxEvents}, and no more than
     * {@code maxBytes} of JSON (a single larger event goes alone). Remove them with
     * {@link #removeSent(List)} once they went through.
     */
    Batch nextBatch(int maxEvents, int maxBytes) {
        List<JSONObject> queued = snapshot();
        List<JSONObject> batch = new ArrayList<>();
        // The brackets of the array, and a comma per event.
        long bytes = 2;
        for (JSONObject event : queued) {
            if (batch.size() >= maxEvents) {
                break;
            }
            long size = event.toString().getBytes(StandardCharsets.UTF_8).length + 1;
            if (!batch.isEmpty() && bytes + size > maxBytes) {
                break;
            }
            batch.add(event);
            bytes += size;
        }
        return new Batch(batch, batch.size() < queued.size());
    }

    /**
     * Removes the events a ping delivered. Events added while it was in flight stay queued.
     */
    synchronized void removeSent(List<JSONObject> sent) {
        Set<JSONObject> delivered = Collections.newSetFromMap(new IdentityHashMap<JSONObject, Boolean>());
        delivered.addAll(sent);
        for (Iterator<Entry> it = entries.iterator(); it.hasNext(); ) {
            if (delivered.contains(it.next().event)) {
                it.remove();
            }
        }
    }

    synchronized void clear() {
        entries.clear();
    }
}
