package io.gleap;

import android.app.Application;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * Detects the shake gesture of the phone
 */
class ShakeGestureDetector extends GleapDetector implements SensorEventListener {
    private static final float SHAKE_THRESHOLD_GRAVITY = 4.0F;
    private static final int SHAKE_SLOP_TIME_MS = 600;
    private long mShakeTimestamp;
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;

    public ShakeGestureDetector(Application application) {
        super(application);
    }

    @Override
    public void initialize() {
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager
                .getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

    }

    @Override
    public void resume() {
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
    public void onSensorChanged(SensorEvent sensorEvent) {
        float gX = sensorEvent.values[0] / SensorManager.GRAVITY_EARTH;
        float gY = sensorEvent.values[1] / SensorManager.GRAVITY_EARTH;
        float gZ = sensorEvent.values[2] / SensorManager.GRAVITY_EARTH;

        // gForce will be close to 1 when there is no movement.
        double gForce = Math.sqrt(gX * gX + gY * gY + gZ * gZ);
        if (gForce > SHAKE_THRESHOLD_GRAVITY) {
            final long now = System.currentTimeMillis();
            // ignore shake events too close to each other (600ms)
            if (mShakeTimestamp + SHAKE_SLOP_TIME_MS > now) {
                return;
            }

            mShakeTimestamp = now;
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
