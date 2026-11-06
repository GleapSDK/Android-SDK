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
    // The last identify of the app (in memory only): identifies again with it to get or refresh
    // the file session of authenticated conversation files (see GleapFileAccess).
    private GleapSessionProperties lastIdentify;
    // The next identify is sent even if the user data did not change (a file session refresh).
    private boolean forceNextIdentify;
    // Where the session and the identified user are kept between app starts.
    private final KeyValueStore store;
    // Counts the logouts (clearUserSession). A request started before a logout neither applies
    // its result nor acts on its failure: it must not bring the cleared session or user back.
    private final AtomicInteger identityGeneration = new AtomicInteger();
    // The grant a signed identify returned (identity.grant), the session it was issued to and
    // its expiry (ms, 0 = unknown). Kept in the store too, so an app restart stays signed until
    // it expires. Sent as Gleap-Grant only with that session's id.
    private volatile String grant;
    private volatile String grantSessionId;
    // True while the store holds a grant, so an answer without one removes nothing it never wrote.
    private volatile boolean grantStored;
    private volatile long grantExpiresAtMs;
    // How long before its expiry the grant is renewed: 5 minutes, at most half its lifetime.
    private volatile long grantRenewMarginMs = GRANT_RENEW_MARGIN_MS;
    // Bumped whenever the grant changes: only the latest scheduled renewal runs.
    private final AtomicInteger grantRenewal = new AtomicInteger();
    private long lastGrantRenewalMs;
    // The renewal posted to the main thread (one at a time).
    private Runnable grantRenewalTask;
    // identity.mode of the last session answer that carried identity ("signed" or null);
    // Classic projects send no identity.
    private volatile String identityMode;
    // The session a signed identify was sent for while it had no grant (memory only): a
    // returning user on a project that switched to Signed gets one identify per app start and
    // session, never a loop.
    private String signedGrantRequestedFor;
    // A grant is renewed (the last signed identify sent again) this long before it expires.
    static final long GRANT_RENEW_MARGIN_MS = 5 * 60 * 1000;
    private static final long GRANT_RENEW_MIN_INTERVAL_MS = 60 * 1000;

    private GleapSessionController(Application application) {
        // The stored state is loaded on Gleap's request thread (see initialize), not here.
        this(GleapPreferencesHelper.lazy(application), false);
    }

    GleapSessionController(KeyValueStore store) {
        this(store, true);
    }

    GleapSessionController(KeyValueStore store, boolean loadStoredStateNow) {
        this.store = store;
        if (loadStoredStateNow) {
            loadStoredState();
        }
    }

    /**
     * Starts the controller. The stored session and user are read on Gleap's request thread,
     * first in line: every value is decrypted through the Android Keystore, which is slow on
     * low-end or busy devices and blocked the thread that called {@code Gleap.initialize} (an
     * ANR when the app's startup waits for it). The session start, identify and the other
     * requests are queued behind it on the same thread, so they see the stored state.
     */
    public static GleapSessionController initialize(Application application) {
        synchronized (GleapSessionController.class) {
            if (instance == null) {
                final GleapSessionController controller = new GleapSessionController(application);
                instance = controller;
                GleapExecutor.SERIAL.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            controller.loadStoredState();
                        } catch (Error | Exception error) {
                            GleapErrors.report(error, "loadStoredState");
                        }
                    }
                });
            }
            return instance;
        }
    }

    /**
     * Reads the session and the identified user of the previous app start from the store. The
     * reads run outside the lock (they decrypt through the keystore); a logout meanwhile wins,
     * so a cleared session is never brought back.
     */
    void loadStoredState() {
        int generation = currentGeneration();

        String id = store.getString("session_id", "");
        String hash = store.getString("session_hash", "");
        String storedGrant = store.getString("grant", "");
        String storedGrantSessionId = store.getString("grant_session_id", "");
        grantStored = !storedGrant.isEmpty();
        GleapSessionProperties storedUser = getStoredGleapUser();

        synchronized (this) {
            if (!isCurrentGeneration(generation)) {
                return;
            }
            if (!id.equals("") && !hash.equals("")) {
                gleapSession = new GleapSession(id, hash);
            }
            gleapSessionProperties = storedUser;
            if (!storedGrant.isEmpty() && storedGrantSessionId.equals(id) && grant == null) {
                setGrantInMemory(storedGrant, storedGrantSessionId);
            }
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
            if (pendingIdentificationAction != null) {
                lastIdentify = pendingIdentificationAction;
            }
        }
    }

    /**
     * Whether an identify is queued or running: until it settles, the session may still be the
     * previous (e.g. guest) one.
     */
    boolean isIdentifyInFlight() {
        return getPendingIdentificationAction() != null || GleapIdentifyService.isRunning();
    }

    /**
     * Taken by the identify service with the pending identify: true when it must be sent even
     * if the user data did not change.
     */
    boolean takeForcedIdentify() {
        synchronized (this) {
            boolean forced = forceNextIdentify;
            forceNextIdentify = false;
            return forced;
        }
    }

    /**
     * @return true when the app identified its own user (an identify sent or on its way, or a
     * session that belongs to a user id); the widget never swaps such a session
     */
    boolean hasIdentifiedUser() {
        synchronized (this) {
            return hasUserId(lastIdentify) || hasUserId(pendingIdentificationAction)
                    || hasUserId(gleapSessionProperties);
        }
    }

    private static boolean hasUserId(GleapSessionProperties properties) {
        return properties != null && properties.getUserId() != null && !properties.getUserId().isEmpty();
    }

    /**
     * @return true when the last identify has a user hash, i.e. can get a file session
     */
    boolean hasIdentifyHash() {
        synchronized (this) {
            return lastIdentify != null && lastIdentify.getHash() != null && !lastIdentify.getHash().isEmpty();
        }
    }

    /**
     * An identify with a user hash must be sent, even with unchanged user data, while the
     * project requires a file session and the current session has no valid one.
     */
    boolean needsFileAccessIdentify(GleapSessionProperties identify) {
        GleapSession session = gleapSession;
        return identify != null && identify.getHash() != null && !identify.getHash().isEmpty()
                && session != null && session.isAuthenticatedFilesRequired()
                && session.validFileAccessToken(0) == null;
    }

    /**
     * Queues the last identify (with a user hash) to be sent again, which gets a new file
     * session. An identify the app queued meanwhile is sent instead.
     *
     * @return false when there is no identify with a user hash
     */
    boolean requestFileAccessIdentify() {
        synchronized (this) {
            if (lastIdentify == null || lastIdentify.getHash() == null || lastIdentify.getHash().isEmpty()) {
                return false;
            }
            if (pendingIdentificationAction == null) {
                pendingIdentificationAction = lastIdentify;
            }
            forceNextIdentify = true;
            return true;
        }
    }

    /**
     * Refreshes the file session now: {@link #requestFileAccessIdentify()} and sends it.
     */
    void refreshFileAccess() {
        if (requestFileAccessIdentify()) {
            executePendingUpdates();
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
            // Revoke the file session before the session that holds it is dropped.
            GleapFileAccess.onLogout(gleapSession != null ? gleapSession.getFileAccessToken() : null);
            store.clear();
            grantStored = false;

            if (gleapSession != null) {
                unregisterPushMessageGroup(gleapSession.getHash());
                gleapSession.setFileAccess(null, null);
            }
            pendingIdentificationAction = null;
            lastIdentify = null;
            forceNextIdentify = false;
            // The identity token goes with lastIdentify and the session properties; the auth
            // tokens go too. The JWT provider stays.
            GleapAuthTokens.getInstance().clear();
            pendingUpdateAction = null;
            gleapSessionProperties = null;
            gleapSession = null;
            isSessionLoaded = false;
            identityMode = null;
            // The grant goes with the session (store.clear removed the stored copy).
            setGrantInMemory(null, null);
        }
    }

    /**
     * The server answered identity_mismatch (the session belongs to another user): drops the
     * local session, its grant and the stored user, starts a fresh visitor session and sends
     * {@code identify} once more on it; the server merges that visitor into the token's
     * contact. The identify, the JWT provider and the auth tokens stay. A second mismatch
     * starts over as a guest. Nothing happens when the app logged out meanwhile.
     */
    void restartForIdentify(GleapSessionProperties identify, int generation) {
        synchronized (this) {
            if (!isCurrentGeneration(generation)) {
                return;
            }
            if (identify.identityMismatchRetried) {
                GleapLog.w("The identity was refused twice (identity_mismatch). Starting over as a guest.");
                clearUserSession();
                setSessionLoaded(true);
                return;
            }
            identityGeneration.incrementAndGet();
            GleapFileAccess.onLogout(gleapSession != null ? gleapSession.getFileAccessToken() : null);
            if (gleapSession != null) {
                unregisterPushMessageGroup(gleapSession.getHash());
                gleapSession.setFileAccess(null, null);
            }
            store.clear();
            grantStored = false;
            setGrantInMemory(null, null);
            identityMode = null;
            gleapSessionProperties = null;
            gleapSession = null;
            isSessionLoaded = false;
            pendingUpdateAction = null;
            GleapSessionProperties retry = identify.withUserJwt(null);
            retry.identityMismatchRetried = true;
            retry.jwtRetried = identify.jwtRetried;
            // A newer identify the app queued meanwhile is sent instead.
            if (pendingIdentificationAction == null) {
                pendingIdentificationAction = retry;
            }
            forceNextIdentify = true;
        }
        // The fresh session's answer sends the identify queued above.
        GleapMainThread.post(new Runnable() {
            @Override
            public void run() {
                new GleapBaseSessionService().executeOnExecutor(GleapExecutor.SERIAL);
            }
        });
    }

    /**
     * @return the grant issued to {@code sessionId}, or null. An expired grant is still sent:
     * the server may extend it once, otherwise it treats the request as unsigned.
     */
    String getGrant(String sessionId) {
        String current = grant;
        return current != null && sessionId != null && sessionId.equals(grantSessionId) ? current : null;
    }

    /**
     * @return true when {@code sessionId} has a grant that expires within {@code marginMs}
     */
    boolean grantExpiresWithin(String sessionId, long marginMs) {
        return getGrant(sessionId) != null && grantExpiresAtMs > 0
                && grantExpiresAtMs - System.currentTimeMillis() < Math.min(marginMs, grantRenewMarginMs);
    }

    /**
     * True when the project runs Signed ({@code identity.mode}), session {@code sessionId}
     * belongs to {@code userId} and has no grant, and no signed identify was sent for it yet in
     * this app start.
     */
    private boolean signedGrantMissing(String sessionId, String userId) {
        synchronized (this) {
            return "signed".equals(identityMode) && sessionId != null && !sessionId.isEmpty()
                    && userId != null && !userId.isEmpty()
                    && !sessionId.equals(signedGrantRequestedFor) && getGrant(sessionId) == null;
        }
    }

    /**
     * Taken by the identify service before it sends (or skips as unchanged) {@code identify}
     * with a user hash: true when the identified session has no grant on a Signed project and
     * this identify is the one attempt to get it. Marks the session, so it is asked once.
     */
    boolean claimSignedGrantRequest(GleapSessionProperties identify) {
        synchronized (this) {
            GleapSession session = gleapSession;
            if (identify == null || identify.getHash() == null || identify.getHash().isEmpty() || session == null) {
                return false;
            }
            String sessionUserId = gleapSessionProperties != null ? gleapSessionProperties.getUserId() : null;
            if (!signedGrantMissing(session.getId(), sessionUserId)) {
                return false;
            }
            signedGrantRequestedFor = session.getId();
            return true;
        }
    }

    private void setGrantInMemory(String value, String sessionId) {
        if (value == null || value.isEmpty() || sessionId == null || sessionId.isEmpty()) {
            grant = null;
            grantSessionId = null;
            grantExpiresAtMs = 0;
        } else {
            Long exp = GleapUserJwt.expirationOf(value);
            Long iat = GleapUserJwt.issuedAtOf(value);
            grant = value;
            grantSessionId = sessionId;
            grantExpiresAtMs = exp != null ? exp * 1000 : 0;
            // A short session is renewed at half its lifetime, not the moment it arrives.
            long issuedAtMs = iat != null ? iat * 1000 : System.currentTimeMillis();
            long lifetimeMs = grantExpiresAtMs > 0 ? grantExpiresAtMs - issuedAtMs : 0;
            grantRenewMarginMs = lifetimeMs > 0 ? Math.min(GRANT_RENEW_MARGIN_MS, lifetimeMs / 2) : GRANT_RENEW_MARGIN_MS;
        }
        grantRenewal.incrementAndGet();
    }

    /**
     * Takes the grant from a session answer for session {@code id}: {@code identity.grant} is
     * stored, an {@code identity} without one drops it (an unsigned identify ends the signed
     * session), an answer without {@code identity} keeps it while it is the same session.
     *
     * @return true when the grant changed
     */
    private boolean applyGrant(JSONObject result, String id) {
        String before = grant;
        JSONObject identity = result.optJSONObject("identity");
        if (identity != null) {
            Object mode = identity.opt("mode");
            identityMode = mode instanceof String ? (String) mode : null;
            Object value = identity.opt("grant");
            if (value instanceof String && !((String) value).isEmpty()) {
                setGrantInMemory((String) value, id);
            } else {
                setGrantInMemory(null, null);
            }
        } else if (grant != null && !id.equals(grantSessionId)) {
            setGrantInMemory(null, null);
        }
        if (grant == null) {
            if (grantStored) {
                store.remove("grant");
                store.remove("grant_session_id");
                grantStored = false;
            }
        } else if (!grant.equals(before)) {
            store.putString("grant", grant);
            store.putString("grant_session_id", grantSessionId);
            grantStored = true;
        }
        return !sameValue(before, grant);
    }

    /**
     * Schedules the grant's renewal (the last signed identify sent again) shortly before it
     * expires.
     */
    private void scheduleGrantRenewal() {
        // min(5 minutes, half the grant's lifetime) before it expires.
        postGrantRenewal(grantExpiresAtMs - grantRenewMarginMs - System.currentTimeMillis(), true);
    }

    /**
     * Posts the renewal check in {@code delayMs}, replacing the one posted before.
     */
    private void postGrantRenewal(long delayMs, final boolean rearm) {
        final int schedule = grantRenewal.get();
        Runnable task;
        synchronized (this) {
            GleapMainThread.removeCallbacks(grantRenewalTask);
            grantRenewalTask = null;
            if (grant == null || grantExpiresAtMs <= 0) {
                return;
            }
            task = new Runnable() {
                @Override
                public void run() {
                    if (grantRenewal.get() == schedule) {
                        refreshGrantIfExpiring(rearm);
                    }
                }
            };
            grantRenewalTask = task;
        }
        GleapMainThread.postDelayed(task, Math.max(0, delayMs));
    }

    /**
     * Sends the last signed identify again (through the JWT provider when its token is about to
     * expire) when the current session's grant has expired or expires within 5 minutes; at most
     * once a minute. Also run when the app comes to the foreground: timers do not run while the
     * device sleeps.
     */
    void refreshGrantIfExpiring() {
        refreshGrantIfExpiring(true);
    }

    /**
     * {@link #refreshGrantIfExpiring()}; {@code rearm}: when the once-a-minute limit holds it
     * back, check again once the minute is over (a short grant renewed at half its lifetime
     * would lapse otherwise). That second check does not re-arm.
     */
    private void refreshGrantIfExpiring(boolean rearm) {
        GleapSession session = gleapSession;
        if (session == null || !grantExpiresWithin(session.getId(), GRANT_RENEW_MARGIN_MS)) {
            return;
        }
        long waitMs = 0;
        synchronized (this) {
            long now = System.currentTimeMillis();
            if (now - lastGrantRenewalMs < GRANT_RENEW_MIN_INTERVAL_MS) {
                waitMs = GRANT_RENEW_MIN_INTERVAL_MS - (now - lastGrantRenewalMs);
            } else {
                lastGrantRenewalMs = now;
            }
        }
        if (waitMs > 0) {
            if (rearm) {
                postGrantRenewal(waitMs + 1000, false);
            }
            return;
        }
        // The same forced resend of the last signed identify that refreshes the file session.
        refreshFileAccess();
    }

    /**
     * Queues {@code identify} again, now with a token from the JWT provider (or the checked old
     * one): only while nothing changed since {@code sentHash} was sent (no logout, no other
     * identify of the app, none queued). With a fresh token it also becomes the last identify.
     */
    void resendIdentify(GleapSessionProperties identify, String sentHash, int generation, boolean forced,
                        boolean tokenRenewed) {
        synchronized (this) {
            if (!isCurrentGeneration(generation) || pendingIdentificationAction != null
                    || lastIdentify == null || !sameValue(lastIdentify.getHash(), sentHash)) {
                return;
            }
            pendingIdentificationAction = identify;
            if (tokenRenewed) {
                lastIdentify = identify.withUserJwt(null);
            }
            forceNextIdentify = forceNextIdentify || forced;
        }
        executePendingUpdates();
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
        keepIdentifyPending(identify, generation, false);
    }

    /**
     * {@link #keepIdentifyPending(GleapSessionProperties, int)}; a forced identify (file
     * session refresh) stays forced.
     */
    void keepIdentifyPending(GleapSessionProperties identify, int generation, boolean forced) {
        synchronized (this) {
            if (isCurrentGeneration(generation) && pendingIdentificationAction == null) {
                pendingIdentificationAction = identify;
                forceNextIdentify = forceNextIdentify || forced;
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
        // Like the email: a session without a phone drops the one of a previous contact.
        store.putString("phone", gleapUser.getPhone());
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
        // An identity token (JWT) is kept in memory only.
        if (gleapUser.getHash() != null && !gleapUser.getHash().equals("") && !GleapUserJwt.isUserJwt(gleapUser.getHash())) {
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
        processSessionActionResult(result, restartEventServices, sendInitDelegate, generation, false);
    }

    /**
     * {@link #processSessionActionResult(JSONObject, boolean, boolean, int)}; {@code fromIdentify}
     * for the answer to an identify.
     */
    void processSessionActionResult(JSONObject result, boolean restartEventServices, boolean sendInitDelegate,
                                    int generation, boolean fromIdentify) {
        processSessionActionResult(result, restartEventServices, sendInitDelegate, generation, fromIdentify, null);
    }

    /**
     * {@link #processSessionActionResult(JSONObject, boolean, boolean, int, boolean)};
     * {@code sentHash} is the user hash the identify carried. An identity token (JWT) the server
     * accepted is kept with the session properties (in memory only), so the same token is not
     * sent again while a new one is.
     */
    void processSessionActionResult(JSONObject result, boolean restartEventServices, boolean sendInitDelegate,
                                    int generation, boolean fromIdentify, String sentHash) {
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
                GleapSession session;
                synchronized (this) {
                    if (!isCurrentGeneration(generation)) {
                        GleapLog.i("Dropped a session answer from before the logout");
                        return;
                    }

                    String previousHash = (gleapSession != null) ? gleapSession.getHash() : null;
                    if (previousHash != null && !previousHash.equalsIgnoreCase(hash)) {
                        unregisterPushMessageGroup(previousHash);
                    }

                    // The file session before this answer.
                    String previousId = gleapSession != null ? gleapSession.getId() : null;
                    String previousUserId = this.gleapSessionProperties != null ? this.gleapSessionProperties.getUserId() : null;
                    String previousUserJwt = this.gleapSessionProperties != null ? this.gleapSessionProperties.getHash() : null;
                    String previousToken = gleapSession != null ? gleapSession.getFileAccessToken() : null;
                    String previousExpiresAt = gleapSession != null ? gleapSession.getFileAccessExpiresAt() : null;

                    mergeUserSession(id, hash);
                    setSessionLoaded(true);
                    gleapSession = getUserSession();

                    // Update current session in session controller.
                    GleapSessionProperties gleapSessionProperties = GleapSessionProperties.fromJSONObject(result);
                    setGleapUserSession(gleapSessionProperties);
                    // The accepted identity token, in memory only (set after it was stored).
                    String userJwt = fromIdentify ? sentHash
                            : (id.equals(previousId) && sameValue(gleapSessionProperties.getUserId(), previousUserId) ? previousUserJwt : null);
                    if (GleapUserJwt.isUserJwt(userJwt)) {
                        gleapSessionProperties.setHash(userJwt);
                    }

                    // Only a verified identify answers with a file session. Other answers (session
                    // start, contact updates) keep the one we have, but never across an identity
                    // change and never an expired one.
                    String token = sessionValue(result, "fileAccessToken");
                    String expiresAt = token != null ? sessionValue(result, "fileAccessExpiresAt") : null;
                    if (token == null && id.equals(previousId)
                            && sameValue(gleapSessionProperties.getUserId(), previousUserId)
                            && GleapFileAccess.isValid(previousToken, previousExpiresAt, 0)) {
                        token = previousToken;
                        expiresAt = previousExpiresAt;
                    }
                    gleapSession.setFileAccess(token, expiresAt);
                    gleapSession.setAuthenticatedFilesRequired(result.optBoolean("authenticatedFilesRequired", false));
                    session = gleapSession;

                    // The grant of a signed identify.
                    applyGrant(result, id);
                    scheduleGrantRenewal();

                    // A returning user on a project that switched to Signed: the session is
                    // identified but has no grant, and the app's identify of this start (or
                    // the hash kept from the last one) would be skipped as unchanged. It is
                    // queued once; the identify service sends it (see claimSignedGrantRequest).
                    if (!fromIdentify && pendingIdentificationAction == null && !GleapIdentifyService.isRunning()
                            && signedGrantMissing(id, gleapSessionProperties.getUserId())) {
                        GleapSessionProperties held = null;
                        if (lastIdentify != null && lastIdentify.getHash() != null && !lastIdentify.getHash().isEmpty()
                                && gleapSessionProperties.getUserId().equals(lastIdentify.getUserId())) {
                            held = lastIdentify;
                        } else if (previousUserJwt != null && !previousUserJwt.isEmpty()
                                && gleapSessionProperties.getUserId().equals(previousUserId)) {
                            held = gleapSessionProperties.withUserJwt(null);
                            held.setHash(previousUserJwt);
                            if (lastIdentify == null) {
                                lastIdentify = held;
                            }
                        }
                        if (held != null) {
                            pendingIdentificationAction = held;
                        }
                    }
                }

                GleapFileAccess.onSessionAnswer(this, session, fromIdentify);
                // An open widget gets the session right away: the identified contact (the
                // messenger skips the survey questions it already knows the answer to), a
                // new file session.
                GleapMainActivity.refreshSession();

                // Check if there are any other actions to complete.
                executePendingUpdates();

                // Notify about registration.
                registerPushMessageGroup(hash);

                if (restartEventServices) {
                    // Queue the session start and keep the events flowing. The realtime socket
                    // stays open when gleapId, gleapHash and apiKey are unchanged; otherwise it
                    // is replaced (a session start followed by an identify opens one socket).
                    GleapEventService.getInstance().sessionStarted();
                    GleapEventService.getInstance().ensureStarted();
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

    private static boolean sameValue(String a, String b) {
        return a == null ? b == null : a.equals(b);
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
