package io.gleap;

import android.app.Activity;
import android.app.Application;
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
        GleapPreferencesHelper prefs = GleapPreferencesHelper.getInstance(application);
        String id = prefs.getString("session_id", "");
        String hash = prefs.getString("session_hash", "");
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
        GleapPreferencesHelper.getInstance(application).clear();

        if (gleapSession != null) {
            unregisterPushMessageGroup(gleapSession.getHash());
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
        GleapPreferencesHelper prefs = GleapPreferencesHelper.getInstance(application);
        prefs.putString("session_hash", hash);
        prefs.putString("session_id", id);
    }

    public GleapSession getUserSession() {
        return gleapSession;
    }

    public void setGleapUserSession(GleapSessionProperties gleapUser) {
        this.gleapSessionProperties = gleapUser;

        if (gleapUser == null) {
            return;
        }

        // Locally save.
        GleapPreferencesHelper prefs = GleapPreferencesHelper.getInstance(application);
        prefs.putString("userId", gleapUser.getUserId());
        prefs.putString("name", gleapUser.getName());
        prefs.putString("email", gleapUser.getEmail());
        if (gleapUser.getPhone() != null) {
            prefs.putString("phone", gleapUser.getPhone());
        }
        if (gleapUser.getPlan() != null) {
            prefs.putString("plan", gleapUser.getPlan());
        }
        if (gleapUser.getCompanyId() != null) {
            prefs.putString("companyId", gleapUser.getCompanyId());
        }
        if (gleapUser.getCompanyName() != null) {
            prefs.putString("companyName", gleapUser.getCompanyName());
        }
        if (gleapUser.getAvatar() != null) {
            prefs.putString("avatar", gleapUser.getAvatar());
        }
        prefs.putFloat("value", (float) gleapUser.getValue());
        prefs.putFloat("sla", (float) gleapUser.getSla());
        if (gleapUser.getHash() != null && !gleapUser.getHash().equals("")) {
            prefs.putString("hash", gleapUser.getHash());
        }
        if (gleapUser.getCustomData() != null) {
            prefs.putString("customData", gleapUser.getCustomData().toString());
        }
    }

    public GleapSessionProperties getStoredGleapUser() {
        GleapSessionProperties gleapUser = new GleapSessionProperties();
        try {
            GleapPreferencesHelper prefs = GleapPreferencesHelper.getInstance(application);
            String userId = prefs.getString("userId", "");
            String userName = prefs.getString("name", "");
            String email = prefs.getString("email", "");
            String phone = prefs.getString("phone", "");
            String plan = prefs.getString("plan", "");
            String companyId = prefs.getString("companyId", "");
            String companyName = prefs.getString("companyName", "");
            String avatar = prefs.getString("avatar", "");
            String hash = prefs.getString("hash", "");
            double value = prefs.getFloat("value", 0);
            double sla = prefs.getFloat("sla", 0);

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
            if (!avatar.isEmpty()) {
                gleapUser.setAvatar(avatar);
            }
            if (!hash.isEmpty()) {
                gleapUser.setHash(hash);
            }

            gleapUser.setValue(value);
            gleapUser.setSla(sla);

            JSONObject customData = new JSONObject();
            try {
                String customDataString = prefs.getString("customData", "");
                customData = new JSONObject(customDataString);
            } catch (Exception ex) {
            }
            gleapUser.setCustomData(customData);
        } catch (Exception | Error ignore) {
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
                // If the server returned a different hash than the one we currently hold
                // (typical on identify-merge into an existing identified session, or on
                // a session rotation triggered by clearIdentity-then-identifyUser without
                // a clean teardown in between) the previous FCM topic subscription is
                // now stale: pushes targeted at the previous identity will keep being
                // delivered to this device until we explicitly unsubscribe.
                //
                // Capture the previous hash BEFORE mergeUserSession overwrites it, then
                // unsubscribe so we are only ever a member of the current session's
                // topic. Without the lastRegisteredUserHash reset the subsequent
                // registerPushMessageGroup(hash) below would no-op when the previous
                // value happens to be cached here.
                String previousHash = (gleapSession != null) ? gleapSession.getHash() : null;
                if (previousHash != null && !previousHash.equalsIgnoreCase(hash)) {
                    unregisterPushMessageGroup(previousHash);
                }

                mergeUserSession(id, hash);
                setSessionLoaded(true);
                gleapSession = getUserSession();

                // Update current session in session controller.
                GleapSessionProperties gleapSessionProperties = GleapSessionProperties.fromJSONObject(result);
                setGleapUserSession(gleapSessionProperties);

                // Check if there are any other actions to complete.
                executePendingUpdates();

                // Notify about registration.
                registerPushMessageGroup(hash);

                if (restartEventServices) {
                    // Restart event service.
                    GleapEventService.getInstance().stop(false);
                    GleapEventService.getInstance().startWebSocketListener();
                }

                // Process push actions.
                Gleap.getInstance().processOpenPushActions();

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
