package io.gleap;

import android.app.Application;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * Detects deliberate shake gestures while filtering out false positives from
 * device rotations. Uses a high-pass filter to remove gravity and requires
 * multiple acceleration peaks within a time window before triggering.
 */
class ShakeGestureDetector extends GleapDetector implements SensorEventListener {
    private static final float SHAKE_THRESHOLD_GRAVITY = 1.8F;
    private static final int SHAKE_SLOP_TIME_MS = 250;
    private static final int SHAKE_COUNT_RESET_TIME_MS = 1500;
    private static final int MIN_SHAKE_COUNT = 2;
    private static final float GRAVITY_FILTER_ALPHA = 0.8f;

    private long mShakeTimestamp;
    private int mShakeCount;
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;

    private final float[] mGravity = new float[3];
    private boolean mHasGravityEstimate = false;

    public ShakeGestureDetector(Application application) {
        super(application);
    }

    @Override
    public void initialize() {
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    @Override
    public void resume() {
        mHasGravityEstimate = false;
        mShakeCount = 0;
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    public void pause() {
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void unregister() {
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!mHasGravityEstimate) {
            mGravity[0] = event.values[0];
            mGravity[1] = event.values[1];
            mGravity[2] = event.values[2];
            mHasGravityEstimate = true;
            return;
        }

        // Low-pass filter to track gravity orientation over time
        mGravity[0] = GRAVITY_FILTER_ALPHA * mGravity[0] + (1 - GRAVITY_FILTER_ALPHA) * event.values[0];
        mGravity[1] = GRAVITY_FILTER_ALPHA * mGravity[1] + (1 - GRAVITY_FILTER_ALPHA) * event.values[1];
        mGravity[2] = GRAVITY_FILTER_ALPHA * mGravity[2] + (1 - GRAVITY_FILTER_ALPHA) * event.values[2];

        // High-pass filter: subtract gravity to isolate user-driven acceleration
        float x = event.values[0] - mGravity[0];
        float y = event.values[1] - mGravity[1];
        float z = event.values[2] - mGravity[2];

        float acceleration = (float) Math.sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH;

        if (acceleration < SHAKE_THRESHOLD_GRAVITY) {
            return;
        }

        final long now = System.currentTimeMillis();

        if (mShakeTimestamp + SHAKE_COUNT_RESET_TIME_MS < now) {
            mShakeCount = 0;
        }

        if (mShakeTimestamp + SHAKE_SLOP_TIME_MS > now) {
            return;
        }

        mShakeTimestamp = now;
        mShakeCount++;

        if (mShakeCount >= MIN_SHAKE_COUNT) {
            mShakeCount = 0;
            try {
                if (!GleapBug.getInstance().isDisabled()) {
                    this.takeScreenshot();
                    pause();
                }
            } catch (Exception e) {
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }
}
