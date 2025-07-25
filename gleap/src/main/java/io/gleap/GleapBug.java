package io.gleap;

import android.app.Application;
import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.Iterator;

import static io.gleap.DateUtil.dateToString;

/**
 * Contains all relevant information gathered in the background.
 */
class GleapBug {
    private static GleapBug instance;
    private Application application;
    private NetworkBuffer networkBuffer = new NetworkBuffer();
    private boolean isSilent = false;
    //bug specific data
    private APPLICATIONTYPE applicationtype = APPLICATIONTYPE.NATIVE;
    private String type = "";
    private final Date startUpDate = new Date();
    private boolean isDisabled = false;
    private String severity = "MEDIUM";
    private String silentBugreportEmail;
    private Bitmap screenshot;
    private Replay replay;
    private JSONObject ticketAttributes;
    private JSONObject customData;
    private JSONObject data;
    private String spamToken;
    private String outboundId;
    private String[] tags;

    private JSONObject outboundAction;

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

    public static GleapBug getInstance() {
        if (instance == null) {
            instance = new GleapBug();
        }
        return instance;
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

    public void removeUserAttribute(String key) {
        if(key != null) {
            try {
                this.customData.remove(key);
            }catch (Exception ex){}
        }
    }

    public void clearCustomData() {
        this.customData = new JSONObject();
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public boolean isDisabled() {
        return isDisabled;
    }

    public void setDisabled(boolean disabled) {
        isDisabled = disabled;
    }

    public APPLICATIONTYPE getApplicationtype() {
        return applicationtype;
    }

    public void setApplicationtype(APPLICATIONTYPE applicationtype) {
        this.applicationtype = applicationtype;
    }

    public Replay getReplay() {
        return replay;
    }

    public void setReplay(Replay replay) {
        this.replay = replay;
    }

    public Date getStartUpDate() {
        return startUpDate;
    }
    
    public void addRequest(Networklog networklog) {
        try {
            networkBuffer.addNetworkLog(networklog);
        }catch (Exception ex) {}
    }


    public JSONArray getNetworklogs() {
        JSONArray requestArry = new JSONArray();
        try {
            for (Networklog networklog : networkBuffer.getNetworklogs()) {
                JSONObject item = networklog.toJSON();
                if(item != null) {
                    requestArry.put(item);
                }
            }
        } catch (Exception err) {
        }
        networkBuffer.clear();
        return requestArry;
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

    public String getSilentBugreportEmail() {
        return silentBugreportEmail;
    }

    public void setSilentBugreportEmail(String silentBugreportEmail) {
        this.silentBugreportEmail = silentBugreportEmail;
    }

    public Application getApplication() {
        return application;
    }

    public void setApplication(Application application) {
        this.application = application;
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

    public boolean isSilent() {
        return isSilent;
    }

    public void setSilent(boolean silent) {
        isSilent = silent;
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

    public JSONObject getOutboundAction() {
        return outboundAction;
    }

    public void setOutboundAction(JSONObject outboundAction) {
        this.outboundAction = outboundAction;
    }
}
