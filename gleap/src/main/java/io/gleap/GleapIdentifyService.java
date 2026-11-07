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
 * <p>
 * An identity token (JWT) refused with {@code token_expired} or {@code token_invalid} keeps the
 * session and the user: an expired one is renewed through the JWT provider and the identify sent
 * once more. A token that expires within 5 minutes is renewed before it is sent again.
 * <p>
 * {@code identity_mismatch} (the session belongs to another user) drops the local session and
 * sends the identify once more on a fresh visitor session. A signed identify's answer carries the
 * grant ({@code identity.grant}), which {@link GleapSessionController} keeps.
 */
public class GleapIdentifyService extends AsyncTask<Void, Void, Integer> {
    private static final String URL_POSTFIX = "/sessions/identify";
    // Identifies running now; see GleapSessionController#isIdentifyInFlight.
    private static final AtomicInteger running = new AtomicInteger();

    // Outcomes of the identify request.
    static final int ACCEPTED = 0;
    static final int REJECTED = 1;
    static final int TOKEN_EXPIRED = 2;
    static final int TOKEN_INVALID = 3;
    static final int IDENTITY_MISMATCH = 4;

    // A signed identify landed: the auth tokens follow once this identify stopped running.
    private boolean sendAuthTokens;

    /**
     * @return true while an identify runs (until its answer is applied)
     */
    static boolean isRunning() {
        return running.get() > 0;
    }

    @Override
    protected Integer doInBackground(Void... voids) {
        running.incrementAndGet();
        Integer result;
        try {
            result = identify();
        } finally {
            running.decrementAndGet();
        }
        if (sendAuthTokens) {
            GleapAuthTokens.getInstance().sendIfNeeded();
        }
        return result;
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
            boolean forced = controller.takeForcedIdentify();

            // Verify if we need to run the identify call - check if already same data. Sent
            // anyway to get or refresh the file session of authenticated conversation files
            // (checked against the session as it is now, e.g. after the session start).
            GleapSessionProperties oldProps = controller.getGleapUserSession();
            // A project that switched to Signed: an identified session without a grant gets the
            // held hash or token once, even unchanged (marked, so it never loops).
            boolean signedGrant = controller.claimSignedGrantRequest(pendingAction);
            boolean needsFileAccess = controller.needsFileAccessIdentify(pendingAction);
            boolean unchanged = oldProps != null && oldProps.equals(pendingAction);
            if (!forced && !needsFileAccess && !signedGrant && unchanged) {
                // Old equals new, nothing to do.
                return 200;
            }
            if (signedGrant && !forced && !needsFileAccess && unchanged) {
                pendingAction.signedGrantOnly = true;
            }
            // A resend (fresh token, no network) must not be skipped as unchanged.
            forced = forced || signedGrant;

            // An identity token about to expire is renewed through the JWT provider first.
            if (!pendingAction.jwtFreshnessChecked && renewBeforeSend(controller, pendingAction, generation, forced)) {
                return 200;
            }

            // Get payload to send.
            JSONObject jsonObject = pendingAction.getJSONPayload();
            
            // Add platform and deviceType
            jsonObject.put("platform", "android");
            jsonObject.put("deviceType", GleapHelper.getDeviceType());
            // setSessionDuration: caps the grant's lifetime (ms).
            long sessionDuration = GleapConfig.getInstance().getSessionDuration();
            if (sessionDuration > 0) {
                jsonObject.put("sessionDuration", sessionDuration);
            }

            // Check if we do have an userId.
            if (!jsonObject.has( "userId")) {
                return 200;
            }

            final GleapSession session = gleapSession;
            final JSONObject payload = jsonObject;
            final String sentHash = pendingAction.getHash();
            final int[] outcome = {ACCEPTED};
            boolean answered = GleapRetry.withBackoff("Identify request", Exception.class, new GleapRetry.Attempt() {
                @Override
                public void run() throws Exception {
                    outcome[0] = performIdentifyRequest(session, payload, generation, sentHash);
                }
            });

            if (answered && outcome[0] == ACCEPTED) {
                sendAuthTokens = sentHash != null && !sentHash.isEmpty();
            } else if (answered && outcome[0] == TOKEN_EXPIRED) {
                GleapUserJwtProviderGate.getInstance().rememberRefusedToken(sentHash);
                renewExpiredToken(controller, pendingAction, generation, forced);
            } else if (answered && outcome[0] == IDENTITY_MISMATCH) {
                // Another user's session (spec §1.3): drop it and identify once more as a fresh
                // visitor.
                controller.restartForIdentify(pendingAction, generation);
            } else if (answered && outcome[0] == TOKEN_INVALID) {
                GleapUserJwtProviderGate.getInstance().rememberRefusedToken(sentHash);
                // The session and the user stay (contract §3).
                GleapLog.w("The identity token was refused (token_invalid): check the signing key and the userId claim.");
            } else if (answered && outcome[0] == REJECTED && pendingAction.signedGrantOnly) {
                // Only asked for the grant of a session the server already identified: it stays.
                GleapLog.w("The signed identify for this session was refused; the session stays unsigned. Pass a token from your backend to setUserJwt.");
            } else if (answered && outcome[0] == REJECTED) {
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
     * When the identify carries an identity token that expires within 5 minutes and a JWT
     * provider is set: asks the provider and queues the identify again with the fresh token (or
     * the old one when the provider failed).
     *
     * @return true when the identify waits for the provider
     */
    private static boolean renewBeforeSend(final GleapSessionController controller, final GleapSessionProperties identify,
                                           final int generation, final boolean forced) {
        final GleapUserJwtProviderGate gate = GleapUserJwtProviderGate.getInstance();
        final String sentHash = identify.getHash();
        if (!gate.hasProvider() || !GleapUserJwt.expiresWithin(sentHash, GleapUserJwt.REFRESH_WINDOW_MS, System.currentTimeMillis())) {
            return false;
        }
        gate.requestToken(new GleapUserJwtProviderGate.TokenCallback() {
            @Override
            public void onResult(String token) {
                GleapSessionProperties next = identify.withUserJwt(token);
                next.jwtFreshnessChecked = true;
                next.signedGrantOnly = identify.signedGrantOnly;
                controller.resendIdentify(next, sentHash, generation, forced, token != null);
            }
        });
        return true;
    }

    /**
     * token_expired: asks the JWT provider once and sends the identify again with the new
     * token. Without a provider (or after the one retry) only a warning; the session stays.
     */
    private static void renewExpiredToken(final GleapSessionController controller, final GleapSessionProperties identify,
                                          final int generation, final boolean forced) {
        final GleapUserJwtProviderGate gate = GleapUserJwtProviderGate.getInstance();
        final String sentHash = identify.getHash();
        if (!gate.hasProvider() || identify.jwtRetried) {
            GleapLog.w("The identity token has expired. Pass a fresh one to Gleap.getInstance().setUserJwt, or set setUserJwtProvider.");
            return;
        }
        gate.requestToken(new GleapUserJwtProviderGate.TokenCallback() {
            @Override
            public void onResult(String token) {
                if (token == null) {
                    return;
                }
                GleapSessionProperties next = identify.withUserJwt(token);
                next.jwtRetried = true;
                next.jwtFreshnessChecked = true;
                controller.resendIdentify(next, sentHash, generation, forced, true);
            }
        });
    }

    /**
     * @return {@link #ACCEPTED}, {@link #REJECTED} (the API rejected the identify), or
     * {@link #TOKEN_EXPIRED} / {@link #TOKEN_INVALID} (an identity token refused with 401)
     * @throws Exception when the identify did not get through; it is retried
     */
    private int performIdentifyRequest(GleapSession gleapSession, JSONObject jsonObject, int generation, String sentHash) throws Exception {
        HttpURLConnection conn = GleapHttp.openSessionPost(URL_POSTFIX, gleapSession);
        GleapHttp.writeJson(conn, jsonObject);

        int status = conn.getResponseCode();
        if (status >= 200 && status < 300) {
            JSONObject result = GleapHttp.readLastJsonLine(conn.getInputStream());
            GleapSessionController.getInstance().processSessionActionResult(result, true, false, generation, true, sentHash);
            return ACCEPTED;
        }

        String errorBody = readErrorBody(conn);
        String refusal = GleapUserJwt.refusalCode(status, errorBody);
        if ("token_expired".equals(refusal)) {
            return TOKEN_EXPIRED;
        }
        if ("token_invalid".equals(refusal)) {
            return TOKEN_INVALID;
        }
        if (GleapUserJwt.isIdentityMismatch(status, errorBody)) {
            return IDENTITY_MISMATCH;
        }
        if (isRejection(status, errorBody)) {
            if (GleapUserJwt.isSignatureRequired(status, errorBody)) {
                GleapLog.w("Gleap: this project enforces identity verification and the identify had no valid signature (signature_required). Pass a token from your backend to setUserJwt.");
            }
            return REJECTED;
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
