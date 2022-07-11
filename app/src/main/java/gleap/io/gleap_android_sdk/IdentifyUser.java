package gleap.io.gleap_android_sdk;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;

import io.gleap.Gleap;
import io.gleap.GleapUser;
import io.gleap.GleapUserProperties;

public class IdentifyUser extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_identify_user);


        findViewById(R.id.bck_btn_identify).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });



        findViewById(R.id.btn_identify).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Gleap.getInstance().identifyUser("12");
            }
        });

        findViewById(R.id.btn_identify2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                GleapUserProperties gleapUserProperties = new GleapUserProperties("12", "Test User", "test@email.com");
                Gleap.getInstance().identifyUser("12", gleapUserProperties);
            }
        });

        findViewById(R.id.btn_identify3).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                GleapUserProperties gleapUserProperties = new GleapUserProperties("12", "Test User", "test@email.com", "f60d2a8960f5e2711159d72b67695014a05aa576023d77056bb27e7d7a96b4a6");
                Gleap.getInstance().identifyUser("1234", gleapUserProperties);
            }
        });

        findViewById(R.id.btn_identify_error).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                GleapUserProperties gleapUserProperties = new GleapUserProperties(null, null, null, null);
                Gleap.getInstance().identifyUser(null, gleapUserProperties);
            }
        });

        findViewById(R.id.btn_identify_value).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                GleapUserProperties gleapUserProperties = new GleapUserProperties("13", "VALUE boy", "test@email.com");
                gleapUserProperties.setValue(20);
                Gleap.getInstance().identifyUser("13", gleapUserProperties);
            }
        });


        findViewById(R.id.btn_clear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Gleap.getInstance().clearIdentity();
            }
        });

        findViewById(R.id.btn_send).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Gleap.getInstance().sendSilentCrashReport("Gleap Identity Check" + (int)Math.floor(Math.random() * 1000) , Gleap.SEVERITY.LOW);
            }
        });
    }
}