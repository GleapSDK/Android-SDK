package io.gleap;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.webkit.ValueCallback;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;

/**
 * Picks a file for an upload field of the widget with the system picker. The activity
 * registers the two result launchers and forwards their results here.
 */
final class GleapFileChooser {
    // Android 13+: the WebView's callback for the running pick.
    private ValueCallback<Uri[]> fileChooserCallback;

    /**
     * @return whether the picker was opened
     */
    boolean show(ValueCallback<Uri[]> filePathCallback, ActivityResultLauncher<Intent> imagePicker,
                 ActivityResultLauncher<Intent> filePicker) {
        // Save the callback for use after file selection
        fileChooserCallback = filePathCallback;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false); // Single selection
            try {
                imagePicker.launch(intent);
                return true;
            } catch (ActivityNotFoundException e) {
                fileChooserCallback = null; // Reset callback on failure
                return false;
            }
        }

        // Before Android 13 the pending callback is kept in GleapConfig (see Gleap.finishImageUpload).
        try {
            ValueCallback<Uri[]> pendingUpload = GleapConfig.getInstance().getFileUploadCallback();
            if (pendingUpload != null) {
                pendingUpload.onReceiveValue(null);
            }

            GleapConfig.getInstance().setFileUploadCallback(filePathCallback);
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*"); // set MIME type to allow all files
            filePicker.launch(i);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Result of the Android 13+ picker.
     */
    void onImagePicked(ActivityResult result) {
        if (fileChooserCallback == null) {
            return;
        }

        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            Uri selectedImage = result.getData().getData();
            if (selectedImage != null) {
                fileChooserCallback.onReceiveValue(new Uri[]{selectedImage});
            } else {
                fileChooserCallback.onReceiveValue(null); // No file selected
            }
        } else {
            fileChooserCallback.onReceiveValue(null); // Handle cancellation or errors
        }
        fileChooserCallback = null; // Reset callback after use
    }

    /**
     * Result of the picker before Android 13.
     */
    void onFilePicked(ActivityResult activityResult) {
        if (activityResult.getResultCode() == Activity.RESULT_OK) {
            Intent intent = activityResult.getData();
            ValueCallback<Uri[]> pendingUpload = GleapConfig.getInstance().getFileUploadCallback();
            if (pendingUpload == null || intent == null) {
                return;
            }

            Uri[] result = null;
            String dataString = intent.getDataString();

            if (dataString != null) {
                result = new Uri[]{Uri.parse(dataString)};
            }

            pendingUpload.onReceiveValue(result);
            GleapConfig.getInstance().setFileUploadCallback(null);
        }
    }
}
