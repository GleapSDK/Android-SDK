package gleap.io.gleap_android_sdk;

import androidx.appcompat.app.AppCompatActivity;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;

import io.gleap.Gleap;
import io.gleap.Networklog;

public class NetworkLogging extends AppCompatActivity {

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
                new HttpCall().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
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