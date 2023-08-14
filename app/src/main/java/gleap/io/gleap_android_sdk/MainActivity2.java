package gleap.io.gleap_android_sdk;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;

import org.json.JSONException;
import org.json.JSONObject;

import io.gleap.Gleap;
import io.gleap.GleapNotInitialisedException;

public class MainActivity2 extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        Gleap.getInstance().showFeedbackButton(false);

        findViewById(R.id.bck_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        findViewById(R.id.btn_feedback).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                    Gleap.getInstance().open();
            }
        });

        findViewById(R.id.btn_feedback_1).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Gleap.getInstance().startFeedbackFlow("bugreporting", false);

            }
        });

        findViewById(R.id.btn_feedback_2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Gleap.getInstance().startFeedbackFlow("bugreporting", true);

            }
        });
    }
}