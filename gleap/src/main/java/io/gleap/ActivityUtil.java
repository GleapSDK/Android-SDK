package io.gleap;

import android.app.Activity;

import java.lang.reflect.Field;
import java.util.Map;

import io.gleap.GetActivityCallback;

class ActivityUtil {
    public static Activity getCurrentActivity() {
        GetActivityCallback activityCallback =  GleapConfig.getInstance().getGetActivityCallback();
        if(activityCallback != null) {
            return activityCallback.getActivity();
        }
        try {
            Class activityThreadClass = Class.forName("android.app.ActivityThread");
            Object activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null);
            Field activitiesField = activityThreadClass.getDeclaredField("mActivities");
            activitiesField.setAccessible(true);
            Map<Object, Object> activities = (Map<Object, Object>) activitiesField.get(activityThread);
            if (activities == null)
                return null;
            for (Object activityRecord : activities.values()) {
                Class activityRecordClass = activityRecord.getClass();
                Field pausedField = activityRecordClass.getDeclaredField("paused");
                pausedField.setAccessible(true);
                if (!pausedField.getBoolean(activityRecord)) {
                    Field activityField = activityRecordClass.getDeclaredField("activity");
                    activityField.setAccessible(true);
                    Activity activity = (Activity) activityField.get(activityRecord);
                    return activity;
                }
            }
        } catch (Exception e) {
        }

        return null;
    }
}
