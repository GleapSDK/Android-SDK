package io.gleap;

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
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;

public class GleapIdentifyService extends AsyncTask<Void, Void, Integer> {
    private static final String httpsUrl = GleapConfig.getInstance().getApiUrl() + "/sessions/identify";
    public boolean isLoaded = false;

    @Override
    protected Integer doInBackground(Void... voids) {
        try {
            if (UserSessionController.getInstance() == null) {
                return 200;
            }

            UserSession userSession = UserSessionController.getInstance().getUserSession();
            if (userSession == null) {
                try {
                    GleapUserSessionLoader gleapUserSessionLoader = new GleapUserSessionLoader();
                    gleapUserSessionLoader.setCallback(new GleapUserSessionLoader.UserSessionLoadedCallback() {
                        @Override
                        public void invoke() {
                            new GleapIdentifyService().execute();
                        }
                    });
                    Handler mainHandler = new Handler(Looper.getMainLooper());
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            gleapUserSessionLoader.execute();
                        }
                    });
                }catch (Exception ignore) {}
                return 200;
            }

            try {
                ActivityUtil.getCurrentActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        GleapInvisibleActivityManger.getInstance().clearMessages();
                    }
                });
            } catch (Exception ingore) {
            }

            try {
                GleapUser gleapUser = UserSessionController.getInstance().getGleapUserSession();

                URL url = new URL(httpsUrl);
                HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
                conn.setRequestProperty("Api-Token", GleapConfig.getInstance().getSdkKey());
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestMethod("POST");

                if (userSession.getId() != null && !userSession.getId().equals("")) {
                    conn.setRequestProperty("Gleap-Id", userSession.getId());
                }

                if (userSession.getHash() != null && !userSession.getHash().equals("")) {
                    conn.setRequestProperty("Gleap-Hash", userSession.getHash());
                }

                JSONObject jsonObject = new JSONObject();
                if (gleapUser != null) {
                    try {
                        if (gleapUser.getUserId() != null && !gleapUser.getUserId().equals("")) {
                            jsonObject.put("userId", gleapUser.getUserId());
                        }

                        if (gleapUser.getGleapUserProperties() != null) {
                            jsonObject.put("email", gleapUser.getGleapUserProperties().getEmail());
                            jsonObject.put("name", gleapUser.getGleapUserProperties().getName());
                            jsonObject.put("userHash", gleapUser.getGleapUserProperties().getHash());
                            if (gleapUser.getGleapUserProperties().getValue() != 0) {
                                jsonObject.put("value", gleapUser.getGleapUserProperties().getValue());
                            }
                            jsonObject.put("phone", gleapUser.getGleapUserProperties().getPhoneNumber());
                        }
                    } catch (Exception ex) {

                    }
                }

                if (!jsonObject.has("userId")) {
                    return 200;
                }

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonObject.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
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

                        GleapUserProperties gleapUserProperties = new GleapUserProperties();
                        if (result.has("name")) {
                            gleapUserProperties.setName(result.getString("name"));
                        }

                        if (result.has("email")) {
                            gleapUserProperties.setEmail(result.getString("email"));
                        }

                        String userId = "";

                        if (result.has("userId")) {
                            userId = result.getString("userId");
                        }

                        UserSessionController.getInstance().setGleapUserSession(new GleapUser(userId, gleapUserProperties));


                        if (id != null && hash != null) {
                            UserSessionController.getInstance().mergeUserSession(id, hash);
                            isLoaded = true;
                        }
                        GleapInvisibleActivityManger.getInstance().render(null, true);
                        UserSessionController.getInstance().setSessionLoaded(true);
                    }

                } catch (Exception e) {
                    UserSessionController.getInstance().setSessionLoaded(true);
                    e.printStackTrace();

                }

            } catch (Exception e) {
                if (UserSessionController.getInstance() != null) {
                    UserSessionController.getInstance().clearUserSession();
                    UserSessionController.getInstance().setSessionLoaded(true);
                }
                isLoaded = true;
            }
        } catch (Exception ignored) {
        }

        return 200;
    }

}
