package io.gleap;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * The events (trackEvent, page views, session start) waiting for the next ping. Thread-safe: the
 * app adds events on any thread while a ping reads them in the background.
 */
final class GleapEventQueue {
    static final int MAX_EVENTS = 500;

    private List<JSONObject> events = new ArrayList<>();

    /**
     * Adds an event; at {@link #MAX_EVENTS} the oldest one is dropped first.
     */
    synchronized void add(JSONObject event) {
        if (events.size() == MAX_EVENTS) {
            events = new ArrayList<>(events.subList(1, events.size()));
        }
        events.add(event);
    }

    /**
     * Adds a session start event, without the limit.
     */
    synchronized void addUncapped(JSONObject event) {
        events.add(event);
    }

    synchronized boolean isEmpty() {
        return events.isEmpty();
    }

    synchronized int size() {
        return events.size();
    }

    /**
     * The queued events, oldest first.
     */
    synchronized JSONArray toJSONArray() {
        JSONArray result = new JSONArray();
        for (JSONObject event : events) {
            result.put(event);
        }
        return result;
    }

    /**
     * The queued events, oldest first, to send; remove them with {@link #removeSent(List)} once
     * they went through.
     */
    synchronized List<JSONObject> snapshot() {
        return new ArrayList<>(events);
    }

    /**
     * Removes the events a ping delivered. Events added while it was in flight stay queued.
     */
    synchronized void removeSent(List<JSONObject> sent) {
        Set<JSONObject> delivered = Collections.newSetFromMap(new IdentityHashMap<JSONObject, Boolean>());
        delivered.addAll(sent);
        for (Iterator<JSONObject> it = events.iterator(); it.hasNext(); ) {
            if (delivered.contains(it.next())) {
                it.remove();
            }
        }
    }

    synchronized void clear() {
        events = new ArrayList<>();
    }
}
