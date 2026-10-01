package io.gleap;

import android.content.Context;
import android.graphics.Bitmap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import java.util.LinkedList;
import java.util.List;

/**
 * Uploads the files of a ticket (multipart, see {@link FormDataHttpsHelper}): the screenshot,
 * the replay frames and the attachments.
 */
class FeedbackUploader implements FeedbackPayloadBuilder.Uploads {
    private static final String UPLOAD_IMAGE_BACKEND_URL_POSTFIX = "/uploads/sdk";
    private static final String UPLOAD_IMAGE_MULTI_BACKEND_URL_POSTFIX = "/uploads/sdksteps";
    private static final String UPLOAD_FILES_MULTI_BACKEND_URL_POSTFIX = "/uploads/attachments";

    private final Context context;

    FeedbackUploader(Context context) {
        this.context = context;
    }

    @Override
    public JSONObject uploadScreenshot(Bitmap image) throws IOException, JSONException {
        GleapConfig config = GleapConfig.getInstance();
        FormDataHttpsHelper multipart = new FormDataHttpsHelper(config.getApiUrl() + UPLOAD_IMAGE_BACKEND_URL_POSTFIX, config.getSdkKey());
        File file = bitmapToFile(image);
        String response;
        try {
            if (file != null) {
                multipart.addFilePart(file);
            }
            response = multipart.finishAndUpload();
        } finally {
            delete(file);
        }
        if (isJSONValid(response)) {
            return new JSONObject(response);
        } else {
            return new JSONObject();
        }
    }

    @Override
    public JSONObject uploadReplay() throws IOException, JSONException {
        JSONObject replay = new JSONObject();
        replay.put("interval", GleapBug.getInstance().getReplay().getInterval());
        JSONArray frames = uploadReplayFrames();
        replay.put("frames", frames);
        return replay;
    }

    @Override
    public JSONArray uploadAttachments() {
        JSONArray result = new JSONArray();
        try {
            JSONObject obj = uploadFiles(GleapFileHelper.getInstance().getAttachments());
            JSONArray fileUrls = (JSONArray) obj.get("fileUrls");
            for (int i = 0; i < fileUrls.length(); i++) {
                File currentFile = GleapFileHelper.getInstance().getAttachments()[i];
                JSONObject entry = new JSONObject();
                entry.put("url", fileUrls.get(i));
                entry.put("name", currentFile.getName());
                try (InputStream is = new BufferedInputStream(new FileInputStream(currentFile))) {
                    entry.put("type", URLConnection.guessContentTypeFromStream(is));
                }
                result.put(entry);
            }
        } catch (Exception ex) {
        }

        return result;
    }

    private JSONObject uploadFiles(File[] files) throws IOException, JSONException {
        GleapConfig config = GleapConfig.getInstance();
        FormDataHttpsHelper multipart = new FormDataHttpsHelper(config.getApiUrl() + UPLOAD_FILES_MULTI_BACKEND_URL_POSTFIX, config.getSdkKey());
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
        GleapConfig config = GleapConfig.getInstance();
        FormDataHttpsHelper multipart = new FormDataHttpsHelper(config.getApiUrl() + UPLOAD_IMAGE_MULTI_BACKEND_URL_POSTFIX, config.getSdkKey());
        List<File> files = new LinkedList<>();
        try {
            for (Bitmap bitmap : images) {
                File file = bitmapToFile(bitmap);
                if (file != null) {
                    files.add(file);
                    multipart.addFilePart(file);
                }
            }
            try {
                String response = multipart.finishAndUpload();
                return new JSONObject(response);
            } catch (Exception ex) {
            }
        } finally {
            for (File file : files) {
                delete(file);
            }
        }

        return null;
    }

    private JSONArray uploadReplayFrames() throws IOException, JSONException {
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
                // Touch interactions were never recorded on Android.
                entry.put("interactions", new JSONArray());
                result.put(entry);
            }
        }

        GleapBug.getInstance().getReplay().reset();
        return result;
    }

    /**
     * Uploads these replay frames (/uploads/sdksteps) for the logs of a capture request and
     * returns their entries, oldest first. The replay itself is not cleared. Frames that cannot
     * be written are left out, and no request is made without frames.
     */
    JSONArray uploadReplayFrames(ScreenshotReplay[] replays) throws IOException, JSONException {
        JSONArray result = new JSONArray();
        List<ScreenshotReplay> included = new LinkedList<>();
        List<File> files = new LinkedList<>();
        try {
            for (ScreenshotReplay replay : replays) {
                if (replay == null || replay.getScreenshot() == null) {
                    continue;
                }
                File file = bitmapToFile(replay.getScreenshot());
                if (file != null) {
                    files.add(file);
                    included.add(replay);
                }
            }
            if (files.isEmpty()) {
                return result;
            }

            GleapConfig config = GleapConfig.getInstance();
            FormDataHttpsHelper multipart = new FormDataHttpsHelper(config.getApiUrl() + UPLOAD_IMAGE_MULTI_BACKEND_URL_POSTFIX, config.getSdkKey());
            for (File file : files) {
                multipart.addFilePart(file);
            }
            JSONArray fileUrls = new JSONObject(multipart.finishAndUpload()).getJSONArray("fileUrls");
            for (int i = 0; i < fileUrls.length() && i < included.size(); i++) {
                ScreenshotReplay replay = included.get(i);
                JSONObject entry = new JSONObject();
                entry.put("url", fileUrls.get(i));
                entry.put("screenname", replay.getScreenName());
                entry.put("date", DateUtil.dateToString(replay.getDate()));
                entry.put("interactions", new JSONArray());
                result.put(entry);
            }
        } finally {
            for (File file : files) {
                delete(file);
            }
        }
        return result;
    }

    // The PNGs are only needed for the upload: screenshots of the app must not stay on disk.
    private static void delete(File file) {
        if (file != null && file.exists() && !file.delete()) {
            GleapLog.w("Could not delete " + file.getName());
        }
    }

    /**
     * Writes the bitmap to a PNG file in the app's cache directory; delete it after the upload.
     */
    private File bitmapToFile(Bitmap bitmap) {
        if (bitmap != null) {
            File outputFile = null;
            try {
                File outputDir = context.getCacheDir();
                outputFile = File.createTempFile("file", ".png", outputDir);
                try (OutputStream os = new FileOutputStream(outputFile)) {
                    os.write(getBytes(bitmap));
                }
                return outputFile;
            } catch (Exception e) {
                delete(outputFile);
            }
        }
        return null;
    }

    private byte[] getBytes(Bitmap input) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        input.compress(Bitmap.CompressFormat.PNG, 90, baos);
        return baos.toByteArray();
    }

    private boolean isJSONValid(String test) {
        try {
            new JSONObject(test);
        } catch (Exception ex) {
            try {
                new JSONArray(test);
            } catch (Exception ex1) {
                return false;
            }
        }
        return true;
    }
}
