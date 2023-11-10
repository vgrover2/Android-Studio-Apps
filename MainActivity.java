package com.example.lab3;

import org.tensorflow.lite.Interpreter;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class MainActivity extends AppCompatActivity implements LocationListener {
    //Handler Object to send the updated step counts to UI Thread
    private final Handler stepHandler = new Handler(Looper.getMainLooper());
    DatabaseReference mDatabase;
    private Button btnShowMap;
    private Button crossingButton;
    private HandlerThread handlerThread;
    private Handler handler;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private float current_heading = 0;

    protected Interpreter tflite;
    private String res;

    float inferenceResult = 0;

    CustomSensorEventListener mySensorListener;
    MapActivity mapActivity;
    OHA myOHA = new OHA();
    private LinkedList<float[]> modelInputs = new LinkedList<>();
    private LocationManager locationManager;
    private muse_plus musePlusObject;
    private static final int PERMISSION_REQUEST_CODE = 123;

    // Task 1.  recycler view
    //      2. euler angles, real-time view of euler angles in recycler view
    //      3. each 100 step a new item

    //RecyclerView
    CustomRecyclerViewAdapter mAdapter;
    RecyclerView mRecyclerView;
    List<itemData> mDataList;
    private jdbcDatabase db = new jdbcDatabase();

    // update recycler view
    private static final long UPDATE_INTERVAL_MS = 500;
    private static final long DELAY_TIME_MS = 500;
    private final Handler recyclerViewHandler = new Handler(Looper.getMainLooper());
    private final Runnable updateTextViewRunnable = new Runnable() {
        @Override
        public void run() {
            if (mDataList == null) {
                return;
            }
            if (mDataList.size() >= 5) {
                // Get the latest sensor reading (or an average, etc.)
                mDataList.get(0).changeValue(String.format("%.3f, %.3f, %.3f", mySensorListener.euler_angles[0], mySensorListener.euler_angles[1], mySensorListener.euler_angles[2]));
                mDataList.get(1).changeValue(String.valueOf(mySensorListener.euler_angles[0]));
                mDataList.get(2).changeValue(String.valueOf(mySensorListener.euler_angles[1]));
                mDataList.get(3).changeValue(String.valueOf(mySensorListener.euler_angles[2]));
                mDataList.get(4).changeValue(String.valueOf(mySensorListener.steps));
                mDataList.get(5).changeValue(String.valueOf((db.getCoordinates()[0])));
                mDataList.get(6).changeValue(String.valueOf((db.getCoordinates()[1])));
                mDataList.get(7).changeValue(String.valueOf((db.getDistance())));
                mDataList.get(8).changeValue(String.valueOf(current_heading));
                mDataList.get(10).changeValue(String.valueOf(inferenceResult));

                float[] data = modelInputs.get(0);

                StringBuilder sb = new StringBuilder("Model Inputs: ");
                for(float value : data){
                    sb.append(String.format("%.3f ", value));
                }
                String formattedData = sb.toString();
                mDataList.get(9).changeValue(formattedData);
                //mDataList.get(9).changeValue(String.valueOf(modelInputs.get(0)));

                // Update mDataList with the contents of the modelInputs queue
                /*
                int i = 9; // Start from index 5 (after existing data)
                for (float[] pair : modelInputs) {
                    String distance = String.format("%.3f", pair[0]);
                    String cosineValue = String.format("%.3f", pair[1]);
                    if (i < mDataList.size()) {
                        mDataList.get(i).changeValue("Distance to Road Center: " + distance);
                        i++;
                    }
                    if (i < mDataList.size()) {
                        mDataList.get(i).changeValue("Cosine(lr_ref_angle - current_heading): " + cosineValue);
                        i++;
                    }
                }
                */
                mAdapter.notifyDataSetChanged();
                recyclerViewHandler.postDelayed(this, UPDATE_INTERVAL_MS);
            }
        }
    };

    //Function to handle the click of Reset Button
    public void stepReset(View v){
        mySensorListener.setReset = true;
    }
    //Main Create Function

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDatabase = FirebaseDatabase.getInstance().getReference();

        btnShowMap = findViewById(R.id.btnShowMap);
        btnShowMap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v){
                double latitude = -89.42318398571551;
                double longitude = 43.07263972183441;

                Intent intent = new Intent(MainActivity.this, MapActivity.class);

                startActivity(intent);
            }
        });

        crossingButton = findViewById(R.id.btnCrossing);
        TextView predictionTextView = findViewById(R.id.predictionTextView);
/*
        crossingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (current_heading > 0){
                    onSuccessfulPrediction();
                    //predictionTextView.setText("Prediction: Crossing");
                } else {
                    //predictionTextView.setText("Prediction: Not Crossing");
                }
            }
        });
*/
        try {
            tflite = new Interpreter(loadModelFile("model_2lstm_d_cosHR.tflite"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        /*
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                inferenceResult = performInference(tflite, modelInputs);

            }
        });
        */

        handlerThread = new HandlerThread("HandlerThread");
        handlerThread.start();

        handler = new Handler(handlerThread.getLooper());
        handler.post(updateRunnable);

        db = new jdbcDatabase();
        // Call the method to retrieve nearest distance
        String longitude = "43.072719";
        String latitude = "-89.423402";
        db.getExtraConnection(longitude, latitude);
        // Get the nearest distance
        float nearestDistance = db.getDistance();
        Log.d("MyTag", "Nearest Distance: " + nearestDistance);

        //define list contents
        mDataList = new ArrayList<>();
        mDataList = getInitData();

        //Linking the UI Elements
        mRecyclerView = findViewById(R.id.recyclerView);
        mAdapter = new CustomRecyclerViewAdapter(mDataList, this);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        //Creating a sensorManager Object
        SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mySensorListener = new CustomSensorEventListener(/*mDataList, musePlusObject, mAdapter, db*/);
        //Creating a Magnetic Field Listener
        sensorManager.registerListener(mySensorListener,
                sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                SensorManager.SENSOR_DELAY_FASTEST);

        //Creating a Linear Acceleration Listener
        sensorManager.registerListener(mySensorListener,
                sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION),
                SensorManager.SENSOR_DELAY_FASTEST);

        //Creating a Accelerometer Listener
        sensorManager.registerListener(mySensorListener,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_GAME);

        //Creating a Gyroscope listener
        sensorManager.registerListener(mySensorListener,
                sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                SensorManager.SENSOR_DELAY_GAME);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        //res = performInference(tflite, modelInputs);

        if(checkLocationPermissions()){
            requestLocationUpdates();
            fetchLastKnownLocation();
        } else {
            requestLocationPermissions();
        }
        inferenceResult = performInference(tflite, modelInputs);

    }
    private void recordToFirebase(long timestamp, Location location) {
        // Check if Firebase Database is properly initialized
        if (mDatabase == null) {
            // Handle the case where Firebase is not properly initialized
            return;
        }

        // Create a data map with timestamp, latitude, and longitude
        Map<String, Object> data = new HashMap<>();
        data.put("timestamp", timestamp);
        data.put("latitude", location.getLatitude());
        data.put("longitude", location.getLongitude());

        // Push the data to the "recordings" node in Firebase Database
        mDatabase.child("recordings").push().setValue(data);
    }
    private void onSuccessfulPrediction() {
        // Get the current timestamp
        long timestamp = System.currentTimeMillis();

        // Get the user's current location
        Location userLocation = getUserLocation();

        // Record the data to Firebase
        if (userLocation != null) {
            recordToFirebase(timestamp, userLocation);
        }
    }
    // Implementation of getUserLocation
    private Location getUserLocation() {
        Location location = null;

        // Use the LocationManager to get the user's last known location
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (location == null) {
                    location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }
            }
        }
        return location;
    }
    public float performInference(Interpreter tflite, LinkedList<float[]> modelInputs){
        //float [][][] inputVal = new float[modelInputs.size()][modelInputs.get(0).length][1];
        int batchSize = modelInputs.size();
        int inputSize = modelInputs.getFirst().length;
        float [][][] inputVal = new float[batchSize][1][inputSize];
        //float[][][] inputVal = new float[][][]{modelInputs.toArray(new float[0][])};
        /*for (int i = 0; i < modelInputs.size(); i++){
            inputVal[i][0] = modelInputs.get(i);
        }
        */
        for (int i = 0; i < batchSize; i++) {
            inputVal[i][0] = modelInputs.get(i);
        }
        float[][] output = new float[batchSize][1];

        //tflite.run(inputVal, output);

        // Extract the classification result from the output
        float classificationResult = output[0][0];

        // Check the classification result and perform an action
        if (classificationResult > 0.5) {
            // Output prediction indicates crossing
            //return "Crossing!";
            System.out.println("Output prediction: Crossing!");
            //Successful Prediction
            onSuccessfulPrediction();
        } else {
            // Output prediction does not indicate crossing
            //return "Not Crossing!";
            System.out.println("Output prediction: Not Crossing");
        }
        return classificationResult;
    }
    private MappedByteBuffer loadModelFile(String filename) throws IOException {
        AssetFileDescriptor assetFileDescriptor = getAssets().openFd(filename);
        FileInputStream fileInputStream = new FileInputStream(assetFileDescriptor.getFileDescriptor());
        FileChannel fileChannel = fileInputStream.getChannel();
        long startOffset = assetFileDescriptor.getStartOffset();
        long declaredLength = assetFileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
    @Override
    protected void onDestroy(){
        super.onDestroy();
        handler.removeCallbacks(updateRunnable);
        handlerThread.quit();
    }
    private boolean checkLocationPermissions(){
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED;
    }
    private void requestLocationPermissions(){
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                PERMISSION_REQUEST_CODE);
    }
    private void requestLocationUpdates(){
        if(checkLocationPermissions()){
            if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED){
                return;
            }
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 1, this);
        }
    }
    private void fetchLastKnownLocation(){
        if(checkLocationPermissions()){
            if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED){
                return;
            }
            Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if(lastKnownLocation != null){
                db.getExtraConnection(String.valueOf(lastKnownLocation.getLongitude()), String.valueOf(lastKnownLocation.getLatitude()));
            }
        }
    }
    @Override
    public void onLocationChanged(@NonNull Location location) {
        Toast.makeText(this, "Location changed: " + location.getLatitude() + ", " + location.getLongitude(), Toast.LENGTH_SHORT).show();

        float distanceToRoadCenter = db.getDistance();
        float lr_ref_angle = db.get_lr_ref_angle();
        //float current_heading = 0;
        if(location.hasBearing()){
            float bearing_from_GPS = location.getBearing();
            float[] euler_angles = mySensorListener.euler_angles;
            current_heading = myOHA.query_update(euler_angles, (float) bearing_from_GPS);
        }
        double cosValue = Math.cos(Math.toRadians(lr_ref_angle - current_heading));
        Pair<Float, Double> dataPair = new Pair<>(distanceToRoadCenter, cosValue);

        db.getExtraConnection(String.valueOf(location.getLongitude()), String.valueOf(location.getLatitude()));

        double longitude = location.getLongitude();
        double latitude = location.getLatitude();

        Intent intent = new Intent("location-update");
        intent.putExtra("longitude", longitude);
        intent.putExtra("latitude", latitude);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        /*
        LatLng updatedLocation = new LatLng(latitude, longitude);

        // Clear any existing markers (if needed)
        mapActivity.mMap.clear();

        // Add a marker for the updated location
        mapActivity.mMap.addMarker(new MarkerOptions().position(updatedLocation).title("Updated Location"));

        // Move the camera to the updated location
        mapActivity.mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(updatedLocation, 15));

*/

    }
    private Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            float distanceToRoadCenter = db.getDistance();
            float lr_ref_angle = db.get_lr_ref_angle();

            double cosValue = Math.cos(Math.toRadians(lr_ref_angle - current_heading));
            float [] dataPair = {(float) distanceToRoadCenter, (float) cosValue};
            //Pair<Float, Double> dataPair = new Pair<>(distanceToRoadCenter, cosValue);
            if(modelInputs.size() >= 80){
                modelInputs.poll();
            }
            modelInputs.offer(dataPair);
            handler.postDelayed(this, DELAY_TIME_MS);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        //Registering Handler
        recyclerViewHandler.post(updateTextViewRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        //Unregistering Handler
        recyclerViewHandler.removeCallbacks(updateTextViewRunnable);
    }
    public void addItem(View v){
        if(mySensorListener.steps != 0){
            mDataList.add(new itemData("Achievement", mySensorListener.steps /2 + " Steps!"));
        }
        mAdapter.notifyDataSetChanged();
    }
    private List<itemData> getInitData()
    {
        List<itemData> list = new ArrayList<>();
        list.add(new itemData("Euler Angles", ""));
        list.add(new itemData("Roll Value", "0.000"));
        list.add(new itemData("Pitch Value", "0.000"));
        list.add(new itemData("Yaw Value", "0.000"));
        list.add(new itemData("Steps:", "0"));
        list.add(new itemData("Longitude:", "0"));
        list.add(new itemData("Latitude:", "0"));
        list.add(new itemData("Distance to nearest road:", "0"));
        list.add(new itemData("Current Heading:", "0"));
        list.add(new itemData("Model Inputs:", ""));
        list.add(new itemData("model inference", ""));
        return list;
    }
}