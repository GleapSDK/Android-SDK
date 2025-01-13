package io.gleap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.gleap.DateUtil.dateToString;
import static io.gleap.DateUtil.formatDate;
import static io.gleap.DateUtil.stringToDate;

class Log {
    private String date;
    private String log;
    private String priority;

    public Log(String date, String log, String priority) {
        this.date = date;
        this.log = log;
        this.priority = priority;
    }

    public JSONObject toJSON() {
        JSONObject jo = new JSONObject();
        try {
            jo.put("date", date);
            jo.put("log", log);
            jo.put("priority", priority);
        } catch (Exception ex) {
        }

        return jo;
    }

    public String getDate() {
        return date;
    }
}

/**
 * Read the log of the application.
 */
class LogReader {
    private static LogReader instance;
    private List<Log> customLogs = new LinkedList<Log>();


    private LogReader() {
    }

    public static LogReader getInstance() {
        if (instance == null) {
            instance = new LogReader();
        }
        return instance;
    }

    /**
     * Reads the stacktrace, formats the string
     *
     * @return {@link JSONArray} formatted log
     */
    public List<Log> readLog() {
        try {
            int id = android.os.Process.myPid();
            Process process = Runtime.getRuntime().exec(new String[]{"logcat", "--pid", "" + id, "-T", "150", "-d"});
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            List<Log> log = new LinkedList<>();
            String line;
            Pattern pattern = Pattern.compile("^\\d{1,2}-\\d{1,2} \\d{1,2}:\\d{1,2}:\\d{1,2}.\\d{1,3}");

            while ((line = bufferedReader.readLine()) != null) {
                Matcher mt = pattern.matcher(line);
                if (mt.lookingAt()) {
                    try {
                        String[] splittedLine = line.split(" ");
                        
                        // Ensure splittedLine has enough elements to avoid ArrayIndexOutOfBoundsException
                        if (splittedLine.length > 5) {
                            String formattedDate = formatDate(splittedLine[1], splittedLine[0]);
                            String logText = "";

                            try {
                                // Safely compute logText
                                int index = line.indexOf(splittedLine[5]);
                                if (index != -1) {
                                    logText = line.substring(index + splittedLine[5].length());
                                }
                            } catch (Exception ex) {
                                // Fallback to building logText manually
                                StringBuilder text = new StringBuilder();
                                for (int i = 5; i < splittedLine.length; i++) {
                                    text.append(splittedLine[i]).append(" ");
                                }
                                logText = text.toString().trim(); // Trim to remove trailing spaces
                            }

                            // Add to log only if formattedDate and logText are valid
                            log.add(new Log(formattedDate, logText, getConsoleLineType(splittedLine[4])));
                        } else {
                            // Handle cases where the line does not meet the expected structure
                            System.err.println("Invalid line structure: " + line);
                        }
                    } catch (Exception ex) {
                        // Log the exception for debugging
                        ex.printStackTrace();
                    }
                }
            }

            return log;
        } catch (IOException e) {
            return null;
        }
    }

    public void log(String msg, GleapLogLevel level) {
        this.customLogs.add(new Log(dateToString(new Date()), msg, level.name()));
    }

    private String getConsoleLineType(String input) {
        if (input.equalsIgnoreCase("e")) {
            return "ERROR";
        }
        if (input.equalsIgnoreCase("w")) {
            return "WARNING";
        }
        return "INFO";
    }

    public JSONArray getLogs() {

        List<Log> toBeSorted = new LinkedList<>();

        if (GleapConfig.getInstance().isEnableConsoleLogsFromCode()) {
            toBeSorted = readLog();
        }
        toBeSorted.addAll(customLogs);

        Collections.sort(toBeSorted, new Comparator() {
            @Override
            public int compare(Object o1, Object o2) {
                try {
                    Date o1Date = stringToDate(((Log) o1).getDate());
                    Date o2Date = stringToDate(((Log) o2).getDate());
                    return o1Date.compareTo(o2Date);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
                return 0;
            }
        });

        JSONArray sortedJsonArray = new JSONArray();
        for (int i = 0; i < toBeSorted.size(); i++) {
            sortedJsonArray.put(toBeSorted.get(i).toJSON());
        }

        this.customLogs = new LinkedList<>();

        return sortedJsonArray;
    }
}
