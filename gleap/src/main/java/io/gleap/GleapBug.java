package io.gleap;

import android.graphics.Bitmap;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static io.gleap.DateUtil.dateToString;

/**
 * Contains all relevant information gathered in the background.
 */
class GleapBug {
    private static GleapBug instance;
    private NetworkBuffer networkBuffer = new NetworkBuffer();
    //bug specific data
    private APPLICATIONTYPE applicationType = APPLICATIONTYPE.NATIVE;
    private String type = "";
    private Bitmap screenshot;
    private Replay replay;
    private JSONObject ticketAttributes;
    private JSONObject customData;
    private JSONObject data;
    private String spamToken;
    private String outboundId;
    private String[] tags;

    private @Nullable
    PhoneMeta phoneMeta;


    private final JSONArray customEventLog = new JSONArray();

    private GleapBug() {
        customData = new JSONObject();
        ticketAttributes = new JSONObject();
        if(60 % GleapConfig.getInstance().getInterval() == 0) {
            replay = new Replay(60 / GleapConfig.getInstance().getInterval(), 1000 * GleapConfig.getInstance().getInterval());
        }else {
            replay = new Replay(12, 5);
        }

    }

    // Synchronized: network requests are recorded from OkHttp threads.
    public static synchronized GleapBug getInstance() {
        if (instance == null) {
            instance = new GleapBug();
        }
        return instance;
    }

    // Tests only.
    static synchronized void resetForTesting() {
        instance = new GleapBug();
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setPhoneMeta(PhoneMeta phoneMeta) {
        this.phoneMeta = phoneMeta;
    }

    public @Nullable
    PhoneMeta getPhoneMeta() {
        return phoneMeta;
    }

    public void setScreenshot(Bitmap screenshot) {
        this.screenshot = screenshot;
    }

    public Bitmap getScreenshot() {
        return screenshot;
    }

    public JSONArray getLogs() {
        return LogReader.getInstance().getLogs();
    }

    public JSONObject getCustomData() {
        return customData;
    }

    public JSONObject getTicketAttributes() {
        return ticketAttributes;
    }

    public void setCustomData(JSONObject customData) {
        this.customData = customData;
    }

    public void setTicketAttribute(String key, Object value) throws JSONException {
        this.ticketAttributes.put(key, value);
    }

    public void setTicketAttribute(String key, int value) throws JSONException {
        this.ticketAttributes.put(key, value);
    }

    public void setTicketAttribute(String key, double value) throws JSONException {
        this.ticketAttributes.put(key, value);
    }

    public void setTicketAttribute(String key, long value) throws JSONException {
        this.ticketAttributes.put(key, value);
    }

    public void setTicketAttribute(String key, boolean value) throws JSONException {
        this.ticketAttributes.put(key, value);
    }

    public void unsetTicketAttribute(String key) {
        this.ticketAttributes.remove(key);
    }

    public void clearTicketAttributes() {
        this.ticketAttributes = new JSONObject();
    }

    public void setCustomData(String key, String value) {
        if(key != null && value != null) {
            try {
                this.customData.put(key, value);
            } catch (Exception e) {
            }
        }
    }

    public void removeCustomData(String key) {
        if(key != null) {
            try {
                this.customData.remove(key);
            }catch (Exception ex){}
        }
    }

    public void clearCustomData() {
        this.customData = new JSONObject();
    }

    public APPLICATIONTYPE getApplicationType() {
        return applicationType;
    }

    public void setApplicationType(APPLICATIONTYPE applicationType) {
        this.applicationType = applicationType;
    }

    public Replay getReplay() {
        return replay;
    }

    public void setReplay(Replay replay) {
        this.replay = replay;
    }

    public void addRequest(Networklog networklog) {
        try {
            networkBuffer.addNetworkLog(networklog);
        }catch (Exception ex) {}
    }


    /**
     * The network logs to send: the SDK's own entries and the attached ones, oldest first, with the
     * blacklist and the props to ignore applied. Reading does not clear them, so a later ticket
     * still gets them.
     */
    public JSONArray getNetworklogs() {
        try {
            List<JSONObject> entries = new ArrayList<>();
            for (Networklog networklog : networkBuffer.getNetworklogs()) {
                entries.add(networklog.toRawJSON());
            }
            JSONArray attached = networkBuffer.getAttachedNetworkLogs();
            for (int i = 0; i < attached.length(); i++) {
                Object entry = attached.opt(i);
                if (entry instanceof JSONObject) {
                    entries.add((JSONObject) entry);
                }
            }
            sortByDate(entries);

            JSONArray merged = new JSONArray();
            for (JSONObject entry : entries) {
                merged.put(entry);
            }
            return GleapNetworkLogSanitizer.fromConfig().sanitize(merged);
        } catch (Throwable err) {
            return new JSONArray();
        }
    }

    // Stable sort by the ISO date; entries without a readable date keep their place at the end.
    static void sortByDate(List<JSONObject> entries) {
        final Map<JSONObject, Long> times = new IdentityHashMap<>();
        for (JSONObject entry : entries) {
            long time = Long.MAX_VALUE;
            Object date = entry.opt("date");
            if (date instanceof String) {
                try {
                    time = DateUtil.stringToDate((String) date).getTime();
                } catch (Exception ignore) {
                }
            }
            times.put(entry, time);
        }
        Collections.sort(entries, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject a, JSONObject b) {
                long timeA = times.get(a);
                long timeB = times.get(b);
                return timeA < timeB ? -1 : (timeA == timeB ? 0 : 1);
            }
        });
    }

    public void setData(JSONObject data) {
        this.data = data;
    }

    public JSONObject mergeJSONObjects(JSONObject json1, JSONObject json2) throws JSONException {
        // Create a new JSONObject to store the merged result
        JSONObject merged = new JSONObject();

        // Iterate over the keys of json1 and copy them to the merged object
        Iterator<String> keys = json1.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            merged.put(key, json1.get(key));
        }

        // Iterate over the keys of json2 and copy them to the merged object, possibly overwriting values
        keys = json2.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            merged.put(key, json2.get(key));
        }

        return merged;
    }

    public JSONObject getData() {
        try {
            return this.mergeJSONObjects(this.data, this.ticketAttributes);
        } catch (Exception exp) {}

        return this.data;
    }

    public void logEvent(String name, JSONObject data) {
        JSONObject event = new JSONObject();
        try {
            event.put("name", name);
            event.put("data", data);
            event.put("date", dateToString(new Date()));
            customEventLog.put(event);
            GleapEventService.getInstance().addEvent(event);
        } catch (Exception ex) {
        }
    }

    public void logEvent(String name) {
        JSONObject event = new JSONObject();
        try {
            event.put("name", name);
            event.put("date", dateToString(new Date()));
            customEventLog.put(event);
            GleapEventService.getInstance().addEvent(event);
        } catch (Exception ex) {
        }
    }

    public JSONArray getCustomEventLog() {
        return customEventLog;
    }

    public String getSpamToken() {
        return spamToken;
    }

    public void setSpamToken(String spamToken) {
        this.spamToken = spamToken;
    }

    public String getOutboundId() {
        return outboundId;
    }

    public void setOutboundId(String outboundId) {
        this.outboundId = outboundId;
    }

    public NetworkBuffer getNetworkBuffer() {
        return networkBuffer;
    }

    public String[] getTags() {
        return tags;
    }

    public void setTags(String[] tags) {
        this.tags = tags;
    }
}
