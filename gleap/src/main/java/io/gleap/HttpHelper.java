package io.gleap;

import android.content.Context;
import android.os.AsyncTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

import io.gleap.callbacks.FeedbackSendingFailedCallback;
import io.gleap.callbacks.FeedbackWillBeSentCallback;

/**
 * Sends a ticket to Gleap (POST /bugs/v2): the report data is copied when the task is created
 * (see {@link FeedbackSubmission}), the files are uploaded and the ticket is posted in the
 * background, the app's callbacks and the listener run on the main thread.
 */
class HttpHelper extends AsyncTask<GleapBug, Void, JSONObject> {
    private static final String REPORT_BUG_URL_POSTFIX = "/bugs/v2";

    interface Sender {
        void send(OnHttpResponseListener listener, Context context);
    }

    private static final Sender ASYNC = new Sender() {
        @Override
        public void send(OnHttpResponseListener listener, Context context) {
            new HttpHelper(listener, context).executeOnExecutor(GleapExecutor.SERIAL, GleapBug.getInstance());
        }
    };

    private static volatile Sender sender = ASYNC;

    private static final FeedbackPayloadBuilder.ReportData DEVICE_DATA = new FeedbackPayloadBuilder.ReportData() {
        @Override
        public JSONArray networkLogs() {
            return GleapBug.getInstance().getNetworklogs();
        }

        @Override
        public JSONObject metaData() throws JSONException {
            PhoneMeta phoneMeta = GleapBug.getInstance().getPhoneMeta();
            return phoneMeta != null ? phoneMeta.getJSONObj() : null;
        }

        @Override
        public JSONArray consoleLogs() {
            return GleapBug.getInstance().getLogs();
        }
    };

    private final Context context;
    private final OnHttpResponseListener listener;
    private final FeedbackSubmission submission;
    // What the sent callbacks receive: the outbound id and the form data.
    private final JSONObject sentData;

    public HttpHelper(OnHttpResponseListener listener, Context context) {
        this.listener = listener;
        this.context = context;
        this.submission = FeedbackSubmission.capture();

        JSONObject data = new JSONObject();
        try {
            data.put("outboundId", submission.outboundId);
            data.put("formData", submission.formData);
        } catch (JSONException ignore) {
        }
        this.sentData = data;
    }

    /**
     * Sends the current report data as a ticket; the listener gets the result on the main thread.
     */
    static void send(OnHttpResponseListener listener, Context context) {
        sender.send(listener, context);
    }

    // Tests only; null restores the background task.
    static void setSenderForTesting(Sender testSender) {
        sender = testSender != null ? testSender : ASYNC;
    }

    /**
     * The report was created: the API answers POST /bugs/v2 with 201, the same check the widget
     * uses to show the confirmation.
     */
    static boolean isSent(JSONObject result) {
        return result != null && result.optInt("status", 0) == 201;
    }

    @Override
    protected void onPreExecute() {
        try {
            FeedbackWillBeSentCallback willBeSent = GleapCallbacks.getInstance().getFeedbackWillBeSentCallback();
            if (willBeSent != null) {
                willBeSent.invoke(submission.formData != null ? submission.formData.toString() : "");
            }
        } catch (Exception ignore) {
        }
    }

    @Override
    protected JSONObject doInBackground(GleapBug... gleapBugs) {
        JSONObject result = new JSONObject();
        try {
            result = postFeedback();
        } catch (Exception e) {
        }

        return result;
    }

    @Override
    protected void onPostExecute(JSONObject result) {
        if (isSent(result)) {
            notifySent();
        } else {
            notifySendingFailed(result);
        }

        GleapBug.getInstance().setSilent(false);
        GleapConfig.getInstance().setCrashStripModel(new JSONObject());
        try {
            listener.onTaskComplete(result);
        } catch (GleapAlreadyInitialisedException e) {
        }
    }

    private static void notifySendingFailed(JSONObject result) {
        try {
            FeedbackSendingFailedCallback failed = GleapCallbacks.getInstance().getFeedbackSendingFailedCallback();
            if (failed != null) {
                int status = result != null ? result.optInt("status", 0) : 0;
                failed.invoke(status > 0
                        ? "The feedback could not be sent (HTTP " + status + ")."
                        : "The feedback could not be sent.");
            }
        } catch (Exception ignore) {
        }
    }

    private void notifySent() {
        // Default form submission callback.
        if (GleapCallbacks.getInstance().getFeedbackSentCallback() != null) {
            if (sentData.has("formData")) {
                try {
                    GleapCallbacks.getInstance().getFeedbackSentCallback().invoke(sentData.getJSONObject("formData"));
                } catch (JSONException e) {
                    GleapCallbacks.getInstance().getFeedbackSentCallback().invoke(null);
                }
            } else {
                GleapCallbacks.getInstance().getFeedbackSentCallback().invoke(null);
            }
        }

        // Send outbound sent.
        try {
            if (GleapCallbacks.getInstance().getOutboundSentCallback() != null) {
                GleapCallbacks.getInstance().getOutboundSentCallback().invoke(sentData);
            }
        } catch (Exception exp) {
        }

        // Track outbound submission.
        if (sentData.has("outboundId")) {
            try {
                String outboundId = sentData.getString("outboundId");

                Gleap.getInstance().trackEvent("outbound-" + outboundId + "-submitted", sentData.getJSONObject("formData"));
            } catch (JSONException e) {
            }
        }
    }

    private JSONObject postFeedback() throws JSONException, IOException {
        HttpURLConnection conn = GleapHttp.openReportPost(REPORT_BUG_URL_POSTFIX,
                GleapSessionController.getInstance().getUserSession());

        JSONObject body = FeedbackPayloadBuilder.build(submission, GleapConfig.getInstance().isEnableConsoleLogs(),
                new FeedbackUploader(context), DEVICE_DATA);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = body.toString().getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        } catch (Exception ex) {
            JSONObject response = new JSONObject();
            try {
                response.put("status", 403);
            } catch (Exception ignore) {
            }

            return response;
        }

        JSONObject response = new JSONObject();

        try {
            response.put("status", conn.getResponseCode());
            JSONObject result = new JSONObject(readInputStreamToString(conn));

            response.put("response", result);
        } catch (Exception ex) {
        }

        return response;
    }

    private String readInputStreamToString(HttpURLConnection connection) {
        String result = null;
        StringBuffer sb = new StringBuffer();
        InputStream is = null;

        try {
            is = new BufferedInputStream(connection.getInputStream());
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String inputLine = "";
            while ((inputLine = br.readLine()) != null) {
                sb.append(inputLine);
            }
            result = sb.toString();
        } catch (Exception e) {
            result = null;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }
        }

        return result;
    }
}
