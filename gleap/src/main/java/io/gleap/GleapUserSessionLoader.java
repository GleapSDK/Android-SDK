package io.gleap;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import javax.net.ssl.HttpsURLConnection;

class GleapUserSessionLoader extends AsyncTask<Void, Void, Integer> {
    private static final String httpsUrl = GleapConfig.getInstance().getApiUrl() + "/sessions";
    private UserSessionLoadedCallback callback;

    @SuppressLint("WrongThread")
    @Override
    protected Integer doInBackground(Void... voids) {
        try {
            URL url = new URL(httpsUrl);
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setRequestProperty("Api-Token", GleapConfig.getInstance().getSdkKey());
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestMethod("POST");
            UserSession userSession = UserSessionController.getInstance().getUserSession();

            if (userSession != null && userSession.getId() != null && !userSession.getId().equals("")) {
                conn.setRequestProperty("Gleap-Id", userSession.getId());
            }
            if(userSession != null && userSession.getHash() != null && !userSession.getHash().equals("")) {
                conn.setRequestProperty("Gleap-Hash", userSession.getHash());
            }

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                JSONObject result = null;
                String input;
                while ((input = br.readLine()) != null) {
                    result = new JSONObject(input);
                }

                String id = null;
                String hash = null;

                if (result != null) {
                     if (result.has("gleapId")) {
                        id = result.getString("gleapId");
                    }

                    if (result.has("gleapHash")) {
                        hash = result.getString("gleapHash");
                    }


                    if (id != null && hash != null) {
                        UserSessionController.getInstance().mergeUserSession(id, hash);
                        UserSessionController.getInstance().setSessionLoaded(true);
                    }

                    GleapUserProperties gleapUserProperties = new GleapUserProperties();
                    if(result.has("name")) {
                        gleapUserProperties.setName(result.getString("name"));
                    }

                    if(result.has("email")) {
                        gleapUserProperties.setEmail(result.getString("email"));
                    }

                    if(result.has("value")) {
                        gleapUserProperties.setValue(result.getDouble("value"));
                    }

                    if(result.has("customData")) {
                        gleapUserProperties.setCustomData(result.getJSONObject("customData"));
                    }

                    String userId ="";
                    if(result.has("userId")) {
                        userId = result.getString("userId");
                    }

                    UserSessionController.getInstance().setGleapUserSession(new GleapUser(userId, gleapUserProperties));
                    UserSessionController.getInstance().getGleapUserSession().setNew(false);

                    ActivityUtil.getCurrentActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Handler mainHandler = new Handler(Looper.getMainLooper());
                            Runnable gleapRunnable = new Runnable() {
                                @Override
                                public void run() throws RuntimeException {
                                    if (GleapConfig.getInstance().getRegisterPushMessageGroupCallback() != null && userSession != null) {
                                        String hash = userSession.getHash();
                                        if(!hash.equals("")) {
                                            GleapConfig.getInstance().getRegisterPushMessageGroupCallback().invoke("gleapuser-" + hash);
                                        }
                                    }
                                }
                            };
                            mainHandler.post(gleapRunnable);
                        }
                    });
                    if(GleapConfig.getInstance().getRegisterPushMessageGroupCallback() != null) {
                        GleapConfig.getInstance().getRegisterPushMessageGroupCallback().invoke("gleapuser-" + hash);
                    }
                    GleapInvisibleActivityManger.getInstance().render(null, true);
                }

                if(GleapConfig.getInstance().getInitializationDoneCallback() != null) {
                    GleapConfig.getInstance().getInitializationDoneCallback().invoke();
                }

                if(this.callback != null) {
                    this.callback.invoke();
                    this.callback = null;
                }
            } catch (Exception e) {
                UserSessionController.getInstance().setSessionLoaded(true);
            }

        } catch (Exception e) {
            UserSessionController.getInstance().setSessionLoaded(true);
        }
        return 200;
    }

    public void setCallback(UserSessionLoadedCallback callback) {
        this.callback = callback;
    }

    public interface UserSessionLoadedCallback{
        void invoke();
    }
}
