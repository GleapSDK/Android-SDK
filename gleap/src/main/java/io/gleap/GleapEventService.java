package io.gleap;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;


import gleap.io.gleap.BuildConfig;
import java.util.Date;

import static io.gleap.DateUtil.dateToString;

class GleapEventService {
    // While the server takes the pings, one goes out every 3 s when events are queued.
    static final long PING_INTERVAL_MS = 3000;
    // One ping carries the oldest events up to these limits; the rest follows right after.
    static final int MAX_EVENTS_PER_PING = 100;
    static final int MAX_PING_BYTES = 256 * 1024;
    // A ping answer bigger than this is not read for capture requests.
    private static final int MAX_PING_ANSWER_BYTES = 64 * 1024;

    // Created with the class: getInstance() is called from several threads.
    private static volatile GleapEventService instance = new GleapEventService();
    private static GleapWebSocketListener webSocketListener;
    private boolean disableInAppNotifications = false;
    private final GleapEventQueue eventQueue = new GleapEventQueue();
    private final GleapPingBackoff backoff = new GleapPingBackoff();
    // A ping is waiting for the request thread or in flight: never more than one.
    private final AtomicBoolean pingInFlight = new AtomicBoolean();
    // The ping loop: start() and stop() count up, so ticks of an earlier loop do nothing.
    private int loop;
    private boolean running;
    private PingScheduler mainLooperScheduler;

    interface WebSocketFactory {
        GleapWebSocketListener create();
    }

    /**
     * Runs the ping loop's next tick after a delay, replacing the one pending.
     */
    interface PingScheduler {
        void schedule(Runnable tick, long delayMs);

        void cancel();
    }

    interface PingClock {
        // For the delays.
        long elapsedRealtime();

        // For Retry-After dates.
        long currentTimeMillis();
    }

    interface PingRandom {
        // Uniformly distributed in [0, 1).
        double next();
    }

    private static final WebSocketFactory OKHTTP_WEBSOCKETS = new WebSocketFactory() {
        @Override
        public GleapWebSocketListener create() {
            return new GleapWebSocketListener();
        }
    };

    private static final PingClock SYSTEM_CLOCK = new PingClock() {
        @Override
        public long elapsedRealtime() {
            return SystemClock.elapsedRealtime();
        }

        @Override
        public long currentTimeMillis() {
            return System.currentTimeMillis();
        }
    };

    private static final PingRandom SYSTEM_RANDOM = new PingRandom() {
        private final Random random = new Random();

        @Override
        public double next() {
            return random.nextDouble();
        }
    };

    private static volatile WebSocketFactory webSocketFactory = OKHTTP_WEBSOCKETS;
    // Tests only: replace the main thread's Handler, the request thread, the clock and the jitter.
    private static volatile PingScheduler testScheduler;
    private static volatile Executor pingExecutor = GleapExecutor.SERIAL;
    private static volatile PingClock clock = SYSTEM_CLOCK;
    private static volatile PingRandom random = SYSTEM_RANDOM;

    private GleapEventService() {
    }

    // Tests only; null restores the real WebSocket.
    static void setWebSocketFactoryForTesting(WebSocketFactory factory) {
        webSocketFactory = factory != null ? factory : OKHTTP_WEBSOCKETS;
    }

    // Tests only; null restores the default.
    static void setPingSchedulerForTesting(PingScheduler scheduler) {
        testScheduler = scheduler;
    }

    // Tests only; null restores the default.
    static void setPingExecutorForTesting(Executor executor) {
        pingExecutor = executor != null ? executor : GleapExecutor.SERIAL;
    }

    // Tests only; null restores the default.
    static void setPingClockForTesting(PingClock pingClock) {
        clock = pingClock != null ? pingClock : SYSTEM_CLOCK;
    }

    // Tests only; null restores the default.
    static void setPingRandomForTesting(PingRandom pingRandom) {
        random = pingRandom != null ? pingRandom : SYSTEM_RANDOM;
    }

    public static GleapEventService getInstance() {
        return instance;
    }

    // Tests only.
    static void resetForTesting() {
        GleapEventService previous = instance;
        if (previous != null) {
            previous.stopLoop();
        }
        instance = new GleapEventService();
        webSocketListener = null;
    }

    public void setDisableInAppNotifications(boolean disableInAppNotifications) {
        this.disableInAppNotifications = disableInAppNotifications;
    }

    public void startWebSocketListener() {
        clearWebsocketListener();

        webSocketListener = webSocketFactory.create();
        webSocketListener.connect();
    }

    /**
     * Queues the session start (and the page shown) for the next ping: once per session load or
     * identify, like iOS, not on every WebSocket (re)connect. They are kept when the queue is
     * full; the oldest other events make room.
     */
    void sessionStarted() {
        try {
            JSONObject sessionStarted = new JSONObject();
            sessionStarted.put("name", "sessionStarted");
            sessionStarted.put("date", dateToString(new Date()));
            eventQueue.addSessionStart(sessionStarted);

            Activity activity = ActivityUtil.getCurrentActivity();
            JSONObject pageView = new JSONObject();
            JSONObject page = new JSONObject();
            page.put("page", activity.getClass().getSimpleName());
            pageView.put("name", "pageView");
            pageView.put("data", page);
            pageView.put("date", dateToString(new Date()));
            eventQueue.addSessionStart(pageView);
        } catch (Exception ex) {
        }
    }

    /**
     * Starts sending the queued events (the WebSocket is connected): every 3 s while the server
     * takes them, right away while more are queued than one ping carries, and backing off while
     * it does not (see {@link GleapPingBackoff}). Only with a session, and one ping at a time.
     */
    public void start() {
        synchronized (this) {
            running = true;
            int current = ++loop;
            // A backoff that is still running also holds back the first ping of a new loop
            // (e.g. after a WebSocket reconnect).
            scheduleTick(current, backoff.remaining(clock.elapsedRealtime()));
        }
    }

    public void stop() {
        stop(true);
    }

    public void stop(Boolean clear) {
        if (clear) {
            eventQueue.clear();
        }
        clearWebsocketListener();
        stopLoop();
    }

    private synchronized void stopLoop() {
        running = false;
        loop++;
        scheduler().cancel();
    }

    private void clearWebsocketListener() {
        if (webSocketListener != null) {
            webSocketListener.destroy();
            webSocketListener = null;
        }
    }

    public void addEvent(JSONObject event) {
        eventQueue.add(event);
    }

    GleapEventQueue getEventQueue() {
        return eventQueue;
    }

    GleapPingBackoff getBackoff() {
        return backoff;
    }

    private PingScheduler scheduler() {
        PingScheduler scheduler = testScheduler;
        if (scheduler != null) {
            return scheduler;
        }
        synchronized (this) {
            if (mainLooperScheduler == null) {
                mainLooperScheduler = new MainLooperScheduler();
            }
            return mainLooperScheduler;
        }
    }

    // Holds the lock: start() and stop() decide which loop may schedule.
    private synchronized void scheduleTick(final int loopId, long delayMs) {
        if (!running || loopId != loop) {
            return;
        }
        scheduler().schedule(new Runnable() {
            @Override
            public void run() {
                tick(loopId);
            }
        }, Math.max(0, delayMs));
    }

    private synchronized boolean isCurrentLoop(int loopId) {
        return running && loopId == loop;
    }

    /**
     * One step of the ping loop (on the main thread): sends a ping when one is due, otherwise
     * schedules the next step. A ping schedules the next step when it is done.
     */
    private void tick(final int loopId) {
        if (!isCurrentLoop(loopId)) {
            return;
        }
        try {
            long wait = backoff.remaining(clock.elapsedRealtime());
            if (wait > 0) {
                scheduleTick(loopId, wait);
                return;
            }
            // A ping still in flight (from before a restart of the loop) is not joined by a
            // second one.
            if (currentSession() == null || eventQueue.isEmpty() || !pingInFlight.compareAndSet(false, true)) {
                scheduleTick(loopId, PING_INTERVAL_MS);
                return;
            }
            try {
                pingExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        long next = PING_INTERVAL_MS;
                        try {
                            next = sendQueuedEvents();
                        } catch (Exception ignore) {
                        } finally {
                            pingInFlight.set(false);
                        }
                        scheduleTick(loopId, next);
                    }
                });
            } catch (RuntimeException e) {
                pingInFlight.set(false);
                throw e;
            }
        } catch (Exception ignore) {
            scheduleTick(loopId, PING_INTERVAL_MS);
        }
    }

    /**
     * The session to ping with: loaded, with an id and a hash. Without one (not loaded yet, after
     * a logout or a rejected identify) nothing is sent and the events stay queued.
     */
    static GleapSession currentSession() {
        GleapSessionController controller = GleapSessionController.getInstance();
        if (controller == null || !controller.isSessionLoaded()) {
            return null;
        }
        GleapSession session = controller.getUserSession();
        if (session == null || isBlank(session.getId()) || isBlank(session.getHash())) {
            return null;
        }
        return session;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Sends the oldest queued events (at most {@link #MAX_EVENTS_PER_PING} and about
     * {@link #MAX_PING_BYTES} of JSON) in one ping. Any 2xx answer delivered them: exactly those
     * are removed, events tracked while the ping was in flight wait for the next one. After a
     * network error, 408, 429 or a 5xx they stay queued, any other error answer drops them;
     * either way the pings back off.
     *
     * @return the delay until the next ping
     */
    long sendQueuedEvents() {
        GleapSession session = currentSession();
        if (session == null) {
            return PING_INTERVAL_MS;
        }
        GleapEventQueue.Batch batch = eventQueue.nextBatch(MAX_EVENTS_PER_PING, MAX_PING_BYTES);
        if (batch.isEmpty()) {
            return PING_INTERVAL_MS;
        }

        long startedAt = clock.elapsedRealtime();
        PingResponse response = null;
        Exception error = null;
        try {
            response = postEvents(session, batch.toJSONArray());
        } catch (Exception e) {
            error = e;
        }
        long now = clock.elapsedRealtime();

        if (response != null && response.isDelivered()) {
            eventQueue.removeSent(batch.events);
            backoff.onSuccess();
            // The rest of a full queue goes right away, otherwise every 3 s.
            return batch.hasMore ? 0 : Math.max(0, PING_INTERVAL_MS - (now - startedAt));
        }

        // The server refused these events for good (e.g. 400, 401, 413): drop them, so they do
        // not hold back the rest of the queue. The next ping still backs off.
        if (response != null && !GleapPingBackoff.isRetryableStatus(response.status)) {
            eventQueue.removeSent(batch.events);
        }

        long retryAfterMs = response != null
                ? GleapPingBackoff.parseRetryAfterMs(response.retryAfter, clock.currentTimeMillis()) : -1;
        long delay = backoff.onFailure(now, retryAfterMs, random.next());
        GleapLog.w("Could not send the events (" + (response != null ? "HTTP " + response.status : error)
                + "), next try in " + delay + " ms");
        return delay;
    }

    /**
     * The answer to a ping.
     */
    static final class PingResponse {
        final int status;
        // The Retry-After header, or null.
        final String retryAfter;

        PingResponse(int status, String retryAfter) {
            this.status = status;
            this.retryAfter = retryAfter;
        }

        boolean isDelivered() {
            return status >= 200 && status < 300;
        }
    }

    /**
     * Sends the events (POST /sessions/ping) with the connect and read timeouts of all requests.
     *
     * @return the HTTP status and Retry-After; a network error throws
     */
    static PingResponse postEvents(GleapSession session, JSONArray events) throws IOException, JSONException {
        HttpURLConnection conn = GleapHttp.openReportPost("/sessions/ping", session, GleapHttp.READ_TIMEOUT_MS);
        try {
            JSONObject body = new JSONObject();
            body.put("events", events);
            body.put("time", PhoneMeta.calculateDurationInDouble());
            body.put("opened", Gleap.getInstance().isOpened());
            body.put("ws", true);
            body.put("sdkVersion", BuildConfig.VERSION_NAME);
            // What the SDK can capture for capture requests (screenshot, recording, logs).
            body.put("caps", GleapCapture.currentCapsJson());
            GleapHttp.writeJson(conn, body);

            int status = conn.getResponseCode();
            String retryAfter = conn.getHeaderField("Retry-After");
            if (GleapHttp.isSuccess(status)) {
                readCaptureRequests(conn);
            } else {
                closeBody(conn, status);
            }
            return new PingResponse(status, retryAfter);
        } finally {
            conn.disconnect();
        }
    }

    // Without a WebSocket the log requests come with the ping answer: {"cr": [...]}.
    private static void readCaptureRequests(HttpURLConnection conn) {
        InputStream stream = null;
        try {
            stream = conn.getInputStream();
            if (stream == null) {
                return;
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                if (out.size() > MAX_PING_ANSWER_BYTES) {
                    return;
                }
            }
            String text = new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8).trim();
            if (!text.startsWith("{")) {
                return;
            }
            JSONArray requests = new JSONObject(text).optJSONArray("cr");
            if (requests != null && requests.length() > 0) {
                GleapCaptureLogs.getInstance().onCaptureRequests(requests);
            }
        } catch (Exception ignore) {
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (Exception ignore) {
                }
            }
        }
    }

    private static void closeBody(HttpURLConnection conn, int status) {
        try {
            InputStream body = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (body != null) {
                body.close();
            }
        } catch (Exception ignore) {
        }
    }

    /**
     * Runs the ping loop on the main thread.
     */
    private static final class MainLooperScheduler implements PingScheduler {
        private final Handler handler = new Handler(Looper.getMainLooper());

        @Override
        public void schedule(Runnable tick, long delayMs) {
            handler.removeCallbacksAndMessages(null);
            handler.postDelayed(tick, delayMs);
        }

        @Override
        public void cancel() {
            handler.removeCallbacksAndMessages(null);
        }
    }

    private GleapChatMessage createComment(String outboundId, JSONObject messageData, String sendAt, String createdAt) throws Exception {
        String senderName = "";
        String profileImageUrl = "";
        String text = "";
        String type = "";
        String shareToken = "";
        String newsId = "";
        String checklistId = "";
        String coverImageUrl = "";
        String nextStepTitle = "";
        int currentStep = 0;
        int totalSteps = 0;
        boolean senderIsBot = false;

        if (messageData.has("type")) {
            type = messageData.getString("type");
        }

        if (messageData.has("text")) {
            text = messageData.getString("text");
        }

        if (messageData.has("sender")) {
            JSONObject sender = messageData.getJSONObject("sender");

            if (sender.has("name")) {
                senderName = sender.getString("name");
            }

            if (sender.has("profileImageUrl")) {
                profileImageUrl = sender.getString("profileImageUrl");
            }

            if (sender.has("isBot")) {
                senderIsBot = sender.optBoolean("isBot", false);
            }
        }

        if (messageData.has("conversation")) {
            JSONObject conversation = messageData.getJSONObject("conversation");
            if (conversation.has("shareToken")) {
                shareToken = conversation.getString("shareToken");
            }
        }

        if (messageData.has("news")) {
            JSONObject conversation = messageData.getJSONObject("news");
            if (conversation.has("id")) {
                newsId = conversation.getString("id");
            }
        }

        if (messageData.has("checklist")) {
            JSONObject conversation = messageData.getJSONObject("checklist");
            if (conversation.has("id")) {
                checklistId = conversation.getString("id");
            }
        }

        if (messageData.has("coverImageUrl")) {
            coverImageUrl = messageData.getString("coverImageUrl");
        }

        if (messageData.has("currentStep")) {
            currentStep = messageData.getInt("currentStep");
        }

        if (messageData.has("totalSteps")) {
            totalSteps = messageData.getInt("totalSteps");
        }

        if (messageData.has("nextStepTitle")) {
            nextStepTitle = messageData.getString("nextStepTitle");
        }

        GleapSender sender = new GleapSender(senderName, profileImageUrl, senderIsBot);
        return new GleapChatMessage(outboundId, type, text, shareToken, sender, newsId, coverImageUrl, currentStep,
                totalSteps, nextStepTitle, checklistId, sendAt, createdAt);
    }

    public void processEventData(JSONObject data) throws Exception {
        if (data == null) {
            return;
        }

        if (data.has("u")) {
            GleapMainThread.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        GleapOverlayManager.getInstance().setMessageCounter(data.getInt("u"));
                    } catch (JSONException e) {
                    }
                }
            });
        }

        if (data.has("a") && data.get("a") instanceof JSONArray) {
            // Like while the widget is open, nothing pops up while the customer captures the app.
            if (Gleap.getInstance().isOpened() || GleapCaptureCoordinator.isSessionActive()) {
                return;
            }

            JSONArray actions = data.getJSONArray("a");

            for (int i = 0; i < actions.length(); i++) {
                JSONObject currentAction = actions.getJSONObject(i);
                if (currentAction.has("actionType")) {
                    if (currentAction.getString("actionType").contains("notification")) {
                        // In app notification.
                        JSONObject messageData = null;

                        try {
                            messageData = currentAction.getJSONObject("data");
                        } catch (JSONException e) {

                        } catch (Exception e) {

                        }

                        // Check if the notification is part of a checklist and has popupType "widget".
                        if (messageData != null && messageData.has("checklist") &&
                                messageData.getJSONObject("checklist").getString("popupType").equals("widget")) {

                            // Open the checklist if the popupType is "widget".
                            Gleap.getInstance().openChecklist(messageData.getJSONObject("checklist").getString("id"),
                                    true);
                        } else {
                            // In app notification.
                            if (!this.disableInAppNotifications) {
                                GleapMainThread.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            String outboundId = "";
                                            if (currentAction.has("outbound")) {
                                                outboundId = currentAction.getString("outbound");
                                            }
                                            JSONObject data = currentAction.getJSONObject("data");
                                            GleapChatMessage comment = createComment(outboundId, data, currentAction.optString("sendAt", ""), currentAction.optString("createdAt", ""));
                                            GleapOverlayManager.getInstance().addNotification(comment, null);
                                        } catch (JSONException e) {

                                        } catch (Exception e) {

                                        }
                                    }
                                });
                            }
                        }
                    } else if (currentAction.getString("format").contains("survey")) {
                        JSONObject jsonObject = new JSONObject();
                        try {
                            jsonObject.put("isSurvey", true);
                            jsonObject.put("hideBackButton", true);
                            jsonObject.put("format", currentAction.getString("format"));
                            jsonObject.put("flow", currentAction.getString("actionType"));
                        } catch (Exception ex) {
                        }

                        GleapMainThread.post(new Runnable() {
                            @Override
                            public void run() {
                                SurveyType surveyType = SurveyType.SURVEY;
                                try {
                                    if (currentAction.getString("format").contains("survey_full")) {
                                        surveyType = SurveyType.SURVEY_FULL;
                                    }
                                } catch (JSONException e) {

                                }

                                // Check if it is open
                                GleapActionQueueHandler.getInstance()
                                        .addActionMessage(new GleapAction("start-survey", jsonObject));
                                Gleap.getInstance().open(surveyType);
                            }
                        });
                    }
                    if (currentAction.getString("actionType").contains("banner")) {
                        GleapMainThread.post(new Runnable() {
                            @Override
                            public void run() {
                                GleapOverlayManager.getInstance().showBanner(currentAction, null);
                            }
                        });
                    } else if (currentAction.getString("actionType").contains("modal")) {
                        GleapMainThread.post(new Runnable() {
                            @Override
                            public void run() {
                                // Get config from current action.
                                try {
                                    JSONObject config = currentAction.getJSONObject("config");
                                    GleapOverlayManager.getInstance().showModal(config, null);
                                } catch (Exception e) {
                                    // Do nothing.
                                }
                            }
                        });
                    } else {
                        // Unknown action.
                    }
                }
            }
        }
    }
}
