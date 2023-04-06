package io.gleap;

import static android.content.Context.MODE_PRIVATE;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

public class UserSessionController {
    private static UserSessionController instance;
    private GleapUser gleapUser;
    private boolean isSessionLoaded = false;
    private UserSession userSession;
    private Application application;

    private UserSessionController(Application application) {
        this.application = application;
        SharedPreferences sharedPreferences = application.getSharedPreferences("usersession", MODE_PRIVATE);
        String id = sharedPreferences.getString("id", "");
        String hash = sharedPreferences.getString("hash", "");
        if (!id.equals("") && !hash.equals("")) {
            userSession = new UserSession(id, hash);
        }

        this.gleapUser = getStoredGleapUser();
    }

    public static UserSessionController initialize(Application application) {
        if (instance == null) {
            instance = new UserSessionController(application);
        }
        return instance;
    }

    public static UserSessionController getInstance() {
        return instance;
    }

    public void clearUserSession() {
        SharedPreferences sharedPreferences = application.getSharedPreferences("usersession", MODE_PRIVATE);
        sharedPreferences.edit().clear().apply();

        ActivityUtil.getCurrentActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Handler mainHandler = new Handler(Looper.getMainLooper());
                Runnable gleapRunnable = new Runnable() {
                    @Override
                    public void run() throws RuntimeException {
                        if (GleapConfig.getInstance().getUnRegisterPushMessageGroupCallback() != null && userSession != null) {
                            String hash = userSession.getHash();
                            if (!hash.equals("")) {
                                GleapConfig.getInstance().getUnRegisterPushMessageGroupCallback().invoke("gleapuser-" + hash);
                            }
                        }
                    }
                };
                mainHandler.post(gleapRunnable);
            }
        });

        this.gleapUser = null;
        this.userSession = null;
        this.isSessionLoaded = false;

    }

    public void clearUser() {
        this.gleapUser = null;
    }


    public void mergeUserSession(String id, String hash) {
        if (userSession == null) {
            userSession = new UserSession(id, hash);
        } else {
            userSession.setHash(hash);
            userSession.setId(id);
        }
        SharedPreferences sharedPreferences = application.getSharedPreferences("usersession", MODE_PRIVATE);
        sharedPreferences.edit().putString("hash", hash).apply();
        sharedPreferences.edit().putString("id", id).apply();
    }

    public UserSession getUserSession() {
        return userSession;
    }

    public void setGleapUserSession(GleapUser gleapUser) {
        if (this.gleapUser != null && !this.gleapUser.equals(gleapUser)) {
            gleapUser.setNew(true);
        }

        if (this.gleapUser == null) {
            gleapUser.setNew(true);
        }


        this.gleapUser = gleapUser;

        SharedPreferences sharedPreferences = application.getSharedPreferences("gleap-user", MODE_PRIVATE);
        sharedPreferences.edit().putString("userId", gleapUser.getUserId()).apply();
        if (gleapUser.getGleapUserProperties() != null) {
            sharedPreferences.edit().putString("userId.name", gleapUser.getGleapUserProperties().getName()).apply();
            sharedPreferences.edit().putString("userId.email", gleapUser.getGleapUserProperties().getEmail()).apply();
            if (gleapUser.getGleapUserProperties().getPhoneNumber() != null) {
                sharedPreferences.edit().putString("userId.phonenumber", gleapUser.getGleapUserProperties().getPhoneNumber()).apply();
            }
            sharedPreferences.edit().putFloat("userId.value", (float) gleapUser.getGleapUserProperties().getValue()).apply();
            if (gleapUser.getGleapUserProperties().getHash() != null && !gleapUser.getGleapUserProperties().getHash().equals("")) {
                sharedPreferences.edit().putString("userId.hash", gleapUser.getGleapUserProperties().getHash()).apply();
            }
            if(gleapUser.getGleapUserProperties().getCustomData() != null) {
                sharedPreferences.edit().putString("customData", gleapUser.getGleapUserProperties().getCustomData().toString()).apply();
            }
        }
    }

    public GleapUser getStoredGleapUser() {
        GleapUser gleapUser = null;
        try {
            SharedPreferences sharedPreferences = application.getSharedPreferences("gleap-user", MODE_PRIVATE);
            String userId = sharedPreferences.getString("userId", "");

            String userName = sharedPreferences.getString("userId.name", "");
            String email = sharedPreferences.getString("userId.email", "");
            String phoneNumber = sharedPreferences.getString("userId.phonenumber", "");
            JSONObject customData = new JSONObject();
            try {
                String customDataString = sharedPreferences.getString("customData", "");
                customData = new JSONObject(customDataString);
            } catch (Exception ex) {
            }
            String hash = sharedPreferences.getString("userId.hash", "");

            double value = sharedPreferences.getFloat("userId.value", 0);

            GleapUserProperties gleapUserProperties = new GleapUserProperties();
            gleapUserProperties.setName(userName);
            gleapUserProperties.setEmail(email);
            gleapUserProperties.setPhoneNumber(phoneNumber);
            gleapUserProperties.setValue(value);

            gleapUserProperties.setCustomData(customData);

            if (!hash.equals("")) {
                gleapUserProperties.setHash(hash);
            }

            gleapUserProperties.setHash(hash);

            if (!userId.equals("")) {
                gleapUser = new GleapUser(userId, gleapUserProperties);
            }

        } catch (Exception | Error ignore) {
        }
        return gleapUser;
    }

    public GleapUser getGleapUserSession() {
        return gleapUser;
    }

    public boolean isSessionLoaded() {
        return isSessionLoaded;
    }

    public void setSessionLoaded(boolean sessionLoaded) {
        isSessionLoaded = sessionLoaded;
    }


}
