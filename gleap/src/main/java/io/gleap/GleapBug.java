package io.gleap;

import android.app.Application;
import android.graphics.Bitmap;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.ParseException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

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
    private String language = "";
    private String severity = "MEDIUM";
    private String silentBugreportEmail;
    private Bitmap screenshot;
    private Replay replay;
    private JSONObject customData;
    private JSONObject data;
    private String spamToken;
    private String outboubdId;

    private @Nullable
    PhoneMeta phoneMeta;


    private final JSONArray customEventLog = new JSONArray();

    private GleapBug() {
        customData = new JSONObject();
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

    public void setCustomData(JSONObject customData) {
        this.customData = customData;
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

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public void addRequest(Networklog networklog) {
        networkBuffer.addNetworkLog(networklog);
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

    public JSONObject getData() {
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
            event.put("date", dateToString(new Date()));
            event.put("name", name);
            event.put("data", data);
            customEventLog.put(event);
            GleapEventService.getInstance().addEvent(event);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void logEvent(String name) {
        JSONObject event = new JSONObject();
        try {
            event.put("date", dateToString(new Date()));
            event.put("name", name);
            customEventLog.put(event);
            GleapEventService.getInstance().addEvent(event);
        } catch (Exception ex) {
            ex.printStackTrace();
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

    public String getOutboubdId() {
        return outboubdId;
    }

    public void setOutboubdId(String outboubdId) {
        this.outboubdId = outboubdId;
    }

    public NetworkBuffer getNetworkBuffer() {
        return networkBuffer;
    }
}
