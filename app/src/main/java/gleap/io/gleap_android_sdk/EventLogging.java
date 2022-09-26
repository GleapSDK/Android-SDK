package gleap.io.gleap_android_sdk;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;

import org.json.JSONObject;

import java.util.logging.Level;
import java.util.logging.Logger;

import io.gleap.Gleap;
import io.gleap.GleapLogLevel;

public class EventLogging extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_logging);

        findViewById(R.id.bck_event).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        findViewById(R.id.event).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Gleap.getInstance().disableConsoleLog();
                Gleap.getInstance().trackEvent("HEY");
                Gleap.getInstance().log("THIS IS A CRIT", GleapLogLevel.ERROR);
                Gleap.getInstance().log("THIS IS A INFO", GleapLogLevel.INFO);
                Gleap.getInstance().log("THIS IS A WARN", GleapLogLevel.WARNING);
                Gleap.getInstance().log("THIS IS A INFO EMPTS");
            }
        });

        findViewById(R.id.event2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                JSONObject jsonObject = new JSONObject();
                try{
                    jsonObject.put("This is ", "an event log");
                }catch (Exception ex) {

                }
                System.out.println("THIS SHOULD BE AFTERWARDS!!!");
                Logger.getAnonymousLogger().log(Level.INFO, "HEY THIS IS IT?" );
                Gleap.getInstance().trackEvent("THIS IS AN EVENT", jsonObject);
            }
        });

        findViewById(R.id.event_error).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Gleap.getInstance().trackEvent(null);
            }
        });

        findViewById(R.id.event_error2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Gleap.getInstance().trackEvent(null, null);
            }
        });

        findViewById(R.id.event_send).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Gleap.getInstance().sendSilentCrashReport("Gleap Event log" + (int)Math.floor(Math.random() * 1000) , Gleap.SEVERITY.LOW);
            }
        });
    }
}