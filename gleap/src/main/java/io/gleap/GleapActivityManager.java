package io.gleap;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

class GleapActivityManager {
    private static GleapActivityManager gleapActivityManager;
    private Application application;
    private String currentPage = "";

    private GleapActivityManager(){}

    public static GleapActivityManager getInstance() {
        if(gleapActivityManager == null) {
            gleapActivityManager = new GleapActivityManager();
        }
        return gleapActivityManager;
    }

    public void start(Application application) {
        this.application = application;
        if (this.application != null) {
            this.application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {
                }

                @Override
                public void onActivityStarted(@NonNull Activity activity) {
                    checkPage(activity);
                    generateFab(activity);
                  }

                @Override
                public void onActivityResumed(@NonNull Activity activity) {

                }

                @Override
                public void onActivityPaused(@NonNull Activity activity) {

                }

                @Override
                public void onActivityStopped(@NonNull Activity activity) {
                    if(GleapBug.getInstance().getApplicationtype() == APPLICATIONTYPE.NATIVE) {
                        GleapInvisibleActivityManger.getInstance().setVisible();
                    }
                }

                @Override
                public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {

                }

                @Override
                public void onActivityDestroyed(@NonNull Activity activity) {
                }
            });

        }
    }

    public void stop(){
        if(this.application != null) {
            this.application.registerActivityLifecycleCallbacks(null);
        }
    }

    private void checkPage(Activity activity) {
        try {
            if (!currentPage.equals(activity.getClass().getSimpleName()) && !activity.getClass().getSimpleName().contains("Gleap")) {
                GleapInvisibleActivityManger.getInstance().setVisible();
                currentPage = activity.getClass().getSimpleName();
                JSONObject object = new JSONObject();
                try {
                    object.put("page", activity.getClass().getSimpleName());
                    Gleap.getInstance().trackEvent("pageView", object);
                    currentPage = activity.getClass().getSimpleName();
                } catch (JSONException e) {
                }
            } else if(!activity.getClass().getSimpleName().contains("Gleap")){
                GleapInvisibleActivityManger.getInstance().setVisible();
            } else {
                GleapInvisibleActivityManger.getInstance().setInvisible();
            }
        }catch (Exception ex){}
    }


    private void generateFab(Activity activity) {
        GleapDetector detector =  GleapDetectorUtil.getDetectorByClassName("FABGesture");

        if(detector != null) {
            FABGesture fabGesture = (FABGesture) detector;
            fabGesture.attachFAB(activity);
        }
    }
}
