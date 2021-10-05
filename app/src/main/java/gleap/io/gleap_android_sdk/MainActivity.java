package gleap.io.gleap_android_sdk;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;


import io.gleap.Gleap;


public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.button3).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    Gleap.getInstance().sendSilentBugReport("A", "b", Gleap.SEVERITY.MEDIUM);
                   Gleap.getInstance().startFeedbackFlow();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
}

