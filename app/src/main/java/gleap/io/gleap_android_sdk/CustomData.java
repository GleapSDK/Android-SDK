package gleap.io.gleap_android_sdk;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;

import org.json.JSONObject;

import io.gleap.Gleap;

public class CustomData extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_data);

        findViewById(R.id.bck_customData).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        findViewById(R.id.custom).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Gleap.getInstance().setCustomData("HEY", "YOI");
            }
        });

        findViewById(R.id.custom2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                JSONObject jsonObject = new JSONObject();

                try{
                    jsonObject.put("JSON", "HEY :)");
                }catch (Exception ex) {}

                Gleap.getInstance().attachCustomData(jsonObject);
            }
        });

        findViewById(R.id.custom3).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Gleap.getInstance().removeCustomDataForKey("HEY");
            }
        });

        findViewById(R.id.custom4).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Gleap.getInstance().clearCustomData();
            }
        });

        findViewById(R.id.custom_send).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Gleap.getInstance().sendSilentCrashReport("Gleap Custom Data" + (int)Math.floor(Math.random() * 1000) , Gleap.SEVERITY.LOW);
            }
        });

        findViewById(R.id.custom_error).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Gleap.getInstance().attachCustomData(null);
            }
        });

        findViewById(R.id.custom_error_2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Gleap.getInstance().setCustomData(null, null);
            }
        });
    }
}