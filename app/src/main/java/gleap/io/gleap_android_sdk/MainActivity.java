package gleap.io.gleap_android_sdk;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

import io.gleap.Gleap;
import io.gleap.GleapNotInitialisedException;


public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.button3).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    Gleap.getInstance().startFeedbackFlow();
                } catch (GleapNotInitialisedException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}

