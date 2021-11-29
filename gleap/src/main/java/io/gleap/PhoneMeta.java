package io.gleap;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Date;
import java.util.Locale;

import static io.gleap.ActivityUtil.getCurrentActivity;

import gleap.io.gleap.BuildConfig;


/**
 * Collected information, gathered from the phone
 */
class PhoneMeta {
    private final Context context;
    private static double startTime;
    private static String deviceModel;
    private static String deviceName;
    private static String lastScreenName;
    private static String bundleID;
    private static String systemName;
    private static String systemVersion;
    private static String buildVersionNumber;
    private static String releaseVersionNumber;
    private static final String sdkVersion = BuildConfig.VERSION_NAME;

    public PhoneMeta(@NonNull Context context) {
        startTime = new Date().getTime();
        this.context = context;
        getPhoneMeta();
    }

    /**
     * get the meta information for the phone
     *
     * @return the metainformation gathered from the phone
     * @throws JSONException cant create JSON Object
     */
    public JSONObject getJSONObj() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("sessionDuration", calculateDuration());
        obj.put("releaseVersionNumber", releaseVersionNumber);
        obj.put("deviceModel", deviceModel);
        obj.put("deviceName", deviceName);
        obj.put("deviceIdentifier", deviceModel);
        obj.put("bundleID", bundleID);
        obj.put("systemName", systemName);
        obj.put("systemVersion", systemVersion);
        obj.put("buildVersionNumber", buildVersionNumber);
        obj.put("lastScreenName", lastScreenName);
        obj.put("networkStatus", getNetworkStatus());
        obj.put("preferredUserLocale", getLocale());
        obj.put("sdkVersion", sdkVersion);

        obj.put("batterySaveMode", getBatterySaveMode());
        obj.put("batteryLevel", getBatteryLevel());
        obj.put("phoneChargingStatus", getBatteryState());

        obj.put("appRAMUsage", getRamUsage());
        obj.put("totalRAM", getRamTotal());

        obj.put("totalDiskSpace", getTotalInternalMemorySize());
        obj.put("totalFreeDiskSpace", getFreeStorage());

        if (BuildConfig.BUILD_TYPE.equals("debug")) {
            obj.put("buildMode", "DEBUG");
        } else {
            obj.put("buildMode", "RELEASE");
        }

        String applicationType = "Android";
        if (GleapBug.getInstance().getApplicationtype() == APPLICATIONTYPE.FLUTTER) {
            applicationType = "Flutter/Android";
        }
        if (GleapBug.getInstance().getApplicationtype() == APPLICATIONTYPE.REACTNATIVE) {
            applicationType = "ReactNative/Android";
        }
        obj.put("sdkType", applicationType);
        return obj;
    }

    private void getPhoneMeta() {
        PackageManager packageManager = context.getPackageManager();
        if (packageManager != null) {
            try {
                PackageInfo packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
                buildVersionNumber = Integer.toString(packageInfo.versionCode);
                releaseVersionNumber = packageInfo.versionName;
                bundleID = packageInfo.packageName;
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
                bundleID = context.getPackageName();

            }
        }
        if (getCurrentActivity() != null) {
            lastScreenName = getCurrentActivity().getClass().getSimpleName();
        }
        deviceModel = Build.MODEL;
        deviceName = Build.DEVICE;
        systemName = "Android";
        systemVersion = Build.VERSION.RELEASE;
    }

    private static String calculateDuration() {
        double timeDif = (new Date().getTime() - startTime) / 1000;
        return Double.toString(timeDif);
    }

    /**
     * The phone network state is only gathered, if the ACCESS_NETWORK_STATE is requested in the AndroidManifest.xml
     *
     * @return status of the network
     */
    private String getNetworkStatus() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_NETWORK_STATE)
                == PackageManager.PERMISSION_GRANTED) {
            ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

            //Only called when the permission is granted
            @SuppressLint("MissingPermission") NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork.getTypeName();

        } else {
            return "";
        }
    }

    private float getBatteryLevel() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        return level * 100 / (float) scale;
    }

    private boolean getBatterySaveMode() {
        PowerManager powerManager = (PowerManager) context.getSystemService(context.POWER_SERVICE);
        return powerManager.isPowerSaveMode();
    }

    private double getRamUsage() {
        Runtime runtime = Runtime.getRuntime();
        long runtimeTotal = runtime.totalMemory();
        long runtimeFree = runtime.freeMemory();
        long runtimeUsed = runtimeTotal - runtimeFree;
        double result = Double.parseDouble(String.format("%02d", runtimeUsed  / (1024 * 1024)));
        return result;

    }

    private double getRamTotal() {
        ActivityManager actManager = (ActivityManager) context.getSystemService(context.ACTIVITY_SERVICE);

        // Declaring MemoryInfo object
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();

        // Fetching the data from the ActivityManager
        actManager.getMemoryInfo(memInfo);
        double result = Double.parseDouble(String.format("%02d", memInfo.totalMem / (1024 * 1024)));
        return result;

    }

    private String getBatteryState() {
        String result = "Unplugged";
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

        if(status == BatteryManager.BATTERY_STATUS_CHARGING){
            result = "Charging";
            int chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            boolean usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB;
            boolean acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC;

            if(usbCharge) {
                result += " (USB)";
            }

            if(acCharge) {
                result += " (AC)";
            }
        } else if(status == BatteryManager.BATTERY_STATUS_FULL){
            result = "Full";
        }
        return result;
    }

    public double getFreeStorage() {
        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
        long bytesAvailable;
        bytesAvailable = stat.getBlockSizeLong() * stat.getAvailableBlocksLong();
        long megAvailable = bytesAvailable / (1024 * 1024 * 1024);
        return megAvailable;
    }

    public double getTotalInternalMemorySize() {
        double totalSize = new File(context.getFilesDir().getAbsoluteFile().toString()).getTotalSpace() / 1024.0 / 1024.0 / 1024.0;
       return totalSize;
    }

    public String getLocale() {
        return Locale.getDefault().getLanguage();
    }

    public void setLastScreen(String lastScreenName) {
        PhoneMeta.lastScreenName = lastScreenName;
    }
}
