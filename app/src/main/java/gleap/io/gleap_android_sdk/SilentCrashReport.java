package gleap.io.gleap_android_sdk;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import org.json.JSONObject;

import io.gleap.Gleap;
import io.gleap.callbacks.FeedbackSentCallback;

public class SilentCrashReport extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_silent_bug_report);
        findViewById(R.id.bck_silence).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        findViewById(R.id.button3).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Gleap.getInstance().sendSilentCrashReport("This is silent, Severity LOW", Gleap.SEVERITY.LOW);
            }
        });

        findViewById(R.id.button4).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    JSONObject exclude = new JSONObject();
                    exclude.put("consoleLog", true);
                    Gleap.getInstance().sendSilentCrashReport("This is silent, Severity LOW, exclude ConsoleLog", Gleap.SEVERITY.LOW, exclude);
                }catch (Exception ex) {
ex.printStackTrace();
                }
            }
        });

        findViewById(R.id.button5).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    JSONObject exclude = new JSONObject();

                    exclude.put("customData",true);
                    Gleap.getInstance().sendSilentCrashReport("This is silent, Severity LOW, exclude ConsoleLog with callback", Gleap.SEVERITY.LOW, exclude);
                }catch (Exception ex) {

                }
            }
        });

        findViewById(R.id.button_error).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Gleap.getInstance().sendSilentCrashReport(null, null, null);
            }
        });

        findViewById(R.id.button6).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Gleap.getInstance().sendSilentCrashReport("This is silent, Severity HIGH", Gleap.SEVERITY.HIGH);
            }
        });
    }
}