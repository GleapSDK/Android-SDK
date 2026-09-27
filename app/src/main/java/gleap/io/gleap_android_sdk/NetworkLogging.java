package gleap.io.gleap_android_sdk;

import androidx.appcompat.app.AppCompatActivity;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;

import io.gleap.Gleap;
import io.gleap.GleapOkHttpInterceptor;
import io.gleap.Networklog;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class NetworkLogging extends AppCompatActivity {
    // Every request of this client shows up in the network logs of the next ticket.
    private final OkHttpClient client = new OkHttpClient.Builder()
            .addInterceptor(new GleapOkHttpInterceptor())
            .build();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_network_logging);

        //  new HttpCall().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        findViewById(R.id.bck_network).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        findViewById(R.id.network).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Gleap.getInstance().trackEvent("NEW");

                new HttpCall().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
                    @Override
                    public void run() {
                        Request request = new Request.Builder()
                                .url("https://613750b8eac1410017c18290.mockapi.io/key/1")
                                .build();
                        try (Response response = client.newCall(request).execute()) {
                            response.body().string();
                        } catch (Exception e) {
                            // Failed requests are logged by the interceptor as well.
                        }
                    }
                });
                Gleap.getInstance().trackEvent("HEY");
            }
        });


        findViewById(R.id.network_clear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Gleap.getInstance().attachNetworkLogs(new Networklog[10]);
            }
        });

        findViewById(R.id.network_send).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Gleap.getInstance().sendSilentCrashReport("Gleap Event log" + (int)Math.floor(Math.random() * 1000) , Gleap.SEVERITY.LOW);
            }
        });
    }
}