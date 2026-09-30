package io.gleap;

import org.json.JSONObject;

import java.util.Date;

/**
 * One console log entry: {@code { "date": ISO, "priority": "INFO" | "WARNING" | "ERROR", "log": "..." }}.
 */
class ConsoleLogEntry {
    private final long time;
    private final String log;
    private final String priority;

    ConsoleLogEntry(long time, String log, String priority) {
        this.time = time;
        this.priority = priority;
        this.log = LogReader.capLog(log, priority);
    }

    long getTime() {
        return time;
    }

    String getLog() {
        return log;
    }

    String getPriority() {
        return priority;
    }

    public JSONObject toJSON() {
        JSONObject jo = new JSONObject();
        try {
            jo.put("date", DateUtil.dateToString(new Date(time)));
            jo.put("log", log);
            jo.put("priority", priority);
        } catch (Exception ex) {
        }

        return jo;
    }
}
