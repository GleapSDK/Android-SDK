package io.gleap;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.webkit.PermissionRequest;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Answers the widget's permission requests (microphone and camera, e.g. for voice messages):
 * asks the user for the matching Android permissions one after another, then grants the
 * WebView what the user allowed.
 */
final class GleapWebPermissions {
    static final int REQUEST_RECORD_AUDIO = 101;
    static final int REQUEST_RECORD_VIDEO = 102;

    private static final class Item {
        final String androidPermission;
        final String webkitPermission;
        final int requestCode;

        Item(String androidPermission, String webkitPermission, int requestCode) {
            this.androidPermission = androidPermission;
            this.webkitPermission = webkitPermission;
            this.requestCode = requestCode;
        }
    }

    private final Activity activity;
    private final Queue<Item> queue = new LinkedList<>();
    private final List<String> grantedWebkitPermissions = new ArrayList<>();
    private boolean isProcessingPermission = false;
    private PermissionRequest permissionRequest;

    GleapWebPermissions(Activity activity) {
        this.activity = activity;
    }

    void onPermissionRequest(PermissionRequest request) {
        permissionRequest = request;
        grantedWebkitPermissions.clear();

        for (String permission : request.getResources()) {
            switch (permission) {
                case "android.webkit.resource.AUDIO_CAPTURE": {
                    ask(Manifest.permission.RECORD_AUDIO, permission, REQUEST_RECORD_AUDIO);
                    break;
                }
                case "android.webkit.resource.VIDEO_CAPTURE": {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        ask(Manifest.permission.CAMERA, permission, REQUEST_RECORD_VIDEO);
                    } else {
                        grantedWebkitPermissions.add(permission);
                    }
                    break;
                }
                // Grant access to file storage permissions
                case "android.webkit.resource.PROTECTED_MEDIA_ID":
                case "android.webkit.resource.MIDIDEVICES":
                    permissionRequest.grant(new String[]{permission});
                    break;
                default:
                    // We'll allow other permissions by default to enable file access
                    permissionRequest.grant(new String[]{permission});
            }
        }
    }

    void ask(String androidPermission, String webkitPermission, int requestCode) {
        queue.offer(new Item(androidPermission, webkitPermission, requestCode));
        processNextPermission();
    }

    void onRequestPermissionsResult(String[] permissions, int[] grantResults) {
        isProcessingPermission = false;

        if (permissions.length > 0) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                String webkitPermission = webkitPermissionFor(permissions[0]);
                if (webkitPermission != null) {
                    grantedWebkitPermissions.add(webkitPermission);
                }
            }
        }

        processNextPermission();

        if (queue.isEmpty() && !isProcessingPermission) {
            grantPermissionsIfReady();
        }
    }

    private void processNextPermission() {
        if (isProcessingPermission || queue.isEmpty()) {
            return;
        }

        Item item = queue.poll();
        if (item == null) {
            return;
        }

        if (ContextCompat.checkSelfPermission(activity.getApplicationContext(), item.androidPermission)
                != PackageManager.PERMISSION_GRANTED) {
            isProcessingPermission = true;
            ActivityCompat.requestPermissions(activity, new String[]{item.androidPermission}, item.requestCode);
        } else {
            grantedWebkitPermissions.add(item.webkitPermission);
            processNextPermission();

            // If queue is now empty and we're not processing, grant immediately
            if (queue.isEmpty() && !isProcessingPermission) {
                grantPermissionsIfReady();
            }
        }
    }

    private void grantPermissionsIfReady() {
        if (permissionRequest != null) {
            if (!grantedWebkitPermissions.isEmpty()) {
                permissionRequest.grant(grantedWebkitPermissions.toArray(new String[0]));
            } else {
                permissionRequest.deny();
            }
            permissionRequest = null;
            grantedWebkitPermissions.clear();
        }
    }

    private static String webkitPermissionFor(String androidPermission) {
        switch (androidPermission) {
            case Manifest.permission.RECORD_AUDIO:
                return "android.webkit.resource.AUDIO_CAPTURE";
            case Manifest.permission.CAMERA:
                return "android.webkit.resource.VIDEO_CAPTURE";
            default:
                return null;
        }
    }
}
