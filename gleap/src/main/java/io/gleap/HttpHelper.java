package io.gleap;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;
import android.util.JsonWriter;

import androidx.annotation.RequiresApi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
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
class HttpHelper extends AsyncTask<GleapBug, Void, JSONObject> {
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

    @Override
    protected JSONObject doInBackground(GleapBug... gleapBugs) {
        GleapBug gleapBug = gleapBugs[0];
        JSONObject result = new JSONObject();
        try {
            result = postFeedback(gleapBug);
        } catch (Exception e) {
        }

        GleapConfig.getInstance().setAction(null);

        return result;
    }

    @Override
    protected void onPostExecute(JSONObject result) {
        if (GleapConfig.getInstance().getFeedbackSentCallback() != null) {
            if (sentCallbackData != null) {
                GleapConfig.getInstance().getFeedbackSentCallback().invoke(sentCallbackData.toString());
            } else {
                GleapConfig.getInstance().getFeedbackSentCallback().invoke("");
            }
        }

        if (GleapConfig.getInstance().getCrashFeedbackSentCallback() != null) {
            if (sentCallbackData != null) {
                GleapConfig.getInstance().getCrashFeedbackSentCallback().invoke(sentCallbackData.toString());
            } else {
                GleapConfig.getInstance().getCrashFeedbackSentCallback().invoke("");
            }
        }

        sentCallbackData = null;

        GleapBug.getInstance().setSilent(false);
        GleapConfig.getInstance().setCrashStripModel(new JSONObject());
        try {
            listener.onTaskComplete(result);
        } catch (GleapAlreadyInitialisedException e) {
        }
    }


    private JSONObject uploadImage(Bitmap image) throws IOException, JSONException {
        FormDataHttpsHelper multipart = new FormDataHttpsHelper(bbConfig.getApiUrl() + UPLOAD_IMAGE_BACKEND_URL_POSTFIX, bbConfig.getSdkKey());
        File file = bitmapToFile(image);
        if (file != null) {
            multipart.addFilePart(file);
        }
        String response = multipart.finishAndUpload();
        if (isJSONValid(response)) {
            return new JSONObject(response);
        } else {
            return new JSONObject();
        }
    }


    private JSONObject uploadFiles(File[] files) throws IOException, JSONException {
        FormDataHttpsHelper multipart = new FormDataHttpsHelper(bbConfig.getApiUrl() + UPLOAD_FILES_MULTI_BACKEND_URL_POSTFIX, bbConfig.getSdkKey());
        for (File file : files) {
            try {
                if (file != null && file.length() > 0) {
                    multipart.addFilePart(file);
                }
            } catch (Exception exception) {
            }
        }
        String response = multipart.finishAndUpload();

        return new JSONObject(response);
    }

    private JSONObject uploadImages(Bitmap[] images) throws IOException, JSONException {
        FormDataHttpsHelper multipart = new FormDataHttpsHelper(bbConfig.getApiUrl() + UPLOAD_IMAGE_MULTI_BACKEND_URL_POSTFIX, bbConfig.getSdkKey());
        for (Bitmap bitmap : images) {
            File file = bitmapToFile(bitmap);
            if (file != null) {
                multipart.addFilePart(file);
            }
        }
        try {
            String response = multipart.finishAndUpload();
            return new JSONObject(response);
        } catch (Exception ex) {
        }

        return null;
    }


    private JSONObject postFeedback(GleapBug gleapBug) throws JSONException, IOException {
        JSONObject config = GleapConfig.getInstance().getStripModel();
        JSONObject stripConfig = GleapConfig.getInstance().getCrashStripModel();
        boolean stripImages = false;

        if (config.has("screenshot")) {
            stripImages = config.getBoolean("screenshot");
        }
        if (stripConfig.has("screenshot")) {
            stripImages = stripConfig.getBoolean("screenshot");
        }

        URL url = new URL(bbConfig.getApiUrl() + REPORT_BUG_URL_POSTFIX);
        HttpURLConnection conn;
        if (bbConfig.getApiUrl().contains("https")) {
            conn = (HttpsURLConnection) url.openConnection();
        } else {
            conn = (HttpURLConnection) url.openConnection();
        }

        conn.setRequestProperty("api-token", bbConfig.getSdkKey());
        conn.setDoOutput(true);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestMethod("POST");
        UserSession userSession = UserSessionController.getInstance().getUserSession();

        if (userSession != null) {
            conn.setRequestProperty("gleap-id", userSession.getId());
            conn.setRequestProperty("gleap-hash", userSession.getHash());
        }
        JSONObject body = new JSONObject();
/*
        if (GleapConfig.getInstance().getAction() != null && GleapConfig.getInstance().getAction().getOutbound() != null) {
            body.put("outbound", GleapConfig.getInstance().getAction().getOutbound());
        }
*/
        body.put("outbound", gleapBug.getOutboubdId());
        if(gleapBug.getOutboubdId() != null && !gleapBug.getOutboubdId().equals("")) {
            gleapBug.setOutboubdId("bugreporting");
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
        callBackData.put("formdata", formData);

        sentCallbackData = callBackData;

        body.put("formData", formData);
        body.put("networkLogs", gleapBug.getNetworklogs());
        body.put("customEventLog", gleapBug.getCustomEventLog());
        body.put("isSilent", gleapBug.isSilent() ? "true" : "false");

        PhoneMeta phoneMeta = gleapBug.getPhoneMeta();

        if (phoneMeta != null) {
            body.put("metaData", phoneMeta.getJSONObj());
        }

        body.put("customData", gleapBug.getCustomData());
        body.put("priority", gleapBug.getSeverity());

        try {
            body.put("tags", new JSONArray(gleapBug.getTags()));
        } catch (Exception ex) {
        }

        if (GleapConfig.getInstance().isEnableConsoleLogs()) {
            body.put("consoleLog", gleapBug.getLogs());
        }

        for (Iterator<String> it = config.keys(); it.hasNext(); ) {
            String key = it.next();
            if (config.getBoolean(key)) {
                body.remove(key);
            }
        }

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

    /**
     * Convert the Bitmap to a File in the cache
     *
     * @param bitmap image which is uploaded
     * @return return the file
     */
    private File bitmapToFile(Bitmap bitmap) {
        if (bitmap != null) {
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
            }
        }
        return null;
    }

    private byte[] getBytes(Bitmap input) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        input.compress(Bitmap.CompressFormat.PNG, 90, baos);
        return baos.toByteArray();
    }

    private JSONObject generateFrames() throws IOException, JSONException {
        JSONObject replay = new JSONObject();
        replay.put("interval", GleapBug.getInstance().getReplay().getInterval());
        JSONArray frames = generateReplayImageUrls();
        replay.put("frames", frames);
        return replay;
    }

    private JSONArray generateAttachments() {
        JSONArray result = new JSONArray();
        try {
            JSONObject obj = uploadFiles(GleapFileHelper.getInstance().getAttachments());
            JSONArray fileUrls = (JSONArray) obj.get("fileUrls");
            for (int i = 0; i < fileUrls.length(); i++) {
                File currentFile = GleapFileHelper.getInstance().getAttachments()[i];
                JSONObject entry = new JSONObject();
                entry.put("url", fileUrls.get(i));
                entry.put("name", currentFile.getName());
                InputStream is = new BufferedInputStream(new FileInputStream(currentFile));
                entry.put("type", URLConnection.guessContentTypeFromStream(is));
                result.put(entry);
            }
        } catch (Exception ex) {
        }

        return result;
    }

    private JSONArray generateReplayImageUrls() throws IOException, JSONException {
        JSONArray result = new JSONArray();
        ScreenshotReplay[] replays = GleapBug.getInstance().getReplay().getScreenshots();
        List<Bitmap> bitmapList = new LinkedList<>();

        for (ScreenshotReplay replay : replays) {
            if (replay != null) {
                bitmapList.add(replay.getScreenshot());
            }
        }

        JSONObject obj = uploadImages(bitmapList.toArray(new Bitmap[bitmapList.size()]));
        if (obj != null) {
            JSONArray fileUrls = (JSONArray) obj.get("fileUrls");
            for (int i = 0; i < fileUrls.length(); i++) {
                JSONObject entry = new JSONObject();
                entry.put("url", fileUrls.get(i));
                entry.put("screenname", replays[i].getScreenName());
                entry.put("date", DateUtil.dateToString(replays[i].getDate()));
                entry.put("interactions", generateInteractions(replays[i]));
                result.put(entry);
            }
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

    private boolean isJSONValid(String test) {
        try {
            new JSONObject(test);
        } catch (Exception ex) {
            // edited, to include @Arthur's comment
            // e.g. in case JSONArray is valid as well...
            try {
                new JSONArray(test);
            } catch (Exception ex1) {
                return false;
            }
        }
        return true;
    }
}