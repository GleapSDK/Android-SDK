package io.gleap;

import android.app.Application;

import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicInteger;

public class GleapSessionController {
    // Read from the request threads.
    private static volatile GleapSessionController instance;
    private GleapSessionProperties gleapSessionProperties;
    private volatile GleapSession gleapSession;
    private GleapSessionProperties pendingIdentificationAction;
    private GleapSessionProperties pendingUpdateAction;
    private volatile boolean isSessionLoaded = false;
    private String lastRegisteredUserHash;
    // Where the session and the identified user are kept between app starts.
    private final KeyValueStore store;
    // Counts the logouts (clearUserSession). A request started before a logout neither applies
    // its result nor acts on its failure: it must not bring the cleared session or user back.
    private final AtomicInteger identityGeneration = new AtomicInteger();

    private GleapSessionController(Application application) {
        this(GleapPreferencesHelper.getInstance(application));
    }

    GleapSessionController(KeyValueStore store) {
        this.store = store;

        // Load existing session.
        String id = store.getString("session_id", "");
        String hash = store.getString("session_hash", "");
        if (!id.equals("") && !hash.equals("")) {
            gleapSession = new GleapSession(id, hash);
        }

        // Load existing session properties.
        this.gleapSessionProperties = getStoredGleapUser();
    }

    public static GleapSessionController initialize(Application application) {
        synchronized (GleapSessionController.class) {
            if (instance == null) {
                instance = new GleapSessionController(application);
            }
            return instance;
        }
    }

    // Tests only.
    static void setInstanceForTesting(GleapSessionController controller) {
        instance = controller;
    }

    public void executePendingUpdates() {
        tryExecuteIdentifyAction();
        tryExecuteContactUpdate();
    }

    private void tryExecuteIdentifyAction() {
        if (this.pendingIdentificationAction == null) {
            return;
        }

        new GleapIdentifyService().executeOnExecutor(GleapExecutor.SERIAL);
    }

    private void tryExecuteContactUpdate() {
        if (this.pendingUpdateAction == null) {
            return;
        }

        new GleapUpdateSessionService().executeOnExecutor(GleapExecutor.SERIAL);
    }

    public GleapSessionProperties getPendingUpdateAction() {
        return pendingUpdateAction;
    }

    public void setPendingUpdateAction(GleapSessionProperties pendingUpdateAction) {
        this.pendingUpdateAction = pendingUpdateAction;
    }

    public GleapSessionProperties getPendingIdentificationAction() {
        synchronized (this) {
            return pendingIdentificationAction;
        }
    }

    public void setPendingIdentificationAction(GleapSessionProperties pendingIdentificationAction) {
        synchronized (this) {
            this.pendingIdentificationAction = pendingIdentificationAction;
        }
    }

    public static GleapSessionController getInstance() {
        return instance;
    }

    /**
     * Taken when a session request starts; see {@link #isCurrentGeneration(int)}.
     */
    int currentGeneration() {
        return identityGeneration.get();
    }

    /**
     * @return false when the session was cleared (logout) since {@code generation} was taken
     */
    boolean isCurrentGeneration(int generation) {
        return identityGeneration.get() == generation;
    }

    public void clearUserSession() {
        synchronized (this) {
            identityGeneration.incrementAndGet();
            store.clear();

            if (gleapSession != null) {
                unregisterPushMessageGroup(gleapSession.getHash());
            }
            pendingIdentificationAction = null;
            pendingUpdateAction = null;
            gleapSessionProperties = null;
            gleapSession = null;
            isSessionLoaded = false;
        }
    }

    /**
     * The API rejected an identify that started in {@code generation}: starts over without the
     * stored session and user, unless the app logged out meanwhile.
     */
    void clearRejectedIdentity(int generation) {
        synchronized (this) {
            if (isCurrentGeneration(generation)) {
                clearUserSession();
                setSessionLoaded(true);
            }
        }
    }

    /**
     * An identify that started in {@code generation} did not get through: it stays pending,
     * unless the app logged out or asked for another identify meanwhile.
     */
    void keepIdentifyPending(GleapSessionProperties identify, int generation) {
        synchronized (this) {
            if (isCurrentGeneration(generation) && pendingIdentificationAction == null) {
                pendingIdentificationAction = identify;
            }
        }
    }

    public void mergeUserSession(String id, String hash) {
        if (gleapSession == null) {
            gleapSession = new GleapSession(id, hash);
        } else {
            gleapSession.setHash(hash);
            gleapSession.setId(id);
        }
        store.putString("session_hash", hash);
        store.putString("session_id", id);
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
        store.putString("userId", gleapUser.getUserId());
        store.putString("name", gleapUser.getName());
        store.putString("email", gleapUser.getEmail());
        if (gleapUser.getPhone() != null) {
            store.putString("phone", gleapUser.getPhone());
        }
        if (gleapUser.getPlan() != null) {
            store.putString("plan", gleapUser.getPlan());
        }
        if (gleapUser.getCompanyId() != null) {
            store.putString("companyId", gleapUser.getCompanyId());
        }
        if (gleapUser.getCompanyName() != null) {
            store.putString("companyName", gleapUser.getCompanyName());
        }
        if (gleapUser.getAvatar() != null) {
            store.putString("avatar", gleapUser.getAvatar());
        }
        store.putFloat("value", (float) gleapUser.getValue());
        store.putFloat("sla", (float) gleapUser.getSla());
        if (gleapUser.getHash() != null && !gleapUser.getHash().equals("")) {
            store.putString("hash", gleapUser.getHash());
        }
        if (gleapUser.getCustomData() != null) {
            store.putString("customData", gleapUser.getCustomData().toString());
        }
    }

    public GleapSessionProperties getStoredGleapUser() {
        GleapSessionProperties gleapUser = new GleapSessionProperties();
        try {
            String userId = store.getString("userId", "");
            String userName = store.getString("name", "");
            String email = store.getString("email", "");
            String phone = store.getString("phone", "");
            String plan = store.getString("plan", "");
            String companyId = store.getString("companyId", "");
            String companyName = store.getString("companyName", "");
            String avatar = store.getString("avatar", "");
            String hash = store.getString("hash", "");
            double value = store.getFloat("value", 0);
            double sla = store.getFloat("sla", 0);

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
                String customDataString = store.getString("customData", "");
                customData = new JSONObject(customDataString);
            } catch (Exception ex) {
            }
            gleapUser.setCustomData(customData);
        } catch (Exception | Error ignore) {
        }
        return gleapUser;
    }

    public void processSessionActionResult(JSONObject result, boolean restartEventServices, boolean sendInitDelegate) {
        processSessionActionResult(result, restartEventServices, sendInitDelegate, currentGeneration());
    }

    /**
     * Applies the answer of a session request that started in {@code generation}; an answer
     * to a request from before a logout is dropped.
     */
    void processSessionActionResult(JSONObject result, boolean restartEventServices, boolean sendInitDelegate,
                                    int generation) {
        if (result == null) {
            return;
        }

        try {
            // An answer without a session (no or empty id and hash, e.g. from an error page)
            // never replaces the stored one.
            String id = sessionValue(result, "gleapId");
            String hash = sessionValue(result, "gleapHash");

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
                synchronized (this) {
                    if (!isCurrentGeneration(generation)) {
                        GleapLog.i("Dropped a session answer from before the logout");
                        return;
                    }

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
                }

                // Check if there are any other actions to complete.
                executePendingUpdates();

                // Notify about registration.
                registerPushMessageGroup(hash);

                if (restartEventServices) {
                    // Restart event service.
                    GleapEventService.getInstance().stop(false);
                    GleapEventService.getInstance().sessionStarted();
                    GleapEventService.getInstance().startWebSocketListener();
                }

                // Process push actions.
                Gleap.getInstance().processOpenPushActions();

                if (sendInitDelegate && GleapCallbacks.getInstance().getInitializationDoneCallback() != null) {
                    GleapCallbacks.getInstance().getInitializationDoneCallback().invoke();
                }
            }
        } catch (Exception exp) {}
    }

    private static String sessionValue(JSONObject result, String key) {
        Object value = result.opt(key);
        return value instanceof String && !((String) value).isEmpty() ? (String) value : null;
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

        // On the main thread, also without an activity on screen (e.g. an app start from a push).
        GleapMainThread.post(new Runnable() {
            @Override
            public void run() throws RuntimeException {
                if (GleapCallbacks.getInstance().getRegisterPushMessageGroupCallback() != null && userHash != null && !userHash.isEmpty()) {
                    GleapCallbacks.getInstance().getRegisterPushMessageGroupCallback().invoke("gleapuser-" + userHash);
                }
            }
        });
    }

    public void unregisterPushMessageGroup(String userHash) {
        this.lastRegisteredUserHash = null;

        // Unregister old user.
        if (GleapCallbacks.getInstance().getUnRegisterPushMessageGroupCallback() != null && userHash != null && !userHash.isEmpty()) {
            try {
                GleapMainThread.post(new Runnable() {
                    @Override
                    public void run() throws RuntimeException {
                        GleapCallbacks.getInstance().getUnRegisterPushMessageGroupCallback().invoke("gleapuser-" + userHash);
                    }
                });
            } catch (Exception ignore) {
            }
        }
    }
}
