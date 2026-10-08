package io.gleap;

import android.os.AsyncTask;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Identifies the session's contact (POST /sessions/identify) with the pending identify action.
 * Started by the SDK; there is no need to run it from the app.
 * <p>
 * Only an explicit rejection by the API clears the stored session and user. When the identify
 * cannot get through (offline, timeouts, rate limits, server errors) the session is kept and the
 * identify stays pending: it runs again with the next session load or when the network comes
 * back.
 */
public class GleapIdentifyService extends AsyncTask<Void, Void, Integer> {
    private static final String URL_POSTFIX = "/sessions/identify";
    // Identifies running now; see GleapSessionController#isIdentifyInFlight.
    private static final AtomicInteger running = new AtomicInteger();

    /**
     * @return true while an identify runs (until its answer is applied)
     */
    static boolean isRunning() {
        return running.get() > 0;
    }

    @Override
    protected Integer doInBackground(Void... voids) {
        running.incrementAndGet();
        try {
            return identify();
        } finally {
            running.decrementAndGet();
        }
    }

    private Integer identify() {
        try {
            final GleapSessionController controller = GleapSessionController.getInstance();
            if (controller == null) {
                return 200;
            }
            // A logout (clearIdentity) from now on cancels this identify.
            final int generation = controller.currentGeneration();

            // If session is not ready yet, wait for session to load.
            GleapSession gleapSession = controller.getUserSession();
            if(gleapSession == null) {
                return 200;
            }

            GleapSessionProperties pendingAction = controller.getPendingIdentificationAction();
            if (pendingAction == null) {
                // Nothing to do.
                return 200;
            }

            // Reset the pending contact identification action.
            controller.setPendingIdentificationAction(null);
            final boolean forced = controller.takeForcedIdentify();

            // Verify if we need to run the identify call - check if already same data. Sent
            // anyway to get or refresh the file session of authenticated conversation files
            // (checked against the session as it is now, e.g. after the session start).
            GleapSessionProperties oldProps = controller.getGleapUserSession();
            if (!forced && !controller.needsFileAccessIdentify(pendingAction)
                    && oldProps != null && oldProps.equals(pendingAction)) {
                // Old equals new, nothing to do.
                return 200;
            }

            // Get payload to send.
            JSONObject jsonObject = pendingAction.getJSONPayload();
            
            // Add platform and deviceType
            jsonObject.put("platform", "android");
            jsonObject.put("deviceType", GleapHelper.getDeviceType());

            // Check if we do have an userId.
            if (!jsonObject.has( "userId")) {
                return 200;
            }

            final GleapSession session = gleapSession;
            final JSONObject payload = jsonObject;
            final boolean[] rejected = {false};
            boolean answered = GleapRetry.withBackoff("Identify request", Exception.class, new GleapRetry.Attempt() {
                @Override
                public void run() throws Exception {
                    rejected[0] = !performIdentifyRequest(session, payload, generation);
                }
            });

            if (answered && rejected[0]) {
                // The API rejected the identify (e.g. an invalid user hash): start over without
                // the stored session and user.
                controller.clearRejectedIdentity(generation);
            } else if (!answered) {
                // Could not get through: keep the session and try again later.
                controller.keepIdentifyPending(pendingAction, generation, forced);
            }
        } catch (Exception ignored) {}

        return 200;
    }

    /**
     * @return false when the API rejected the identify
     * @throws Exception when the identify did not get through; it is retried
     */
    private boolean performIdentifyRequest(GleapSession gleapSession, JSONObject jsonObject, int generation) throws Exception {
        HttpURLConnection conn = GleapHttp.openSessionPost(URL_POSTFIX, gleapSession);
        GleapHttp.writeJson(conn, jsonObject);

        int status = conn.getResponseCode();
        if (status >= 200 && status < 300) {
            JSONObject result = GleapHttp.readLastJsonLine(conn.getInputStream());
            GleapSessionController.getInstance().processSessionActionResult(result, true, false, generation, true);
            return true;
        }

        if (isRejection(status, readErrorBody(conn))) {
            return false;
        }
        throw new IOException("Identify answered with HTTP " + status);
    }

    /**
     * The API rejects an identify with a 4xx answer and its JSON error body. Timeouts (408), rate
     * limits (429) and answers without a JSON body (e.g. from a proxy) are not rejections.
     */
    static boolean isRejection(int status, String errorBody) {
        if (status < 400 || status >= 500 || status == 408 || status == 429 || errorBody == null) {
            return false;
        }
        try {
            new JSONObject(errorBody);
            return true;
        } catch (Exception notJson) {
            return false;
        }
    }

    private static String readErrorBody(HttpURLConnection conn) {
        try (InputStream stream = conn.getErrorStream()) {
            if (stream == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return null;
        }
    }
}
