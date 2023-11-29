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

            GleapUser gleapUser = UserSessionController.getInstance().getGleapUserSession();
            if(gleapUser != null && !gleapUser.isNew() ) {
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
                } catch (Exception ignore) {
                }
                return 200;
            }

            if (GleapConfig.getInstance().getUnRegisterPushMessageGroupCallback() != null && userSession != null) {
                String hash = userSession.getHash();
                if (!hash.equals("")) {
                    try {
                        ActivityUtil.getCurrentActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Handler mainHandler = new Handler(Looper.getMainLooper());
                                Runnable gleapRunnable = new Runnable() {
                                    @Override
                                    public void run() throws RuntimeException {
                                        GleapConfig.getInstance().getUnRegisterPushMessageGroupCallback().invoke("gleapuser-" + hash);
                                    }
                                };
                                mainHandler.post(gleapRunnable);
                            }
                        });
                    } catch (Exception ignore) {
                    }
                }
            }

            try {

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
                            jsonObject.put("phone", gleapUser.getGleapUserProperties().getPhone());
                            jsonObject.put("plan", gleapUser.getGleapUserProperties().getPlan());
                            jsonObject.put("companyName", gleapUser.getGleapUserProperties().getCompanyName());
                            jsonObject.put("companyId", gleapUser.getGleapUserProperties().getCompanyId());
                            JSONObject customData = gleapUser.getGleapUserProperties().getCustomData();
                            if (customData != null) {
                                jsonObject = JsonUtil.mergeJSONObjects(jsonObject, customData);
                            }
                        }

                       jsonObject.put("lang", GleapConfig.getInstance().getLanguage());
                    } catch (Exception ex) {

                    }
                }

                if (!jsonObject.has( "userId")) {
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

                        if(result.has("value")) {
                            gleapUserProperties.setValue(result.getDouble("value"));
                        }

                        if (result.has("plan")) {
                            gleapUserProperties.setPlan(result.getString("plan"));
                        }

                        if (result.has("companyName")) {
                            gleapUserProperties.setCompanyName(result.getString("companyName"));
                        }

                        if (result.has("companyId")) {
                            gleapUserProperties.setCompanyId(result.getString("companyId"));
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

                        UserSessionController.getInstance().setSessionLoaded(true);
                        ActivityUtil.getCurrentActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Handler mainHandler = new Handler(Looper.getMainLooper());
                                Runnable gleapRunnable = new Runnable() {
                                    @Override
                                    public void run() throws RuntimeException {
                                        if (GleapConfig.getInstance().getRegisterPushMessageGroupCallback() != null) {
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
                    }

                    // Send session started
                    gleapUser.setNew(false);

                    // Restart event service.
                    GleapEventService.getInstance().stop(false);
                    GleapEventService.getInstance().startWebSocketListener();
                } catch (Exception e) {
                    UserSessionController.getInstance().setSessionLoaded(true);
                    if (UserSessionController.getInstance() != null) {
                        UserSessionController.getInstance().clearUserSession();
                        UserSessionController.getInstance().setSessionLoaded(true);
                    }
                    isLoaded = true;
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
