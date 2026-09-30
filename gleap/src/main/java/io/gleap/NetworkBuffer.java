package io.gleap;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;

/**
 * Keeps the newest network log entries recorded by the SDK (ring buffer, oldest dropped first) and,
 * separately, the entries attached from outside (e.g. the React Native or Flutter SDK), which each
 * attach call replaces. Reading never clears the buffer. Thread-safe.
 */
public class NetworkBuffer {
    static final int MAX_AMOUNT = 30;

    private final ArrayDeque<Networklog> networklogs = new ArrayDeque<>();
    private JSONArray attachedNetworkLogs = new JSONArray();

    public NetworkBuffer() {
    }

    /**
     * The entries recorded by the SDK, oldest first.
     */
    public synchronized Networklog[] getNetworklogs() {
        return networklogs.toArray(new Networklog[0]);
    }

    /**
     * Adds an entry. Once the buffer is full, the oldest entry is dropped.
     */
    public synchronized void addNetworkLog(Networklog networklog) {
        if (networklog == null) {
            return;
        }
        while (networklogs.size() >= MAX_AMOUNT) {
            networklogs.removeFirst();
        }
        networklogs.addLast(networklog);
    }

    /**
     * Replaces the attached network logs. null or an empty array removes them.
     */
    public void attachNetworkLogs(Networklog[] networklogs) {
        JSONArray attached = new JSONArray();
        if (networklogs != null) {
            for (Networklog networklog : networklogs) {
                if (networklog != null) {
                    attached.put(networklog.toRawJSON());
                }
            }
        }
        setAttachedNetworkLogs(attached);
    }

    /**
     * Replaces the attached network logs with copies of the given entries (objects only).
     */
    void attachNetworkLogs(JSONArray networkLogs) {
        JSONArray attached = new JSONArray();
        if (networkLogs != null) {
            for (int i = 0; i < networkLogs.length(); i++) {
                Object entry = networkLogs.opt(i);
                if (entry instanceof JSONObject) {
                    try {
                        attached.put(GleapNetworkLogSanitizer.deepCopy(entry));
                    } catch (Exception ignore) {
                    }
                }
            }
        }
        setAttachedNetworkLogs(attached);
    }

    private synchronized void setAttachedNetworkLogs(JSONArray attached) {
        this.attachedNetworkLogs = attached;
    }

    /**
     * The attached entries. Callers must not modify the returned array.
     */
    synchronized JSONArray getAttachedNetworkLogs() {
        return attachedNetworkLogs;
    }

    /**
     * Removes all recorded and attached entries.
     */
    public synchronized void clear() {
        networklogs.clear();
        attachedNetworkLogs = new JSONArray();
    }
}
