package com.example.lab3;

import androidx.fragment.app.FragmentActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.example.lab3.databinding.ActivityMapBinding;

import android.view.View;
import android.widget.Button;




public class MapActivity extends FragmentActivity implements OnMapReadyCallback {

    public GoogleMap mMap;
    private Marker currentMarker;
    private ActivityMapBinding binding;
    BroadcastReceiver locationUpdateReceiver;
    //private double longitude;
    //private double latitude;
    MainActivity mainActivity;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        binding = ActivityMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Button backButton = findViewById(R.id.backButton);

        // Set a click listener for the back button
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Create an Intent to start the main activity
                Intent intent = new Intent(MapActivity.this, MainActivity.class);
                startActivity(intent);
            }
        });
/*
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
*/
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(googleMap -> {
            mMap = googleMap;

            if(getIntent().hasExtra("latitude") && getIntent().hasExtra("longitude")){
                double latitude = getIntent().getDoubleExtra("latitude", 0.0);
                double longitude = getIntent().getDoubleExtra("longitude", 0.0);
                updateMarkerPosition(latitude, longitude);
            }
        });
        locationUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals("location-update")) {
                    double latitude = intent.getDoubleExtra("latitude", 0.0);
                    double longitude = intent.getDoubleExtra("longitude", 0.0);

                    // Update the map with the new location data
                    updateMarkerPosition(latitude, longitude);
                }
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(locationUpdateReceiver, new IntentFilter("location-update"));
    }

    private void updateMarkerPosition(double latitude, double longitude){
        LatLng newLocation = new LatLng(latitude, longitude);
        if(currentMarker == null){
            currentMarker = mMap.addMarker(new MarkerOptions().position(newLocation).title("Current Location"));
        } else {
            currentMarker.setPosition(newLocation);
        }
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newLocation, 15));
    }
    @Override
    protected void onDestroy(){
        LocalBroadcastManager.getInstance(this).unregisterReceiver(locationUpdateReceiver);
        super.onDestroy();
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        LatLng updatedLocation  = new LatLng(43.07263972183441, -89.42318398571551);
        mMap.addMarker(new MarkerOptions().position(updatedLocation).title("Updated Location"));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(updatedLocation));
    }
}