package io.gleap;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;

import androidx.annotation.RequiresApi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.ParseException;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

/**
 * Sends the report to the gleap dashboard.
 */
class HttpHelper extends AsyncTask<GleapBug, Void, Integer> {
    private static final String UPLOAD_IMAGE_BACKEND_URL_POSTFIX = "/uploads/sdk";
    private static final String UPLOAD_IMAGE_MULTI_BACKEND_URL_POSTFIX = "/uploads/sdksteps";
    private static final String UPLOAD_FILES_MULTI_BACKEND_URL_POSTFIX = "/uploads/attachments";
    private static final String REPORT_BUG_URL_POSTFIX = "/bugs/v2";
    private final Context context;
    private static JSONObject sentCallbackData;

    GleapConfig bbConfig = GleapConfig.getInstance();

    private final OnHttpResponseListener listener;

    public HttpHelper(OnHttpResponseListener listener, Context context) {
        this.listener = listener;
        this.context = context;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected Integer doInBackground(GleapBug... gleapBugs) {
        if (GleapConfig.getInstance().getFeedbackWillBeSentCallback() != null) {
            GleapConfig.getInstance().getFeedbackWillBeSentCallback().invoke("");
        }
        GleapBug gleapBug = gleapBugs[0];
        int httpResult = 0;
        try {
            httpResult = postFeedback(gleapBug);
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }

        GleapConfig.getInstance().setAction(null);

        return httpResult;
    }

    @Override
    protected void onPostExecute(Integer result) {
        if (GleapConfig.getInstance().getFeedbackSentCallback() != null) {
            if (sentCallbackData != null) {
                GleapConfig.getInstance().getFeedbackSentCallback().invoke(sentCallbackData.toString());
            } else {
                GleapConfig.getInstance().getFeedbackSentCallback().invoke("");
            }
        }
        if (GleapConfig.getInstance().getCrashFeedbackSentCallback() != null) {
            GleapConfig.getInstance().getCrashFeedbackSentCallback().invoke("");
            GleapConfig.getInstance().setCrashFeedbackSentCallback(null);

        }

        sentCallbackData = null;
        GleapConfig.getInstance().setCrashStripModel(null);
        GleapBug.getInstance().setSilent(false);
        try {
            listener.onTaskComplete(result);
        } catch (GleapAlreadyInitialisedException e) {
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private JSONObject uploadImage(Bitmap image) throws IOException, JSONException {
        FormDataHttpsHelper multipart = new FormDataHttpsHelper(bbConfig.getApiUrl() + UPLOAD_IMAGE_BACKEND_URL_POSTFIX, bbConfig.getSdkKey());
        File file = bitmapToFile(image);
        if (file != null) {
            multipart.addFilePart(file);
        }
        String response = multipart.finishAndUpload();
        return new JSONObject(response);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private JSONObject uploadFiles(File[] files) throws IOException, JSONException {
        FormDataHttpsHelper multipart = new FormDataHttpsHelper(bbConfig.getApiUrl() + UPLOAD_FILES_MULTI_BACKEND_URL_POSTFIX, bbConfig.getSdkKey());
        for (File file : files) {
            if (file != null) {
                try {
                    if (file.length() > 0) {
                        multipart.addFilePart(file);
                    }
                } catch (IOException exception) {
                }
            }
        }
        String response = multipart.finishAndUpload();
        return new JSONObject(response);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private JSONObject uploadImages(Bitmap[] images) throws IOException, JSONException {
        FormDataHttpsHelper multipart = new FormDataHttpsHelper(bbConfig.getApiUrl() + UPLOAD_IMAGE_MULTI_BACKEND_URL_POSTFIX, bbConfig.getSdkKey());
        for (Bitmap bitmap : images) {
            File file = bitmapToFile(bitmap);
            if (file != null) {
                multipart.addFilePart(file);
            }
        }
        String response = multipart.finishAndUpload();
        return new JSONObject(response);
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    private Integer postFeedback(GleapBug gleapBug) throws JSONException, IOException, ParseException {
        JSONObject config = GleapConfig.getInstance().getStripModel();
        JSONObject crashStrip = GleapConfig.getInstance().getCrashStripModel();
        boolean stripImages = false;

        if (config.has("screenshot")) {
            stripImages = config.getBoolean("screenshot");
        }

        if (crashStrip != null && crashStrip.has("screenshot")) {
            stripImages = crashStrip.getBoolean("screenshot");
        }


        URL url = new URL(bbConfig.getApiUrl() + REPORT_BUG_URL_POSTFIX);
        HttpsURLConnection conn;
        if (bbConfig.getApiUrl().contains("https")) {
            conn = (HttpsURLConnection) url.openConnection();
        } else {
            conn = (HttpsURLConnection) url.openConnection();
        }
        conn.setRequestProperty("api-token", bbConfig.getSdkKey());
        conn.setDoOutput(true);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestMethod("POST");
        UserSession userSession = UserSessionController.getInstance().getUserSession();

        conn.setRequestProperty("gleap-id", userSession.getId());
        conn.setRequestProperty("gleap-hash", userSession.getHash());

        JSONObject body = new JSONObject();

        if (GleapConfig.getInstance().getAction() != null && GleapConfig.getInstance().getAction().getOutbound() != null) {
            body.put("outbound", GleapConfig.getInstance().getAction().getOutbound());
        }

        if (GleapConfig.getInstance().getAction() != null) {
            body.put("outbound", GleapConfig.getInstance().getAction().getOutbound());
        }
        body.put("spamToken", gleapBug.getSpamToken());

        if (!stripImages) {
            JSONObject responseUploadImage = uploadImage(gleapBug.getScreenshot());
            body.put("screenshotUrl", responseUploadImage.get("fileUrl"));
            body.put("replay", generateFrames());
        }

        body.put("type", gleapBug.getType());

        if (config.has("attachments") && !config.getBoolean("attachments") || !config.has("attachments")) {
            body.put("attachments", generateAttachments());
        }

        JSONObject formData = gleapBug.getData();

        //prepare data for sent
        JSONObject callBackData = new JSONObject();
        callBackData.put("type", gleapBug.getType());
        callBackData.put("formadata", formData);

        sentCallbackData = callBackData;

        body.put("formData", formData);
        body.put("networkLogs", gleapBug.getNetworklogs());
        body.put("customEventLog", gleapBug.getCustomEventLog());
        body.put("isSilent", gleapBug.isSilent() ? "true" : "false");

        try {
            PhoneMeta phoneMeta = gleapBug.getPhoneMeta();
            if (phoneMeta != null) {
                body.put("metaData", phoneMeta.getJSONObj());
            }
        } catch (Exception ex) {
        }


        body.put("customData", gleapBug.getCustomData());
        body.put("priority", gleapBug.getSeverity());

        if (GleapConfig.getInstance().isEnableConsoleLogs()) {
            body.put("consoleLog", gleapBug.getLogs());
        }

        for (Iterator<String> it = config.keys(); it.hasNext(); ) {
            String key = it.next();
            if (config.getBoolean(key)) {
                body.remove(key);
            }
        }

        if (crashStrip != null) {
            for (Iterator<String> it = crashStrip.keys(); it.hasNext(); ) {
                String key = it.next();
                if (crashStrip.getBoolean(key)) {
                    body.remove(key);
                }
            }
        }

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = body.toString().getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        return conn.getResponseCode();
    }

    /**
     * Convert the Bitmap to a File in the cache
     *
     * @param bitmap image which is uploaded
     * @return return the file
     */
    private File bitmapToFile(Bitmap bitmap) {
        try {
            File outputDir = context.getCacheDir();
            File outputFile = File.createTempFile("file", ".png", outputDir);
            OutputStream
                    os
                    = new FileOutputStream(outputFile);

            os.write(getBytes(bitmap));
            os.close();
            return outputFile;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private byte[] getBytes(Bitmap input) {
        if (input == null) {
            return new byte[0];
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        input.compress(Bitmap.CompressFormat.PNG, 90, baos);
        return baos.toByteArray();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private JSONObject generateFrames() throws IOException, JSONException {
        JSONObject replay = new JSONObject();
        replay.put("interval", GleapBug.getInstance().getReplay().getInterval());
        JSONArray frames = generateReplayImageUrls();
        replay.put("frames", frames);
        return replay;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private JSONArray generateAttachments() throws IOException, JSONException {
        JSONArray result = new JSONArray();
        JSONObject obj = uploadFiles(GleapFileHelper.getInstance().getAttachments());
        JSONArray fileUrls = (JSONArray) obj.get("fileUrls");
        for (int i = 0; i < fileUrls.length(); i++) {
            File currentFile = GleapFileHelper.getInstance().getAttachments()[i];
            JSONObject entry = new JSONObject();
            entry.put("url", fileUrls.get(i));
            entry.put("name", currentFile.getName());
            entry.put("type", Files.probeContentType(currentFile.toPath()));
            result.put(entry);
        }
        return result;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private JSONArray generateReplayImageUrls() throws IOException, JSONException {
        JSONArray result = new JSONArray();
        ScreenshotReplay[] replays = GleapBug.getInstance().getReplay().getScreenshots();
        List<Bitmap> bitmapList = new LinkedList<>();

        for (ScreenshotReplay replay : replays) {
            if (replay != null) {
                bitmapList.add(ScreenshotUtil.getResizedBitmap(replay.getScreenshot(), -0.5f));
            }
        }

        JSONObject obj = uploadImages(bitmapList.toArray(new Bitmap[bitmapList.size()]));
        JSONArray fileUrls = (JSONArray) obj.get("fileUrls");
        for (int i = 0; i < fileUrls.length(); i++) {
            JSONObject entry = new JSONObject();
            entry.put("url", fileUrls.get(i));
            entry.put("screenname", replays[i].getScreenName());
            entry.put("date", DateUtil.dateToString(replays[i].getDate()));
            entry.put("interactions", generateInteractions(replays[i]));
            result.put(entry);
        }
        GleapBug.getInstance().getReplay().reset();
        return result;
    }

    public JSONArray generateInteractions(ScreenshotReplay replay) throws JSONException {
        JSONArray result = new JSONArray();
        for (Interaction interaction : replay.getInteractions()) {
            JSONObject obj = new JSONObject();
            obj.put("x", interaction.getX());
            obj.put("y", interaction.getY());
            obj.put("date", DateUtil.dateToString(interaction.getOffset()));
            obj.put("type", interaction.getInteractiontype());
            result.put(obj);
        }
        return result;
    }

    private static JSONObject concatJSONS(JSONObject json, JSONObject obj) {
        JSONObject result = new JSONObject();

        try {
            Iterator<String> iteratorJson = json.keys();
            while (iteratorJson.hasNext()) {
                String key = iteratorJson.next();
                result.put(key, json.get(key));
            }
            Iterator<String> iteratorObj = obj.keys();
            while (iteratorObj.hasNext()) {
                String key = iteratorObj.next();
                result.put(key, obj.get(key));
            }
        } catch (Exception err) {
            err.printStackTrace();
        }

        return result;
    }
}


