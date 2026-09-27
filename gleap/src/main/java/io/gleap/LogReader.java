package io.gleap;

import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One console log entry: {@code { "date": ISO, "priority": "INFO" | "WARNING" | "ERROR", "log": "..." }}.
 */
class Log {
    private final long time;
    private final String log;
    private final String priority;

    Log(long time, String log, String priority) {
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

/**
 * The console logs sent with a ticket: the app's logcat output (read when a ticket is sent), the
 * messages logged with {@link Gleap#log(String)} and the attached console logs.
 */
class LogReader {
    static final int MAX_CUSTOM_LOGS = 500;
    static final int LOGCAT_LINES = 500;
    static final int MAX_LOG_LENGTH = 1000;
    static final int MAX_ERROR_LOG_LENGTH = 5000;
    static final String TRUNCATED_MARKER = "… [truncated]";
    private static final long ONE_DAY_MS = 24L * 60 * 60 * 1000;

    // threadtime: "09-27 10:00:00.123  1234  1256 E Tag     : message"
    private static final Pattern THREADTIME = Pattern.compile(
            "^(\\d\\d)-(\\d\\d)\\s+(\\d\\d):(\\d\\d):(\\d\\d)\\.(\\d{3})\\s+(\\d+)\\s+(\\d+)\\s+([VDIWEFA])\\s+(.*?)\\s*: (.*)$");
    private static final Pattern THREADTIME_WITHOUT_TAG = Pattern.compile(
            "^(\\d\\d)-(\\d\\d)\\s+(\\d\\d):(\\d\\d):(\\d\\d)\\.(\\d{3})\\s+(\\d+)\\s+(\\d+)\\s+([VDIWEFA])\\s+(.*)$");

    private static final LogReader instance = new LogReader();

    private final ArrayDeque<Log> customLogs = new ArrayDeque<>();
    private JSONArray attachedLogs = new JSONArray();

    private LogReader() {
    }

    public static LogReader getInstance() {
        return instance;
    }

    /**
     * Reads the newest logcat lines of this process. Blocks for the duration of the logcat call,
     * so never call it on the main thread.
     */
    List<Log> readLog() {
        // --pid needs API 24; before that the lines are filtered by pid after parsing.
        return readLogcat(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N);
    }

    List<Log> readLogcat(boolean usePidOption) {
        List<Log> logs = new ArrayList<>();
        Process process = null;
        try {
            int pid = android.os.Process.myPid();
            List<String> command = new ArrayList<>(Arrays.asList(
                    "logcat", "-d", "-v", "threadtime", "-T", String.valueOf(LOGCAT_LINES)));
            if (usePidOption) {
                command.add("--pid=" + pid);
            }
            process = new ProcessBuilder(command).redirectErrorStream(true).start();

            List<String> lines = new ArrayList<>();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), Charset.forName("UTF-8")));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            } finally {
                reader.close();
            }

            long now = System.currentTimeMillis();
            TimeZone timeZone = TimeZone.getDefault();
            for (String line : lines) {
                Log log = parseLogcatLine(line, pid, now, timeZone);
                if (log != null) {
                    logs.add(log);
                }
            }
        } catch (Throwable ignore) {
        } finally {
            if (process != null) {
                try {
                    process.destroy();
                } catch (Throwable ignore) {
                }
            }
        }
        return logs;
    }

    /**
     * Parses one line of {@code logcat -v threadtime}.
     *
     * @param pid only lines of this process are returned; a negative pid accepts every process
     * @param now the current time; a date in the future belongs to the previous year
     * @return the entry, or null for other lines (e.g. "--------- beginning of main")
     */
    static Log parseLogcatLine(String line, int pid, long now, TimeZone timeZone) {
        if (line == null) {
            return null;
        }
        String message;
        Matcher matcher = THREADTIME.matcher(line);
        if (matcher.matches()) {
            String tag = matcher.group(10).trim();
            message = tag.isEmpty() ? matcher.group(11) : tag + ": " + matcher.group(11);
        } else {
            matcher = THREADTIME_WITHOUT_TAG.matcher(line);
            if (!matcher.matches()) {
                return null;
            }
            message = matcher.group(10).trim();
        }

        try {
            if (pid >= 0 && Integer.parseInt(matcher.group(7)) != pid) {
                return null;
            }
        } catch (NumberFormatException ignore) {
            return null;
        }

        Calendar calendar = new GregorianCalendar(timeZone);
        calendar.setTimeInMillis(now);
        int year = calendar.get(Calendar.YEAR);
        calendar.clear();
        calendar.set(year,
                Integer.parseInt(matcher.group(1)) - 1,
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)),
                Integer.parseInt(matcher.group(4)),
                Integer.parseInt(matcher.group(5)));
        calendar.set(Calendar.MILLISECOND, Integer.parseInt(matcher.group(6)));
        if (calendar.getTimeInMillis() > now + ONE_DAY_MS) {
            // logcat has no year: a line from December read in January.
            calendar.add(Calendar.YEAR, -1);
        }

        return new Log(calendar.getTimeInMillis(), message, priorityFor(matcher.group(9)));
    }

    static String priorityFor(String level) {
        if ("E".equals(level) || "F".equals(level) || "A".equals(level)) {
            return GleapLogLevel.ERROR.name();
        }
        if ("W".equals(level)) {
            return GleapLogLevel.WARNING.name();
        }
        return GleapLogLevel.INFO.name();
    }

    /**
     * Keeps a log at most 1000 characters long (5000 for errors), including the truncation marker.
     */
    static String capLog(String log, String priority) {
        if (log == null) {
            return "";
        }
        int max = GleapLogLevel.ERROR.name().equals(priority) ? MAX_ERROR_LOG_LENGTH : MAX_LOG_LENGTH;
        if (log.length() <= max) {
            return log;
        }
        int cut = max - TRUNCATED_MARKER.length();
        if (Character.isHighSurrogate(log.charAt(cut - 1))) {
            cut--;
        }
        return log.substring(0, cut) + TRUNCATED_MARKER;
    }

    public void log(String msg, GleapLogLevel level) {
        Log log = new Log(System.currentTimeMillis(), msg, (level != null ? level : GleapLogLevel.INFO).name());
        synchronized (this) {
            while (customLogs.size() >= MAX_CUSTOM_LOGS) {
                customLogs.removeFirst();
            }
            customLogs.addLast(log);
        }
    }

    /**
     * Replaces the attached console logs with copies of the given entries (objects only).
     */
    void attachLogs(JSONArray logs) {
        JSONArray attached = new JSONArray();
        if (logs != null) {
            for (int i = 0; i < logs.length(); i++) {
                Object entry = logs.opt(i);
                if (entry instanceof JSONObject) {
                    try {
                        attached.put(GleapNetworkLogSanitizer.deepCopy(entry));
                    } catch (Exception ignore) {
                    }
                }
            }
        }
        synchronized (this) {
            attachedLogs = attached;
        }
    }

    /**
     * All console logs, oldest first. Reads logcat (unless disabled with
     * {@link Gleap#disableConsoleLog()}), so never call it on the main thread. Reading does not
     * clear the logs.
     */
    public JSONArray getLogs() {
        final List<Object[]> entries = new ArrayList<>();
        if (GleapConfig.getInstance().isEnableConsoleLogsFromCode()) {
            for (Log log : readLog()) {
                entries.add(new Object[]{log.getTime(), log.toJSON()});
            }
        }

        JSONArray attached;
        synchronized (this) {
            for (Log log : customLogs) {
                entries.add(new Object[]{log.getTime(), log.toJSON()});
            }
            attached = attachedLogs;
        }
        for (int i = 0; i < attached.length(); i++) {
            Object entry = attached.opt(i);
            if (!(entry instanceof JSONObject)) {
                continue;
            }
            try {
                JSONObject copy = (JSONObject) GleapNetworkLogSanitizer.deepCopy(entry);
                Object log = copy.opt("log");
                if (log instanceof String) {
                    copy.put("log", capLog((String) log, copy.optString("priority")));
                }
                long time = Long.MAX_VALUE;
                Object date = copy.opt("date");
                if (date instanceof String) {
                    try {
                        time = DateUtil.stringToDate((String) date).getTime();
                    } catch (Exception ignore) {
                    }
                }
                entries.add(new Object[]{time, copy});
            } catch (Exception ignore) {
            }
        }

        // Stable: entries with the same time keep their order.
        Collections.sort(entries, new Comparator<Object[]>() {
            @Override
            public int compare(Object[] a, Object[] b) {
                long timeA = (Long) a[0];
                long timeB = (Long) b[0];
                return timeA < timeB ? -1 : (timeA == timeB ? 0 : 1);
            }
        });

        JSONArray result = new JSONArray();
        for (Object[] entry : entries) {
            result.put(entry[1]);
        }
        return result;
    }
}
