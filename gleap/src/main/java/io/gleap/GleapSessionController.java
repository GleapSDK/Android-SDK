package io.gleap;

import static android.content.Context.MODE_PRIVATE;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

public class GleapSessionController {
    private static GleapSessionController instance;
    private GleapSessionProperties gleapSessionProperties;
    private GleapSession gleapSession;
    private GleapSessionProperties pendingIdentificationAction;
    private GleapSessionProperties pendingUpdateAction;
    private boolean isSessionLoaded = false;
    private String lastRegisteredUserHash;
    private Application application;

    private GleapSessionController(Application application) {
        this.application = application;

        // Load existing session.
        SharedPreferences sharedPreferences = application.getSharedPreferences("usersession", MODE_PRIVATE);
        String id = sharedPreferences.getString("id", "");
        String hash = sharedPreferences.getString("hash", "");
        if (!id.equals("") && !hash.equals("")) {
            gleapSession = new GleapSession(id, hash);
        }

        // Load existing session properties.
        this.gleapSessionProperties = getStoredGleapUser();
    }

    public static GleapSessionController initialize(Application application) {
        if (instance == null) {
            instance = new GleapSessionController(application);
        }
        return instance;
    }

    public void executePendingUpdates() {
        tryExecuteIdentifyAction();
        tryExecuteContactUpdate();
    }

    private void tryExecuteIdentifyAction() {
        if (this.pendingIdentificationAction == null) {
            return;
        }

        new GleapIdentifyService().execute();
    }

    private void tryExecuteContactUpdate() {
        if (this.pendingUpdateAction == null) {
            return;
        }

        new GleapUpdateSessionService().execute();
    }

    public GleapSessionProperties getPendingUpdateAction() {
        return pendingUpdateAction;
    }

    public void setPendingUpdateAction(GleapSessionProperties pendingUpdateAction) {
        this.pendingUpdateAction = pendingUpdateAction;
    }

    public GleapSessionProperties getPendingIdentificationAction() {
        return pendingIdentificationAction;
    }

    public void setPendingIdentificationAction(GleapSessionProperties pendingIdentificationAction) {
        this.pendingIdentificationAction = pendingIdentificationAction;
    }

    public static GleapSessionController getInstance() {
        return instance;
    }

    public void clearUserSession() {
        SharedPreferences sharedPreferences = application.getSharedPreferences("usersession", MODE_PRIVATE);
        sharedPreferences.edit().clear().apply();

        ActivityUtil.getCurrentActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Handler mainHandler = new Handler(Looper.getMainLooper());
                Runnable gleapRunnable = new Runnable() {
                    @Override
                    public void run() throws RuntimeException {

                    }
                };
                mainHandler.post(gleapRunnable);
            }
        });

        if (GleapConfig.getInstance().getUnRegisterPushMessageGroupCallback() != null && gleapSession != null) {
            String hash = gleapSession.getHash();
            if (!hash.equals("")) {
                GleapConfig.getInstance().getUnRegisterPushMessageGroupCallback().invoke("gleapuser-" + hash);
            }
        }
        pendingUpdateAction = null;
        gleapSessionProperties = null;
        gleapSession = null;
        isSessionLoaded = false;
    }

    public void mergeUserSession(String id, String hash) {
        if (gleapSession == null) {
            gleapSession = new GleapSession(id, hash);
        } else {
            gleapSession.setHash(hash);
            gleapSession.setId(id);
        }
        SharedPreferences sharedPreferences = application.getSharedPreferences("usersession", MODE_PRIVATE);
        sharedPreferences.edit().putString("hash", hash).apply();
        sharedPreferences.edit().putString("id", id).apply();
    }

    public GleapSession getUserSession() {
        return gleapSession;
    }

    public void setGleapUserSession(GleapSessionProperties gleapUser) {
        this.gleapSessionProperties = gleapUser;

        // Locally save.
        SharedPreferences sharedPreferences = application.getSharedPreferences("gleap-user", MODE_PRIVATE);
        sharedPreferences.edit().putString("userId", gleapUser.getUserId()).apply();
        if (gleapUser != null) {
            sharedPreferences.edit().putString("name", gleapUser.getName()).apply();
            sharedPreferences.edit().putString("email", gleapUser.getEmail()).apply();
            if (gleapUser.getPhone() != null) {
                sharedPreferences.edit().putString("phone", gleapUser.getPhone()).apply();
            }
            if (gleapUser.getPlan() != null) {
                sharedPreferences.edit().putString("plan", gleapUser.getPlan()).apply();
            }
            if (gleapUser.getCompanyId() != null) {
                sharedPreferences.edit().putString("companyId", gleapUser.getCompanyId()).apply();
            }
            if (gleapUser.getCompanyName() != null) {
                sharedPreferences.edit().putString("companyName", gleapUser.getCompanyName()).apply();
            }
            sharedPreferences.edit().putFloat("value", (float) gleapUser.getValue()).apply();
            if (gleapUser.getHash() != null && !gleapUser.getHash().equals("")) {
                sharedPreferences.edit().putString("hash", gleapUser.getHash()).apply();
            }
            if(gleapUser.getCustomData() != null) {
                sharedPreferences.edit().putString("customData", gleapUser.getCustomData().toString()).apply();
            }
        }
    }

    public GleapSessionProperties getStoredGleapUser() {
        // Initialize gleapUser at the beginning.
        GleapSessionProperties gleapUser = new GleapSessionProperties();
        try {
            SharedPreferences sharedPreferences = application.getSharedPreferences("gleap-user", MODE_PRIVATE);
            String userId = sharedPreferences.getString("userId", "");
            String userName = sharedPreferences.getString("name", "");
            String email = sharedPreferences.getString("email", "");
            String phone = sharedPreferences.getString("phone", "");
            String plan = sharedPreferences.getString("plan", "");
            String companyId = sharedPreferences.getString("companyId", "");
            String companyName = sharedPreferences.getString("companyName", "");
            String hash = sharedPreferences.getString("hash", "");
            double value = sharedPreferences.getFloat("value", 0);

            // Set props.
            if (!userId.isEmpty()) {
                gleapUser.setUserId(userId);
            }
            if (!userName.isEmpty()) {
                gleapUser.setName(userName);
            }
            if (!email.isEmpty()) {
                gleapUser.setEmail(email);
            }
            if (!phone.isEmpty()) {
                gleapUser.setPhone(phone);
            }
            if (!plan.isEmpty()) {
                gleapUser.setPlan(plan);
            }
            if (!companyId.isEmpty()) {
                gleapUser.setCompanyId(companyId);
            }
            if (!companyName.isEmpty()) {
                gleapUser.setCompanyName(companyName);
            }
            if (!hash.isEmpty()) {
                gleapUser.setHash(hash);
            }

            // Set value.
            gleapUser.setValue(value);

            // Set custom data.
            JSONObject customData = new JSONObject();
            try {
                String customDataString = sharedPreferences.getString("customData", "");
                customData = new JSONObject(customDataString);
            } catch (Exception ex) {
                // Handle exception if necessary
            }
            gleapUser.setCustomData(customData);
        } catch (Exception | Error ignore) {
            // Handle exception if necessary, or leave it empty to ignore
        }
        return gleapUser;
    }

    public void processSessionActionResult(JSONObject result, boolean restartEventServices, boolean sendInitDelegate) {
        if (result == null) {
            return;
        }

        try {
            String id = null;
            String hash = null;

            if (result.has("gleapId")) {
                id = result.getString("gleapId");
            }

            if (result.has("gleapHash")) {
                hash = result.getString("gleapHash");
            }

            // Notify the session controller.
            if (id != null && hash != null) {
                mergeUserSession(id, hash);
                setSessionLoaded(true);
                gleapSession = getUserSession();

                // Update current session in session controller.
                GleapSessionProperties gleapSessionProperties = GleapSessionProperties.fromJSONObject(result);
                setGleapUserSession(gleapSessionProperties);

                // Check if there are any other actions to complete.
                executePendingUpdates();

                // Notify about registration.
                registerPushMessageGroup(gleapSessionProperties.getHash());

                if (restartEventServices) {
                    // Restart event service.
                    GleapEventService.getInstance().stop(false);
                    GleapEventService.getInstance().startWebSocketListener();
                }

                if (sendInitDelegate && GleapConfig.getInstance().getInitializationDoneCallback() != null) {
                    GleapConfig.getInstance().getInitializationDoneCallback().invoke();
                }
            }
        } catch (Exception exp) {}
    }

    public GleapSessionProperties getGleapUserSession() {
        return gleapSessionProperties;
    }

    public boolean isSessionLoaded() {
        return isSessionLoaded;
    }

    public void setSessionLoaded(boolean sessionLoaded) {
        isSessionLoaded = sessionLoaded;
    }

    public void registerPushMessageGroup(String userHash) {
        if (this.lastRegisteredUserHash != null && this.lastRegisteredUserHash.equalsIgnoreCase(userHash)) {
            // Already registered.
            return;
        }

        this.lastRegisteredUserHash = userHash;

        ActivityUtil.getCurrentActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Handler mainHandler = new Handler(Looper.getMainLooper());
                Runnable gleapRunnable = new Runnable() {
                    @Override
                    public void run() throws RuntimeException {
                        if (GleapConfig.getInstance().getRegisterPushMessageGroupCallback() != null && userHash != null && !userHash.isEmpty()) {
                            GleapConfig.getInstance().getRegisterPushMessageGroupCallback().invoke("gleapuser-" + userHash);
                        }
                    }
                };
                mainHandler.post(gleapRunnable);
            }
        });
    }

    public void unregisterPushMessageGroup(String userHash) {
        this.lastRegisteredUserHash = null;

        // Unregister old user.
        if (GleapConfig.getInstance().getUnRegisterPushMessageGroupCallback() != null && userHash != null && !userHash.isEmpty()) {
            try {
                ActivityUtil.getCurrentActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Handler mainHandler = new Handler(Looper.getMainLooper());
                        Runnable gleapRunnable = new Runnable() {
                            @Override
                            public void run() throws RuntimeException {
                                GleapConfig.getInstance().getUnRegisterPushMessageGroupCallback().invoke("gleapuser-" + userHash);
                            }
                        };
                        mainHandler.post(gleapRunnable);
                    }
                });
            } catch (Exception ignore) {
            }
        }
    }
}
