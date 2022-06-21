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
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.gleap.DateUtil.dateToString;
import static io.gleap.DateUtil.formatDate;

/**
 * Read the log of the application.
 */
class LogReader {
    private static LogReader instance;
    private JSONArray logs = new JSONArray();


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
    public JSONArray readLog() {
        try {

            int id = android.os.Process.myPid();
            Process process = Runtime.getRuntime().exec(new String[]{"logcat", "--pid", "" + id, "-T", "1000", "-d"});
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            JSONArray log = new JSONArray();
            String line;
            Pattern pattern = Pattern.compile("^\\d{1,2}-\\d{1,2} \\d{1,2}:\\d{1,2}:\\d{1,2}.\\d{1,3}");

            while ((line = bufferedReader.readLine()) != null) {
                Matcher mt = pattern.matcher(line);
                if (mt.lookingAt()) {
                    String[] splittedLine = line.split(" ");
                    String formattedDate = formatDate(splittedLine[1], splittedLine[0]);
                    JSONObject object = new JSONObject();
                    object.put("date", formattedDate);
                    object.put("priority", getConsoleLineType(splittedLine[4]));
                    String logText = "";
                    try {
                        logText = line.substring(line.indexOf(splittedLine[5]) + splittedLine[5].length());
                    } catch (Exception ex) {
                        StringBuilder text = new StringBuilder();
                        for (int i = 5; i < splittedLine.length; i++) {
                            text.append(splittedLine[i]).append(" ");
                        }
                        logText = text.toString();

                    }
                    object.put("log", logText);

                    log.put(object);
                }
            }

            return log;
        } catch (IOException e) {
            return null;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void log(String msg, GleapLogLevel level) {
        try {
            JSONObject object = new JSONObject();
            object.put("date", dateToString(new Date()));
            object.put("priority", level.name());
            object.put("log", msg);
            this.logs.put(object);
        }catch (Exception ex) {

        }
    }

    private String getConsoleLineType(String input) {
        if (input.toLowerCase().equals("e")) {
            return "ERROR";
        }
        if (input.toLowerCase().equals("w")) {
            return "WARNING";
        }
        return "INFO";
    }

    public JSONArray getLogs() {
        JSONArray sortedJsonArray = new JSONArray();

        JSONArray cl = readLog();
        for(int i = 0; i < cl.length();i++) {
            try{
                this.logs.put(cl.getJSONObject(i));
            }catch (Exception ex) {}
        }

        List list = new ArrayList();
        for(int i = 0; i < this.logs.length(); i++) {
            try {
                list.add(this.logs.getJSONObject(i));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        Collections.sort(list, new Comparator() {
            @Override
            public int compare(Object o1, Object o2) {
                try{
                    Date o1Date = new Date(((JSONObject)o1).getString("date"));
                    Date o2Date = new Date(((JSONObject)o2).getString("date"));

                    return o1Date.compareTo(o2Date);
                } catch (Exception ex) {

                }
                return 0;
            }
        });
        for(int i = 0; i < this.logs.length(); i++) {
            sortedJsonArray.put(list.get(i));
        }
        return sortedJsonArray;
    }
}
