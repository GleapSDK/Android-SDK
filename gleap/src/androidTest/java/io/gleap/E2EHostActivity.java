package io.gleap;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

/**
 * The app screen the e2e tests run on (the widget opens over it, screenshots show it).
 */
public class E2EHostActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView text = new TextView(this);
        text.setText(getClass().getSimpleName());
        text.setTextSize(28);
        text.setGravity(Gravity.CENTER);
        text.setBackgroundColor(Color.rgb(230, 240, 255));
        setContentView(text);
    }
}
