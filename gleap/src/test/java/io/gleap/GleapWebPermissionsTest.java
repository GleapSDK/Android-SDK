package io.gleap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.webkit.PermissionRequest;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * What the widget's WebView may use: the microphone (voice messages) and the camera, once the
 * app has the Android permission; nothing else, whichever frame asks.
 */
public class GleapWebPermissionsTest {
    private final List<String> answers = new ArrayList<>();

    private PermissionRequest request(final String... resources) {
        return new PermissionRequest() {
            @Override
            public Uri getOrigin() {
                return null;
            }

            @Override
            public String[] getResources() {
                return resources;
            }

            @Override
            public void grant(String[] granted) {
                answers.add("grant " + Arrays.toString(granted));
            }

            @Override
            public void deny() {
                answers.add("deny");
            }
        };
    }

    // An activity whose app holds every Android permission already.
    private static Activity activityWithAllPermissions() {
        final Context context = new ContextWrapper(null) {
            @Override
            public int checkPermission(String permission, int pid, int uid) {
                return PackageManager.PERMISSION_GRANTED;
            }
        };
        return new Activity() {
            @Override
            public Context getApplicationContext() {
                return context;
            }
        };
    }

    @Test
    public void onlyTheMicrophoneIsGrantedFromAMixedRequest() {
        new GleapWebPermissions(activityWithAllPermissions()).onPermissionRequest(request(
                PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID,
                PermissionRequest.RESOURCE_AUDIO_CAPTURE,
                PermissionRequest.RESOURCE_MIDI_SYSEX,
                "android.webkit.resource.SOMETHING_NEW"));

        assertEquals(Arrays.asList("grant [" + PermissionRequest.RESOURCE_AUDIO_CAPTURE + "]"), answers);
    }

    @Test
    public void aRequestForOtherResourcesIsDenied() {
        new GleapWebPermissions(activityWithAllPermissions()).onPermissionRequest(request(
                PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID,
                PermissionRequest.RESOURCE_MIDI_SYSEX));

        assertEquals(Arrays.asList("deny"), answers);
    }

    @Test
    public void theRequestIsAnsweredOnce() {
        new GleapWebPermissions(activityWithAllPermissions()).onPermissionRequest(request(
                PermissionRequest.RESOURCE_AUDIO_CAPTURE,
                PermissionRequest.RESOURCE_VIDEO_CAPTURE));

        assertEquals(1, answers.size());
        assertTrue(answers.get(0), answers.get(0).contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE));
        assertTrue(answers.get(0), answers.get(0).contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE));
    }
}
