package io.gleap;

import static android.content.Context.MODE_PRIVATE;

import android.app.Application;
import android.content.SharedPreferences;

public class UserSessionController {
    private static UserSessionController instance;
    private GleapUserSession gleapUserSession;
    private UserSession userSession;
    private Application application;

    private  UserSessionController(Application application){
        this.application = application;
        SharedPreferences sharedPreferences = application.getSharedPreferences("usersession", MODE_PRIVATE);
        String id = sharedPreferences.getString("id", "");
        String type = sharedPreferences.getString("type", "");
        String hash = sharedPreferences.getString("hash", "");
        userSession = new UserSession(id, type, hash);
    }

    public static UserSessionController initialize(Application application) {
        if(instance == null) {
            instance = new UserSessionController(application);
        }
        return instance;
    }

    public static UserSessionController getInstance(){
        return instance;
    }

    public UserSession getUserSession() {
        return userSession;
    }

    public void clearUserSession() {
        SharedPreferences sharedPreferences = application.getSharedPreferences("usersession", MODE_PRIVATE);
        sharedPreferences.edit().clear().apply();
    }

    public void setUserSession(GleapUserSession  gleapUserSession) {
        this.gleapUserSession = gleapUserSession;
    }

    public void mergeUserSession(String id, String hash, String type) {
        userSession.setHash(hash);
        userSession.setId(id);
        userSession.setType(type);
        SharedPreferences sharedPreferences = application.getSharedPreferences("usersession", MODE_PRIVATE);
        sharedPreferences.edit().putString("hash", hash).apply();
        sharedPreferences.edit().putString("id", id).apply();
        sharedPreferences.edit().putString("type", type).apply();
    }

    public void setGleapUserSession(GleapUserSession gleapUserSession){
        this.gleapUserSession = gleapUserSession;
    }

    public GleapUserSession getGleapUserSession() {
        SharedPreferences sharedPreferences = application.getSharedPreferences("usersession", MODE_PRIVATE);

        String id = sharedPreferences.getString("id", "");
        String type = sharedPreferences.getString("type", "");
        String hash = sharedPreferences.getString("hash", "");
        userSession = new UserSession(id, type, hash);
        return gleapUserSession;
    }
}
