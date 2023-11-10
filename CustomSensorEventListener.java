package com.example.lab3;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.os.Handler;
import android.os.Looper;

import java.util.List;

public class CustomSensorEventListener implements SensorEventListener {

    private muse_plus musePlusObject;
    public int steps;
    public float[] euler_angles = {0,0,0};
    private List<itemData> mDataList;
    public boolean setReset = false;
    private jdbcDatabase db;
    CustomRecyclerViewAdapter mAdapter;
    private float[] accelerometerValues;
    private float[] previousGravityValues = new float[3];
    private final double[] gravity = {0.0, 0.0, 0.0};
    private float[] linearAccelerationValues = new float[3];
    private final double[] acceleration = {0.0, 0.0, 0.0};
    public CustomSensorEventListener(/*List<itemData> dataList, muse_plus musePlusObject,CustomRecyclerViewAdapter mAdapter, jdbcDatabase db*/){
/*
        this.musePlusObject = musePlusObject;
        this.db = db;
        this.mAdapter = mAdapter;
        this.mDataList = dataList;
        */
        musePlusObject = new muse_plus();

    }
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (sensorEvent.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            //Fetching the sensor values

            float[] linearAccelerationValues = sensorEvent.values;
            double x = linearAccelerationValues[0];
            double y = linearAccelerationValues[1];
            double z = linearAccelerationValues[2];

            double alpha = 0.95;
            gravity[0] = alpha * gravity[0] + (1 - alpha) * x;
            gravity[1] = alpha * gravity[1] + (1 - alpha) * y;
            gravity[2] = alpha * gravity[2] + (1 - alpha) * z;

            acceleration[0] = x - gravity[0];
            acceleration[1] = y - gravity[1];
            acceleration[2] = z - gravity[2];

            double magnitude = Math.sqrt(Math.pow(acceleration[0], 2) + Math.pow(acceleration[1], 2) + Math.pow(acceleration[2], 2));
            boolean thresholdCrossed = (magnitude < 1.75 || magnitude > 3);

            if(!thresholdCrossed){
                steps += 1;
            }
            if(setReset) {
                setReset = false;
                steps = 0;
            }
            musePlusObject.update_gravity(sensorEvent.values);
        }

        //If there is a change in Accelerometer values
        if(sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
            //Fetching the accelerometer values
            accelerometerValues = sensorEvent.values;
            musePlusObject.update_acc(sensorEvent.values);

        }
        //If there is a change in Gyroscope values
        if(sensorEvent.sensor.getType() == Sensor.TYPE_GYROSCOPE){
            //Fetching the gyroscope values
            musePlusObject.update_gyro(sensorEvent.values, sensorEvent.timestamp);
            float[] tmp = musePlusObject.get_EulerAngles();
            euler_angles = (tmp == null) ? euler_angles : tmp;

            //compute euler angles

        }

        //If there is a change in Magnetic Field values
        /*if (sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            //Fetching the magnetic field values
            float[] magneticValues = sensorEvent.values;
            //Running muse plus
            musePlusObject.update_mag(sensorEvent.values, sensorEvent.timestamp);

        }
        */
        if (sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            float[] magneticValues = sensorEvent.values;
            if (magneticValues != null && musePlusObject != null) {
                musePlusObject.update_mag(magneticValues, sensorEvent.timestamp);
            }
        }
    }
    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
    /*
    private final Handler recyclerViewHandler = new Handler(Looper.getMainLooper());
    public Runnable updateTextViewRunnable = new Runnable() {
        @Override
        public void run() {
            if (mDataList == null) {
                return;
            }
            if (mDataList.size() >= 5) {
                // Get the latest sensor reading (or an average, etc.)
                mDataList.get(0).changeValue(String.format("%.3f, %.3f, %.3f", euler_angles[0], euler_angles[1], euler_angles[2]));
                mDataList.get(1).changeValue(String.valueOf(euler_angles[0]));
                mDataList.get(2).changeValue(String.valueOf(euler_angles[1]));
                mDataList.get(3).changeValue(String.valueOf(euler_angles[2]));
                mDataList.get(4).changeValue(String.valueOf(steps));
                mDataList.get(5).changeValue(String.valueOf((db.getCoordinates()[0])));
                mDataList.get(6).changeValue(String.valueOf((db.getCoordinates()[1])));
                mDataList.get(7).changeValue(String.valueOf((db.getDistance())));
                mAdapter.notifyDataSetChanged();
                recyclerViewHandler.postDelayed(this, 1000);
            }
        }
    };
    */

}
