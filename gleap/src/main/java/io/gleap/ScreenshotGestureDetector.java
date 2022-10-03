package io.gleap;

import android.Manifest;
import android.app.Application;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;

import java.util.Locale;

class ScreenshotGestureDetector extends GleapDetector {

    public ScreenshotGestureDetector(Application application) {
        super(application);
    }

    @Override
    public void initialize() {
        resume();
    }

    @Override
    public void resume() {
        application.getContentResolver().registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, contentObserver);
    }

    @Override
    public void pause() {
        application.getContentResolver().unregisterContentObserver(contentObserver);
    }

    @Override
    public void unregister() {
        application.getContentResolver().unregisterContentObserver(contentObserver);
    }

    private void startBugReporting() {
        this.takeScreenshot();
    }

    private final ContentObserver contentObserver = new ContentObserver(new Handler()) {
        @Override
        public boolean deliverSelfNotifications() {
            return super.deliverSelfNotifications();
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            try {
           //     startBugReporting();
            }catch (Exception ex) {
                ex.printStackTrace();
                GleapDetectorUtil.resumeAllDetectors();
            }
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if(application.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        if (uri.toString().matches(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString() + "/[0-9]+")) {

                            Cursor cursor = null;
                            try {
                                cursor = application.getContentResolver().query(uri, new String[]{
                                        MediaStore.Images.Media.DISPLAY_NAME,
                                        MediaStore.Images.Media.DATA
                                }, null, null, null);
                                if (cursor != null && cursor.moveToFirst()) {
                                     final String path = cursor.getString(Math.max(cursor.getColumnIndex(MediaStore.Images.Media.DATA), 0));
                                    if(path.toLowerCase(Locale.ROOT).contains("screenshot")) {
                                        startBugReporting();
                                    }
                                }
                            } finally {
                                if (cursor != null) {
                                    cursor.close();
                                }
                            }
                        }
                    } catch (Exception ignore) {
                        ignore.printStackTrace();
                    }
                } else {
                    startBugReporting();
                }
            }
            }
        };
}
