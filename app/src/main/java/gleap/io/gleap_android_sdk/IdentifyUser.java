package gleap.io.gleap_android_sdk;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;

import org.json.JSONObject;

import io.gleap.Gleap;
import io.gleap.GleapSessionProperties;

public class IdentifyUser extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_identify_user);

        //Gleap.getInstance().showFeedbackButton(true);

        findViewById(R.id.bck_btn_identify).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        findViewById(R.id.btn_identify).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Gleap.getInstance().identifyUser(String.valueOf(Math.floor(Math.random() * 100)));
            }
        });

        findViewById(R.id.btn_identify2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                GleapSessionProperties gleapSessionProperties = new GleapSessionProperties("12", "Test User", "test@email.com");
                JSONObject jsonObject = new JSONObject();
                try{
                    jsonObject.put("customProperty", 1337);
                    jsonObject.put("customStringProperty","STRING PROPERTY");
                } catch (Exception custom) {
                    custom.printStackTrace();
                }
                gleapSessionProperties.setAvatar("https://picsum.photos/354/354");
                gleapSessionProperties.setCustomData(jsonObject);
                gleapSessionProperties.setCompanyId("COM12");
                gleapSessionProperties.setCompanyName("COMAAAAAA");
                gleapSessionProperties.setPlan("asdfasdfs");
                gleapSessionProperties.setSla(100);
                Gleap.getInstance().identifyUser("1338", gleapSessionProperties, null);
            }
        });

        findViewById(R.id.btn_identify22).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                GleapSessionProperties gleapSessionProperties = new GleapSessionProperties("12", "Test User", "test@email.com");
                JSONObject jsonObject = new JSONObject();
                try{
                    jsonObject.put("customProperty", 1337);
                    jsonObject.put("customStringProperty2","STRING PROPERTY");
                }catch (Exception custom) {
                    custom.printStackTrace();
                }
                gleapSessionProperties.setCustomData(jsonObject);
                gleapSessionProperties.setCompanyId("COM12");
                gleapSessionProperties.setCompanyName("COMAAAAAA");
                gleapSessionProperties.setPlan("asdfasdfs");
                Gleap.getInstance().updateContact(gleapSessionProperties);
            }
        });

        findViewById(R.id.btn_identify3).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                GleapSessionProperties gleapSessionProperties = new GleapSessionProperties("12", "Test User", "test@email.com", "f60d2a8960f5e2711159d72b67695014a05aa576023d77056bb27e7d7a96b4a6");
                Gleap.getInstance().identifyUser("1234", gleapSessionProperties);
            }
        });

        findViewById(R.id.btn_identify_error).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                GleapSessionProperties gleapSessionProperties = new GleapSessionProperties(null, null, null, null);
                Gleap.getInstance().identifyUser(null, gleapSessionProperties);
            }
        });

        findViewById(R.id.btn_identify_value).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                GleapSessionProperties gleapSessionProperties = new GleapSessionProperties("13", "VALUE boy", "test@email.com");
                gleapSessionProperties.setValue(20);
                gleapSessionProperties.setPhone("+436502425552");
                Gleap.getInstance().identifyUser("13", gleapSessionProperties);
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