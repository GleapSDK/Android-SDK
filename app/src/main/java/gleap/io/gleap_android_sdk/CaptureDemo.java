package gleap.io.gleap_android_sdk;

import android.content.Intent;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import io.gleap.Gleap;

/**
 * A screen to try capture requests on: a GL surface (SurfaceView content), a password field
 * and a masked view (painted black in captures), a dialog, a running clock (motion for
 * recordings) and a way to change the activity while the capture bar is shown.
 */
public class CaptureDemo extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private GLSurfaceView glView;
    private TextView clock;
    private final long startedAt = SystemClock.uptimeMillis();

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long ms = SystemClock.uptimeMillis() - startedAt;
            clock.setText(String.format(Locale.ROOT, "Clock %02d:%02d.%d", ms / 60000, (ms / 1000) % 60, (ms / 100) % 10));
            handler.postDelayed(this, 100);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capture_demo);

        glView = findViewById(R.id.capture_gl);
        glView.setEGLContextClientVersion(2);
        glView.setRenderer(new MovingSquareRenderer());

        clock = findViewById(R.id.capture_clock);
        Gleap.getInstance().maskView(findViewById(R.id.capture_masked));

        findViewById(R.id.capture_open_widget).setOnClickListener(view -> Gleap.getInstance().open());
        findViewById(R.id.capture_dialog).setOnClickListener(view -> showDialog());
        findViewById(R.id.capture_close_widget).setOnClickListener(view -> Gleap.getInstance().close());
        findViewById(R.id.capture_next).setOnClickListener(view ->
                startActivity(new Intent(CaptureDemo.this, CaptureDemo.class)));
        findViewById(R.id.capture_log).setOnClickListener(view -> {
            Gleap.getInstance().log("Capture demo: a log line");
            Gleap.getInstance().trackEvent("capture-demo-tap");
        });

        TextView captureSwitch = findViewById(R.id.capture_switch);
        captureSwitch.setOnClickListener(view -> {
            captureEnabled = !captureEnabled;
            Gleap.getInstance().setCaptureEnabled(captureEnabled);
            captureSwitch.setText(captureEnabled ? "Screen capture: on" : "Screen capture: off");
        });
        TextView logsSwitch = findViewById(R.id.capture_logs_switch);
        logsSwitch.setOnClickListener(view -> {
            remoteLogs = !remoteLogs;
            Gleap.getInstance().setRemoteLogCollectionEnabled(remoteLogs);
            logsSwitch.setText(remoteLogs ? "Remote logs: on" : "Remote logs: off");
        });
    }

    // Process-wide like the SDK's switches (a new screen starts with them on again).
    private static boolean captureEnabled = true;
    private static boolean remoteLogs = true;

    private void showDialog() {
        EditText secret = new EditText(this);
        secret.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        secret.setText("dialog-secret");
        new AlertDialog.Builder(this)
                .setTitle("A dialog to capture")
                .setMessage("The capture bar moves above this dialog. Its password field is painted black.")
                .setView(secret)
                .setPositiveButton("Close", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        glView.onResume();
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        glView.onPause();
        super.onPause();
    }

    /**
     * A changing background with a yellow square moving left and right.
     */
    private static final class MovingSquareRenderer implements GLSurfaceView.Renderer {
        private final long start = SystemClock.uptimeMillis();
        private int width;
        private int height;

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            this.width = width;
            this.height = height;
            GLES20.glViewport(0, 0, width, height);
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            float t = (SystemClock.uptimeMillis() - start) / 1000f;
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
            GLES20.glClearColor(0.1f, 0.45f + 0.35f * (float) Math.sin(t), 0.35f, 1f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            int size = Math.max(1, height / 2);
            int x = (int) ((width - size) * (0.5 + 0.5 * Math.sin(t * 1.3)));
            GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
            GLES20.glScissor(x, height / 4, size, size);
            GLES20.glClearColor(1f, 0.85f, 0f, 1f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        }
    }
}
