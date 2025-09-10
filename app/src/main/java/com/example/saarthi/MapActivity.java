package com.example.saarthi;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private static final String TAG = "MapActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 101;

    private List<Checkpoint> checkpointList = new ArrayList<>();
    private List<Checkpoint> filteredCheckpointList = new ArrayList<>();
    private HashMap<String, Marker> checkpointMarkers = new HashMap<>();
    private List<Polyline> routePolylines = new ArrayList<>();
    private HashMap<Polyline, Integer> pathUsageCount = new HashMap<>();

    private FusedLocationProviderClient fusedLocationClient;
    private LatLng currentLocation;

    private Spinner pathTypeSpinner;
    private Button btnChangePath;
    private TextView tvPathInfo;

    private String selectedPathType = "normal";
    private int currentPathVariant = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Initialize UI elements
        pathTypeSpinner = findViewById(R.id.path_type_spinner);
        btnChangePath = findViewById(R.id.btn_change_path);
        tvPathInfo = findViewById(R.id.tv_path_info);

        // Setup path type spinner
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.path_types, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        pathTypeSpinner.setAdapter(adapter);

        pathTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedPathType = parent.getItemAtPosition(position).toString().toLowerCase();
                filterCheckpointsByType();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        btnChangePath.setOnClickListener(v -> {
            currentPathVariant = (currentPathVariant + 1) % 3; // Cycle through 3 path variants
            drawRoutes();
            updatePathInfo();
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);

        checkLocationPermission();
        loadCheckpoints();
    }

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            enableMyLocation();
        }
    }

    private void enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        mMap.setMyLocationEnabled(true);

        // Get current location continuously
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(5000); // update every 5 sec
        locationRequest.setFastestInterval(3000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        fusedLocationClient.requestLocationUpdates(locationRequest, new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                Location loc = locationResult.getLastLocation();
                if (loc != null) {
                    currentLocation = new LatLng(loc.getLatitude(), loc.getLongitude());
                    // Zoom camera to current location
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 16));

                    // Draw path to first checkpoint if available
                    if (!filteredCheckpointList.isEmpty()) {
                        LatLng firstCp = new LatLng(filteredCheckpointList.get(0).latitude, filteredCheckpointList.get(0).longitude);
                        drawWalkingPath(currentLocation, firstCp, 0, true);
                    }
                }
            }
        }, getMainLooper());
    }

    private void loadCheckpoints() {
        DatabaseReference checkpointRef = FirebaseDatabase.getInstance().getReference("checkpoints");
        checkpointRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                checkpointList.clear();
                for (DataSnapshot cpSnap : snapshot.getChildren()) {
                    Checkpoint cp = cpSnap.getValue(Checkpoint.class);
                    if (cp != null) checkpointList.add(cp);
                }

                // Sort checkpoints by number
                Collections.sort(checkpointList, Comparator.comparingInt(c -> c.number));

                // Filter checkpoints based on selected type
                filterCheckpointsByType();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load checkpoints: " + error.getMessage());
            }
        });
    }

    private void filterCheckpointsByType() {
        filteredCheckpointList.clear();

        for (Checkpoint cp : checkpointList) {
            if (selectedPathType.equals("all") ||
                    (cp.type != null && cp.type.equals(selectedPathType))) {
                filteredCheckpointList.add(cp);
            }
        }

        // Clear existing markers
        for (Marker marker : checkpointMarkers.values()) {
            marker.remove();
        }
        checkpointMarkers.clear();

        // Clear existing polylines
        for (Polyline polyline : routePolylines) {
            polyline.remove();
        }
        routePolylines.clear();
        pathUsageCount.clear();

        // Show markers with real-time user count
        setupRealTimeUserCount();

        // Draw walking paths between checkpoints
        drawRoutes();
    }

    private void setupRealTimeUserCount() {
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("user_locations");
        usersRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<UserLocation> allUsers = new ArrayList<>();
                for (DataSnapshot userSnap : snapshot.getChildren()) {
                    Double lat = userSnap.child("latitude").getValue(Double.class);
                    Double lng = userSnap.child("longitude").getValue(Double.class);
                    if (lat != null && lng != null) allUsers.add(new UserLocation(lat, lng));
                }

                // Update markers for all checkpoints
                for (Checkpoint cp : filteredCheckpointList) {
                    int peopleCount = 0;
                    LatLng cpLatLng = new LatLng(cp.latitude, cp.longitude);
                    for (UserLocation ul : allUsers) {
                        float[] distance = new float[1];
                        android.location.Location.distanceBetween(
                                cp.latitude, cp.longitude,
                                ul.latitude, ul.longitude,
                                distance
                        );
                        if (distance[0] <= 300) peopleCount++;
                    }

                    Marker marker = checkpointMarkers.get(cp.name);
                    if (marker == null) {
                        marker = mMap.addMarker(new MarkerOptions()
                                .position(cpLatLng)
                                .title("Checkpoint " + cp.number + " - " + cp.name)
                                .snippet("Type: " + cp.type + ", People: " + peopleCount));
                        checkpointMarkers.put(cp.name, marker);
                    } else {
                        marker.setSnippet("Type: " + cp.type + ", People: " + peopleCount);
                        marker.showInfoWindow();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load user locations: " + error.getMessage());
            }
        });
    }

    private void drawRoutes() {
        // Draw path from current location to first checkpoint
        if (currentLocation != null && !filteredCheckpointList.isEmpty()) {
            LatLng firstCp = new LatLng(filteredCheckpointList.get(0).latitude, filteredCheckpointList.get(0).longitude);
            drawWalkingPath(currentLocation, firstCp, 0, true);
        }

        // Draw paths between checkpoints
        for (int i = 0; i < filteredCheckpointList.size() - 1; i++) {
            LatLng origin = new LatLng(filteredCheckpointList.get(i).latitude, filteredCheckpointList.get(i).longitude);
            LatLng dest = new LatLng(filteredCheckpointList.get(i + 1).latitude, filteredCheckpointList.get(i + 1).longitude);
            drawWalkingPath(origin, dest, i + 1, false);
        }

        updatePathInfo();
    }

    private void drawWalkingPath(LatLng origin, LatLng dest, int pathIndex, boolean isToFirstCheckpoint) {
        // For demonstration, we'll create slightly different paths based on variant
        // In a real app, you would use different waypoints or alternative routes from Directions API
        String url = getDirectionsUrl(origin, dest, currentPathVariant);
        new DownloadTask(pathIndex, isToFirstCheckpoint).execute(url);
    }

    private String getDirectionsUrl(LatLng origin, LatLng dest, int variant) {
        String str_origin = "origin=" + origin.latitude + "," + origin.longitude;
        String str_dest = "destination=" + dest.latitude + "," + dest.longitude;
        String mode = "mode=walking";

        // Add alternative routes parameter if we want different paths
        String alternatives = "alternatives=true";

        String parameters = str_origin + "&" + str_dest + "&" + mode + "&" + alternatives;
        String output = "json";
        String apiKey = getString(R.string.google_maps_key);
        return "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters + "&key=" + apiKey;
    }

    // --- DownloadTask & ParserTask ---
    private class DownloadTask extends AsyncTask<String, Void, String> {
        private int pathIndex;
        private boolean isToFirstCheckpoint;

        public DownloadTask(int pathIndex, boolean isToFirstCheckpoint) {
            this.pathIndex = pathIndex;
            this.isToFirstCheckpoint = isToFirstCheckpoint;
        }

        @Override
        protected String doInBackground(String... url) {
            try {
                return downloadUrl(url[0]);
            } catch (Exception e) {
                e.printStackTrace();
                return "";
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null && !result.isEmpty()) {
                new ParserTask(pathIndex, isToFirstCheckpoint).execute(result);
            }
        }
    }

    private String downloadUrl(String strUrl) throws IOException {
        String data = "";
        InputStream iStream = null;
        HttpURLConnection urlConnection = null;
        try {
            URL url = new URL(strUrl);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.connect();
            iStream = urlConnection.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(iStream));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            data = sb.toString();
            br.close();
        } finally {
            if (iStream != null) iStream.close();
            if (urlConnection != null) urlConnection.disconnect();
        }
        return data;
    }

    private class ParserTask extends AsyncTask<String, Integer, List<LatLng>> {
        private int pathIndex;
        private boolean isToFirstCheckpoint;

        public ParserTask(int pathIndex, boolean isToFirstCheckpoint) {
            this.pathIndex = pathIndex;
            this.isToFirstCheckpoint = isToFirstCheckpoint;
        }

        @Override
        protected List<LatLng> doInBackground(String... jsonData) {
            List<LatLng> path = new ArrayList<>();
            try {
                JSONObject jObject = new JSONObject(jsonData[0]);
                if (!jObject.getString("status").equals("OK")) return path;

                DirectionsJSONParser parser = new DirectionsJSONParser();

                // For demonstration, select a different route based on currentPathVariant
                // In a real app, you would parse all available routes and select one
                path = parser.parse(jObject, currentPathVariant);

            } catch (Exception e) {
                e.printStackTrace();
            }
            return path;
        }

        @Override
        protected void onPostExecute(List<LatLng> result) {
            if (result != null && !result.isEmpty()) {
                PolylineOptions lineOptions = new PolylineOptions();
                lineOptions.addAll(result).width(10).color(getPathColor(pathIndex)).geodesic(true);

                // Remove existing polyline for this path if it exists
                if (pathIndex < routePolylines.size() && routePolylines.get(pathIndex) != null) {
                    routePolylines.get(pathIndex).remove();
                }

                Polyline polyline = mMap.addPolyline(lineOptions);

                // Store the polyline and set initial usage count to 0
                if (pathIndex >= routePolylines.size()) {
                    routePolylines.add(polyline);
                    pathUsageCount.put(polyline, 0);
                } else {
                    routePolylines.set(pathIndex, polyline);
                    pathUsageCount.put(polyline, 0);
                }

                // Add click listener to show path information
                mMap.setOnPolylineClickListener(polyline1 -> {
                    Integer count = pathUsageCount.get(polyline1);
                    Toast.makeText(MapActivity.this,
                            "Path usage: " + (count != null ? count : 0) + " people",
                            Toast.LENGTH_SHORT).show();
                });

                // Fit camera to show all checkpoints and current location
                LatLngBounds.Builder builder = new LatLngBounds.Builder();
                for (Checkpoint cp : filteredCheckpointList)
                    builder.include(new LatLng(cp.latitude, cp.longitude));
                if (currentLocation != null) builder.include(currentLocation);
                mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100));
            }
        }
    }

    private int getPathColor(int pathIndex) {
        // Different colors for different paths
        int[] colors = {Color.BLUE, Color.RED, Color.GREEN, Color.MAGENTA, Color.CYAN};
        return colors[pathIndex % colors.length];
    }

    private void updatePathInfo() {
        tvPathInfo.setText("Current path variant: " + (currentPathVariant + 1) + " | Usage: 0 people");
    }

    // --- Data Models ---
    public static class Checkpoint {
        public double latitude;
        public double longitude;
        public String name;
        public int number;
        public String type; // normal, vip, disabled

        public Checkpoint() {}
    }

    public static class UserLocation {
        public double latitude;
        public double longitude;
        public UserLocation(double lat, double lng) {
            this.latitude = lat;
            this.longitude = lng;
        }
    }
}