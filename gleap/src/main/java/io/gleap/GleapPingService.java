package io.gleap;

import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;
 class GleapPingService {
    private static GleapPingService instance;
    private final static int TIMEINTERVAL = 7500; //7.5 seconds
     private final String httpsUrl = GleapConfig.getInstance().getApiUrl() + "/sessions/ping";

    private GleapPingService(){
        System.out.println(httpsUrl);
    }

    public static GleapPingService getInstance() {
        if(instance == null ){
            instance = new GleapPingService();
        }
        return instance;
    }

    Handler mHandler;
    Runnable mHandlerTask = new Runnable()
    {
        @Override
        public void run() {
            new PingCall().execute();
            mHandler.postDelayed(mHandlerTask, TIMEINTERVAL);
        }
    };

    public void start(){
        //run your Task
        mHandlerTask.run();
    }

    private void stopTask()
    {
        // stop your Task
        mHandler.removeCallbacks(mHandlerTask);
    }

    private class PingCall extends AsyncTask<String,Void,Integer> {

        @Override
        protected Integer doInBackground(String... params) {
            URL url;
            try {
                url = new URL(httpsUrl);
                HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
                con.connect();
                readResponse(con);
            } catch (IOException e) {
            }
            return 200;
        }

        private void readResponse(HttpsURLConnection con) throws IOException {

            if (con != null) {

                try {
                    BufferedReader br =
                            new BufferedReader(
                                    new InputStreamReader(con.getInputStream()));
                    String input;
                    JSONObject result = null;
                    while ((input = br.readLine()) != null) {
                        result = new JSONObject(input);
                    }
                    br.close();

                    if (result != null) {
                        if (result.has("actionType")) {
                            if (result.has("outbound")) {
                                GleapConfig.getInstance().setAction(new GleapAction(result.getString("actionType"), result.getString("outbound")));

                                try {
                                    Gleap.getInstance().startFeedbackFlow(GleapConfig.getInstance().getAction().getActionType());
                                } catch (GleapNotInitialisedException e) {
                                }
                            }

                            if(result.has("notification")) {
                                System.out.println(result.getString("notification"));
                            }
                        }
                    }
                    con.disconnect();
                } catch (Exception ex) {
ex.printStackTrace();
                }

            }

        }
    }

}
