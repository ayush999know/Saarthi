package com.example.saarthi;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.CountDownTimer;
import com.google.android.gms.maps.model.MapStyleOptions;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.maps.android.PolyUtil;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VipFragment extends Fragment implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener, GoogleMap.OnMapClickListener {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final String DIRECTIONS_API_KEY = "YOUR_ANDROID_API_KEY";
    private static final int LOCATION_UPDATE_INTERVAL = 1000;
    private static final int PEOPLE_COUNT_UPDATE_INTERVAL = 3000;
    private static final double RADIUS_METERS = 400;
    private static final double CHECKPOINT_RADIUS = 100;
    private static final double NEXT_CHECKPOINT_RADIUS = 500;
    private static final double ARRIVAL_RADIUS = 1000;
    private static final int CHECKPOINT_TIMER_MINUTES = 10;
    private static final int ARRIVAL_ESTIMATION_MINUTES = 30;
    private static final double AVERAGE_WALKING_SPEED = 1.4;

    private GoogleMap gMap;
    private ImageView btnExpandMap, btnMyLocation, btnClearRoute, btnFastTravel;
    private ImageView iconToilet, iconChangingRoom, iconFirstAid, iconCheckpoint, iconGhat, iconLounge;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private Polyline currentRoute;
    private Circle radiusCircle;

    // Firebase
    private DatabaseReference databaseReference;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;

    // Marker tracking
    private Map<String, List<Marker>> markerMap = new HashMap<>();
    private Map<String, Boolean> iconState = new HashMap<>();

    // Info bar views
    private CardView infoBar;
    private TextView tvInfoName, tvInfoType, tvInfoDescription, tvInfoTime;
    private TextView tvPeopleCount, tvSafetyStatus;
    private Button btnInfoNavigate, btnReserve, btnConcierge, btnFastTravelToNext;
    private ImageView btnCloseInfo;
    private Marker lastClickedMarker;
    private LatLng destinationLatLng;
    private LatLng currentUserLocation;

    // Checkpoint tracking - VIP only
    private List<Checkpoint> allVipCheckpoints = new ArrayList<>();
    private Checkpoint currentCheckpoint = null;
    private Checkpoint nextCheckpoint = null;
    private int currentCheckpointIndex = -1;

    // Checkpoint UI elements
    private TextView tvCurrentCheckpoint;
    private TextView tvNextCheckpoint;
    private TextView tvPeopleAtCurrent;
    private TextView tvPeopleAtNext;
    private TextView tvNextCheckpointDistance;
    private TextView tvArrivalEstimateCurrent;
    private TextView tvArrivalEstimateNext;
    private TextView tvCountdownTimer;
    private LinearLayout countdownLayout;
    private CardView checkpointCard;

    // Countdown timer
    private CountDownTimer countDownTimer;
    private long timeLeftInMillis = 0;
    private boolean timerRunning = false;

    // Handlers for periodic tasks
    private Handler locationUpdateHandler;
    private Handler peopleCountHandler;
    private Handler checkpointHandler;
    private Runnable locationUpdateRunnable;
    private Runnable peopleCountRunnable;
    private Runnable checkpointRunnable;

    // VIP Custom marker colors
    private static final float[] VIP_MARKER_COLORS = {
            BitmapDescriptorFactory.HUE_YELLOW,  // Gold for VIP checkpoints
            BitmapDescriptorFactory.HUE_VIOLET,  // Purple for VIP facilities
            BitmapDescriptorFactory.HUE_ORANGE,  // Orange for VIP lounges
            BitmapDescriptorFactory.HUE_ROSE,    // Pink for VIP exclusive areas
            BitmapDescriptorFactory.HUE_AZURE    // Blue for VIP amenities
    };

    // Checkpoint model class with VIP properties
    public static class Checkpoint {
        public String id;
        public String name;
        public int number;
        public double latitude;
        public double longitude;
        public int peopleCount;
        public String type; // "normal", "vip", "disabled"
        public boolean isExclusive; // VIP exclusive access
        public int capacity; // VIP capacity limit
        public Map<String, Boolean> arrivingUserIds = new HashMap<>();
        public Map<String, Long> userArrivalTimes = new HashMap<>();

        public Checkpoint() {}

        public Checkpoint(String id, String name, int number, double latitude, double longitude, String type, boolean isExclusive, int capacity) {
            this.id = id;
            this.name = name;
            this.number = number;
            this.latitude = latitude;
            this.longitude = longitude;
            this.type = type;
            this.isExclusive = isExclusive;
            this.capacity = capacity;
        }

        public boolean isVip() {
            return "vip".equals(type);
        }

        public boolean isExclusive() {
            return isExclusive;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_vip, container, false);

        // Initialize views
        btnExpandMap = root.findViewById(R.id.btnExpandMap);
        btnMyLocation = root.findViewById(R.id.btnMyLocation);
        btnClearRoute = root.findViewById(R.id.btnClearRoute);
        btnFastTravel = root.findViewById(R.id.btnFastTravel);
        iconToilet = root.findViewById(R.id.iconBathroom);
        iconChangingRoom = root.findViewById(R.id.iconChangingRoom);
        iconFirstAid = root.findViewById(R.id.iconFirstAid);
        iconCheckpoint = root.findViewById(R.id.iconCheckpoint);
        iconGhat = root.findViewById(R.id.iconGhat);
        iconLounge = root.findViewById(R.id.iconLounge);
        btnConcierge = root.findViewById(R.id.btnConcierge);
        btnFastTravelToNext = root.findViewById(R.id.btnFastTravelToNext);

        // Initialize info bar views
        infoBar = root.findViewById(R.id.infoBar);
        tvInfoName = root.findViewById(R.id.tvInfoName);
        tvInfoType = root.findViewById(R.id.tvInfoType);
        tvInfoDescription = root.findViewById(R.id.tvInfoDescription);
        tvInfoTime = root.findViewById(R.id.tvInfoTime);
        tvPeopleCount = root.findViewById(R.id.tvPeopleCount);
        tvSafetyStatus = root.findViewById(R.id.tvSafetyStatus);
        btnInfoNavigate = root.findViewById(R.id.btnInfoNavigate);
        btnReserve = root.findViewById(R.id.btnReserve);
        btnCloseInfo = root.findViewById(R.id.btnCloseInfo);

        // Initialize checkpoint views
        checkpointCard = root.findViewById(R.id.checkpointCard);
        tvCurrentCheckpoint = root.findViewById(R.id.tvCurrentCheckpoint);
        tvNextCheckpoint = root.findViewById(R.id.tvNextCheckpoint);
        tvPeopleAtCurrent = root.findViewById(R.id.tvPeopleAtCurrent);
        tvPeopleAtNext = root.findViewById(R.id.tvPeopleAtNext);
        tvNextCheckpointDistance = root.findViewById(R.id.tvNextCheckpointDistance);
        tvArrivalEstimateCurrent = root.findViewById(R.id.tvArrivalEstimateCurrent);
        tvArrivalEstimateNext = root.findViewById(R.id.tvArrivalEstimateNext);
        tvCountdownTimer = root.findViewById(R.id.tvCountdownTimer);
        countdownLayout = root.findViewById(R.id.countdownLayout);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        databaseReference = FirebaseDatabase.getInstance("https://saarthi-1d760-default-rtdb.firebaseio.com/").getReference();

        // Initialize marker map with VIP categories
        markerMap.put("checkpoint", new ArrayList<>());
        markerMap.put("toilet", new ArrayList<>());
        markerMap.put("changing-room", new ArrayList<>());
        markerMap.put("first-aid", new ArrayList<>());
        markerMap.put("ghat", new ArrayList<>());
        markerMap.put("lounge", new ArrayList<>()); // VIP exclusive

        // Initialize icon states
        iconState.put("checkpoint", false);
        iconState.put("toilet", false);
        iconState.put("changing-room", false);
        iconState.put("first-aid", false);
        iconState.put("ghat", false);
        iconState.put("lounge", false); // VIP exclusive

        // Initialize handlers
        locationUpdateHandler = new Handler();
        peopleCountHandler = new Handler();
        checkpointHandler = new Handler();

        // Check Google Play Services
        if (!checkGooglePlayServices()) {
            Toast.makeText(getContext(), "Google Play Services not available", Toast.LENGTH_LONG).show();
        }

        // Check API key
        checkApiKey();

        // Initialize map fragment
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.mapPreview);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        } else {
            Toast.makeText(getContext(), "Map fragment not found", Toast.LENGTH_SHORT).show();
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        // Initialize location request
        locationRequest = LocationRequest.create();
        locationRequest.setInterval(LOCATION_UPDATE_INTERVAL);
        locationRequest.setFastestInterval(LOCATION_UPDATE_INTERVAL);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        // Initialize location callback
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult != null) {
                    for (Location location : locationResult.getLocations()) {
                        updateUserLocation(location);
                    }
                }
            }
        };

        // Set up click listeners
        setupClickListeners(root);

        return root;
    }

    private boolean checkGooglePlayServices() {
        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = googleApiAvailability.isGooglePlayServicesAvailable(requireContext());
        return resultCode == ConnectionResult.SUCCESS;
    }

    private void checkApiKey() {
        try {
            if (DIRECTIONS_API_KEY.isEmpty() || DIRECTIONS_API_KEY.equals("YOUR_ANDROID_API_KEY")) {
                Toast.makeText(getContext(), "Please configure Google Maps API key", Toast.LENGTH_LONG).show();
                Log.e("API_KEY", "Invalid or missing Google Maps API key");
            }
        } catch (Exception e) {
            Log.e("API_KEY", "Error checking API key: " + e.getMessage());
        }
    }

    private void setupClickListeners(View root) {
        btnExpandMap.setOnClickListener(v -> {
            Intent intent = new Intent(requireActivity(), MapActivity.class);
            startActivity(intent);
        });

        btnMyLocation.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.getLastLocation()
                        .addOnSuccessListener(requireActivity(), location -> {
                            if (location != null) {
                                LatLng userLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                                gMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15));
                            }
                        });
            } else {
                requestLocationPermission();
            }
        });

        btnClearRoute.setOnClickListener(v -> {
            if (currentRoute != null) {
                currentRoute.remove();
                currentRoute = null;
                Toast.makeText(getContext(), "Route cleared", Toast.LENGTH_SHORT).show();
            }
        });

        btnFastTravel.setOnClickListener(v -> {
            showFastTravelOptions();
        });

        btnFastTravelToNext.setOnClickListener(v -> {
            if (nextCheckpoint != null) {
                fastTravelToCheckpoint(nextCheckpoint);
            }
        });

        btnConcierge.setOnClickListener(v -> {
            requestConciergeService();
        });

        btnCloseInfo.setOnClickListener(v -> hideInfoBar());

        btnInfoNavigate.setOnClickListener(v -> {
            if (lastClickedMarker != null && destinationLatLng != null) {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
                    fusedLocationClient.getLastLocation()
                            .addOnSuccessListener(requireActivity(), location -> {
                                if (location != null) {
                                    LatLng origin = new LatLng(location.getLatitude(), location.getLongitude());
                                    getRouteToDestination(origin, destinationLatLng);
                                } else {
                                    Toast.makeText(getContext(), "Unable to get current location", Toast.LENGTH_SHORT).show();
                                }
                            });
                } else {
                    requestLocationPermission();
                }
            } else {
                Toast.makeText(getContext(), "Please select a destination first", Toast.LENGTH_SHORT).show();
            }
        });

        btnReserve.setOnClickListener(v -> {
            if (lastClickedMarker != null) {
                reserveVipLocation(lastClickedMarker.getTitle());
            }
        });

        setupToggle(iconCheckpoint, "checkpoint", VIP_MARKER_COLORS[0]);
        setupToggle(iconToilet, "toilet", VIP_MARKER_COLORS[1]);
        setupToggle(iconChangingRoom, "changing-room", VIP_MARKER_COLORS[1]);
        setupToggle(iconFirstAid, "first-aid", VIP_MARKER_COLORS[1]);
        setupToggle(iconGhat, "ghat", VIP_MARKER_COLORS[1]);
        setupToggle(iconLounge, "lounge", VIP_MARKER_COLORS[2]); // VIP exclusive
    }

    @Override
    public void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
            startPeopleCountUpdates();
            loadVipCheckpoints(); // Load only VIP checkpoints
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        stopLocationUpdates();
        stopPeopleCountUpdates();
        stopCheckpointDetection();
        pauseTimer();
    }

    private void startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            try {
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
            } catch (SecurityException e) {
                Log.e("Location", "SecurityException: " + e.getMessage());
            }
        }
    }

    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    private void startPeopleCountUpdates() {
        peopleCountRunnable = new Runnable() {
            @Override
            public void run() {
                if (currentUserLocation != null) {
                    updatePeopleCountInRadius();
                }
                peopleCountHandler.postDelayed(this, PEOPLE_COUNT_UPDATE_INTERVAL);
            }
        };
        peopleCountHandler.postDelayed(peopleCountRunnable, PEOPLE_COUNT_UPDATE_INTERVAL);
    }

    private void stopPeopleCountUpdates() {
        if (peopleCountHandler != null && peopleCountRunnable != null) {
            peopleCountHandler.removeCallbacks(peopleCountRunnable);
        }
    }

    private void loadVipCheckpoints() {
        databaseReference.child("checkpoints").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                allVipCheckpoints.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Checkpoint checkpoint = snapshot.getValue(Checkpoint.class);
                    if (checkpoint != null && checkpoint.isVip()) { // Only load VIP checkpoints
                        checkpoint.id = snapshot.getKey();

                        // Get arriving user IDs and their arrival times
                        if (snapshot.child("arrivingUserIds").exists()) {
                            for (DataSnapshot userIdSnapshot : snapshot.child("arrivingUserIds").getChildren()) {
                                checkpoint.arrivingUserIds.put(userIdSnapshot.getKey(), true);
                            }
                        }

                        // Get user arrival times
                        if (snapshot.child("userArrivalTimes").exists()) {
                            for (DataSnapshot timeSnapshot : snapshot.child("userArrivalTimes").getChildren()) {
                                Long arrivalTime = timeSnapshot.getValue(Long.class);
                                if (arrivalTime != null) {
                                    checkpoint.userArrivalTimes.put(timeSnapshot.getKey(), arrivalTime);
                                }
                            }
                        }

                        allVipCheckpoints.add(checkpoint);
                    }
                }

                // Sort checkpoints by number
                Collections.sort(allVipCheckpoints, (c1, c2) -> Integer.compare(c1.number, c2.number));

                // Start checking for nearby VIP checkpoints
                startCheckpointDetection();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("Checkpoints", "Error loading VIP checkpoints: " + databaseError.getMessage());
            }
        });
    }

    private void startCheckpointDetection() {
        checkpointRunnable = new Runnable() {
            @Override
            public void run() {
                if (currentUserLocation != null) {
                    checkForNearbyCheckpoints();
                }
                checkpointHandler.postDelayed(this, 5000); // Check every 5 seconds
            }
        };
        checkpointHandler.postDelayed(checkpointRunnable, 5000);
    }

    private void stopCheckpointDetection() {
        if (checkpointHandler != null && checkpointRunnable != null) {
            checkpointHandler.removeCallbacks(checkpointRunnable);
        }
    }

    private void checkForNearbyCheckpoints() {
        if (allVipCheckpoints.isEmpty() || currentUserLocation == null) return;

        // Check if user is near any VIP checkpoint
        for (int i = 0; i < allVipCheckpoints.size(); i++) {
            Checkpoint checkpoint = allVipCheckpoints.get(i);
            float[] results = new float[1];
            Location.distanceBetween(
                    currentUserLocation.latitude, currentUserLocation.longitude,
                    checkpoint.latitude, checkpoint.longitude,
                    results
            );

            if (results[0] <= CHECKPOINT_RADIUS) {
                // User is at a VIP checkpoint
                if (currentCheckpoint == null || !currentCheckpoint.id.equals(checkpoint.id)) {
                    setCurrentCheckpoint(checkpoint, i);
                }
                break;
            }
        }

        // Update next VIP checkpoint information
        updateNextCheckpointInfo();
    }

    private void setCurrentCheckpoint(Checkpoint checkpoint, int index) {
        currentCheckpoint = checkpoint;
        currentCheckpointIndex = index;

        // Update UI
        String checkpointText = checkpoint.name + " (#" + checkpoint.number + ")";
        if (checkpoint.isExclusive()) {
            checkpointText += " ⭐"; // Star indicator for exclusive VIP checkpoints
        }
        tvCurrentCheckpoint.setText(checkpointText);

        // Show countdown timer
        countdownLayout.setVisibility(View.VISIBLE);

        // Start timer for this checkpoint (10 minutes to stay at this checkpoint)
        startCheckpointTimer(checkpoint);

        // Update next checkpoint
        updateNextCheckpoint();

        // Update Firebase - mark user as arrived at this checkpoint with timestamp
        if (currentUser != null) {
            long arrivalTime = System.currentTimeMillis();
            databaseReference.child("checkpoints").child(checkpoint.id)
                    .child("arrivingUserIds").child(currentUser.getUid()).setValue(true);
            databaseReference.child("checkpoints").child(checkpoint.id)
                    .child("userArrivalTimes").child(currentUser.getUid()).setValue(arrivalTime);
        }

        // Get people count at current checkpoint (including yourself)
        getPeopleCountAtCheckpoint(currentCheckpoint, CHECKPOINT_RADIUS, tvPeopleAtCurrent, true);
    }

    private void updateNextCheckpoint() {
        if (currentCheckpointIndex >= 0 && currentCheckpointIndex < allVipCheckpoints.size() - 1) {
            nextCheckpoint = allVipCheckpoints.get(currentCheckpointIndex + 1);
            String checkpointText = nextCheckpoint.name + " (#" + nextCheckpoint.number + ")";
            if (nextCheckpoint.isExclusive()) {
                checkpointText += " ⭐"; // Star indicator for exclusive VIP checkpoints
            }
            tvNextCheckpoint.setText(checkpointText);

            // Show fast travel button for VIP users
            btnFastTravelToNext.setVisibility(View.VISIBLE);
        } else if (currentCheckpointIndex == allVipCheckpoints.size() - 1) {
            nextCheckpoint = null;
            tvNextCheckpoint.setText("Last VIP checkpoint reached");
            btnFastTravelToNext.setVisibility(View.GONE);
        } else {
            nextCheckpoint = allVipCheckpoints.get(0); // Default to first checkpoint
            String checkpointText = nextCheckpoint.name + " (#" + nextCheckpoint.number + ")";
            if (nextCheckpoint.isExclusive()) {
                checkpointText += " ⭐";
            }
            tvNextCheckpoint.setText(checkpointText);
            btnFastTravelToNext.setVisibility(View.VISIBLE);
        }

        // Get people count at next checkpoint
        if (nextCheckpoint != null) {
            getPeopleCountAtCheckpoint(nextCheckpoint, NEXT_CHECKPOINT_RADIUS, tvPeopleAtNext, false);
        }
    }

    private void updateNextCheckpointInfo() {
        if (nextCheckpoint == null || currentUserLocation == null) return;

        // Calculate distance to next checkpoint
        float[] results = new float[1];
        Location.distanceBetween(
                currentUserLocation.latitude, currentUserLocation.longitude,
                nextCheckpoint.latitude, nextCheckpoint.longitude,
                results
        );

        // Update distance UI
        tvNextCheckpointDistance.setText("VIP Distance: " + Math.round(results[0]) + "m");

        // Get arrival estimates
        calculateArrivalEstimates();
    }

    private void getPeopleCountAtCheckpoint(Checkpoint checkpoint, double radius, TextView textView, boolean includeCurrentUser) {
        databaseReference.child("user_locations").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                int peopleCount = 0;

                for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                    if (!includeCurrentUser && currentUser != null && userSnapshot.getKey().equals(currentUser.getUid())) {
                        continue;
                    }

                    Double latitude = userSnapshot.child("latitude").getValue(Double.class);
                    Double longitude = userSnapshot.child("longitude").getValue(Double.class);
                    Long timestamp = userSnapshot.child("timestamp").getValue(Long.class);

                    if (latitude != null && longitude != null && timestamp != null) {
                        if (System.currentTimeMillis() - timestamp > 120000) continue;

                        float[] distanceResults = new float[1];
                        Location.distanceBetween(
                                checkpoint.latitude, checkpoint.longitude,
                                latitude, longitude,
                                distanceResults
                        );

                        if (distanceResults[0] <= radius) {
                            peopleCount++;
                        }
                    }
                }

                // If including current user and they're not already counted, add them
                if (includeCurrentUser && currentUser != null && currentUserLocation != null) {
                    float[] distanceResults = new float[1];
                    Location.distanceBetween(
                            checkpoint.latitude, checkpoint.longitude,
                            currentUserLocation.latitude, currentUserLocation.longitude,
                            distanceResults
                    );

                    if (distanceResults[0] <= radius) {
                        peopleCount++;
                    }
                }

                checkpoint.peopleCount = peopleCount;

                // Show capacity information for VIP checkpoints
                String countText = String.valueOf(peopleCount);
                if (checkpoint.capacity > 0) {
                    countText += "/" + checkpoint.capacity;

                    // Change color if near capacity
                    if (peopleCount >= checkpoint.capacity) {
                        textView.setTextColor(Color.parseColor("#FF6B6B"));
                    } else if (peopleCount >= checkpoint.capacity * 0.8) {
                        textView.setTextColor(Color.parseColor("#FFA500"));
                    } else {
                        textView.setTextColor(Color.parseColor("#BBBBBB"));
                    }
                }

                textView.setText(countText);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("PeopleCount", "Error: " + databaseError.getMessage());
            }
        });
    }

    private void calculateArrivalEstimates() {
        if (currentCheckpoint == null) return;

        // Calculate arrival estimate for current checkpoint (people within 1km but outside checkpoint radius)
        calculateArrivalsAtCurrentCheckpoint();

        if (nextCheckpoint != null) {
            // Calculate arrival estimate for next checkpoint
            calculateArrivalsAtNextCheckpoint();
        }
    }

    private void calculateArrivalsAtCurrentCheckpoint() {
        databaseReference.child("user_locations").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                int arrivingCount = 0;
                List<String> arrivalTimes = new ArrayList<>();

                for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                    if (currentUser != null && userSnapshot.getKey().equals(currentUser.getUid())) continue;

                    Double latitude = userSnapshot.child("latitude").getValue(Double.class);
                    Double longitude = userSnapshot.child("longitude").getValue(Double.class);
                    Long timestamp = userSnapshot.child("timestamp").getValue(Long.class);

                    if (latitude != null && longitude != null && timestamp != null) {
                        if (System.currentTimeMillis() - timestamp > 120000) continue;

                        float[] distanceResults = new float[1];
                        Location.distanceBetween(
                                currentCheckpoint.latitude, currentCheckpoint.longitude,
                                latitude, longitude,
                                distanceResults
                        );

                        // People within 1km radius but outside checkpoint radius will arrive
                        if (distanceResults[0] > CHECKPOINT_RADIUS && distanceResults[0] <= ARRIVAL_RADIUS) {
                            arrivingCount++;

                            // Calculate estimated arrival time
                            double travelTime = (distanceResults[0] - CHECKPOINT_RADIUS) / AVERAGE_WALKING_SPEED / 60; // in minutes
                            int roundedTime = (int) Math.ceil(travelTime);
                            arrivalTimes.add(roundedTime + "min");
                        }
                    }
                }

                // Check if current user is also outside checkpoint radius but within 1km
                if (currentUserLocation != null) {
                    float[] distanceResults = new float[1];
                    Location.distanceBetween(
                            currentCheckpoint.latitude, currentCheckpoint.longitude,
                            currentUserLocation.latitude, currentUserLocation.longitude,
                            distanceResults
                    );

                    if (distanceResults[0] > CHECKPOINT_RADIUS && distanceResults[0] <= ARRIVAL_RADIUS) {
                        arrivingCount++;
                        double travelTime = (distanceResults[0] - CHECKPOINT_RADIUS) / AVERAGE_WALKING_SPEED / 60;
                        int roundedTime = (int) Math.ceil(travelTime);
                        arrivalTimes.add(roundedTime + "min");
                    }
                }

                if (arrivingCount > 0) {
                    // Show exact arrival times if available
                    if (!arrivalTimes.isEmpty()) {
                        StringBuilder timeText = new StringBuilder();
                        for (String time : arrivalTimes) {
                            if (timeText.length() > 0) timeText.append(", ");
                            timeText.append(time);
                        }
                        tvArrivalEstimateCurrent.setText("VIPs arriving: " + arrivingCount + " (" + timeText.toString() + ")");
                    } else {
                        tvArrivalEstimateCurrent.setText("VIPs arriving: " + arrivingCount);
                    }
                } else {
                    tvArrivalEstimateCurrent.setText("No VIPs arriving soon");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("ArrivalEstimate", "Error: " + databaseError.getMessage());
            }
        });
    }

    private void calculateArrivalsAtNextCheckpoint() {
        // Calculate distance between current and next checkpoint
        float[] distanceResults = new float[1];
        Location.distanceBetween(
                currentCheckpoint.latitude, currentCheckpoint.longitude,
                nextCheckpoint.latitude, nextCheckpoint.longitude,
                distanceResults
        );

        double distance = distanceResults[0];

        // Get all users at current checkpoint with their arrival times
        databaseReference.child("checkpoints").child(currentCheckpoint.id)
                .child("userArrivalTimes").addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        int estimatedArrivals = 0;
                        List<String> arrivalTimes = new ArrayList<>();
                        long currentTime = System.currentTimeMillis();

                        for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                            Long arrivalTime = userSnapshot.getValue(Long.class);
                            if (arrivalTime != null) {
                                // Calculate how long the user has been at the checkpoint
                                long timeAtCheckpoint = (currentTime - arrivalTime) / 1000; // in seconds

                                // If user has been at checkpoint for more than 10 minutes, they should have left
                                if (timeAtCheckpoint > CHECKPOINT_TIMER_MINUTES * 60) {
                                    continue;
                                }

                                // Calculate remaining time at checkpoint
                                long remainingTime = (CHECKPOINT_TIMER_MINUTES * 60) - timeAtCheckpoint;

                                // Calculate travel time to next checkpoint (distance / speed)
                                double travelTime = distance / AVERAGE_WALKING_SPEED; // in seconds

                                // Total time until arrival at next checkpoint
                                double totalTime = remainingTime + travelTime;

                                // Convert to minutes
                                double totalTimeMinutes = totalTime / 60;

                                // If total time is less than 30 minutes, count this user
                                if (totalTimeMinutes <= ARRIVAL_ESTIMATION_MINUTES) {
                                    estimatedArrivals++;
                                    arrivalTimes.add((int) Math.ceil(totalTimeMinutes) + "min");
                                }
                            }
                        }

                        if (estimatedArrivals > 0) {
                            // Show exact arrival times if available
                            if (!arrivalTimes.isEmpty()) {
                                StringBuilder timeText = new StringBuilder();
                                for (String time : arrivalTimes) {
                                    if (timeText.length() > 0) timeText.append(", ");
                                    timeText.append(time);
                                }
                                tvArrivalEstimateNext.setText("VIPs arriving: " + estimatedArrivals + " (" + timeText.toString() + ")");
                            } else {
                                tvArrivalEstimateNext.setText("VIPs arriving: " + estimatedArrivals);
                            }
                        } else {
                            tvArrivalEstimateNext.setText("No VIPs arriving soon");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e("ArrivalEstimate", "Error: " + databaseError.getMessage());
                    }
                });
    }

    private void startCheckpointTimer(Checkpoint checkpoint) {
        if (currentUser == null) return;

        // Start countdown timer (10 minutes to stay at this checkpoint)
        startTimer(CHECKPOINT_TIMER_MINUTES * 60 * 1000);

        // Add user to arriving list with timestamp
        long arrivalTime = System.currentTimeMillis();
        databaseReference.child("checkpoints").child(checkpoint.id)
                .child("arrivingUserIds").child(currentUser.getUid()).setValue(true);
        databaseReference.child("checkpoints").child(checkpoint.id)
                .child("userArrivalTimes").child(currentUser.getUid()).setValue(arrivalTime);

        // Remove user after 10 minutes
        new Handler().postDelayed(() -> {
            databaseReference.child("checkpoints").child(checkpoint.id)
                    .child("arrivingUserIds").child(currentUser.getUid()).removeValue();
            databaseReference.child("checkpoints").child(checkpoint.id)
                    .child("userArrivalTimes").child(currentUser.getUid()).removeValue();
        }, CHECKPOINT_TIMER_MINUTES * 60 * 1000);
    }

    private void startTimer(long duration) {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        timeLeftInMillis = duration;

        countDownTimer = new CountDownTimer(timeLeftInMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeLeftInMillis = millisUntilFinished;
                updateCountdownText();
            }

            @Override
            public void onFinish() {
                timerRunning = false;
                tvCountdownTimer.setText("00:00");
                countdownLayout.setVisibility(View.GONE);

                // Timer finished - user should leave the checkpoint now
                Toast.makeText(getContext(), "Time to move to next VIP checkpoint!", Toast.LENGTH_LONG).show();
            }
        }.start();

        timerRunning = true;
    }

    private void updateCountdownText() {
        int minutes = (int) (timeLeftInMillis / 1000) / 60;
        int seconds = (int) (timeLeftInMillis / 1000) % 60;

        String timeLeftFormatted = String.format("%02d:%02d", minutes, seconds);
        tvCountdownTimer.setText(timeLeftFormatted);

        // Update the timer description
        tvArrivalEstimateCurrent.setText("VIP access expires in: " + minutes + "min " + seconds + "sec");
    }

    private void pauseTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            timerRunning = false;
        }
    }

    private void updateUserLocation(Location location) {
        if (currentUser != null) {
            currentUserLocation = new LatLng(location.getLatitude(), location.getLongitude());

            String userId = currentUser.getUid();
            DatabaseReference userLocationRef = databaseReference.child("user_locations").child(userId);

            Map<String, Object> locationData = new HashMap<>();
            locationData.put("latitude", location.getLatitude());
            locationData.put("longitude", location.getLongitude());
            locationData.put("timestamp", System.currentTimeMillis());
            locationData.put("userId", userId);
            locationData.put("userEmail", currentUser.getEmail());
            locationData.put("isVip", true); // Mark as VIP user

            userLocationRef.setValue(locationData);
            updateRadiusCircle();
        }
    }

    private void updateRadiusCircle() {
        if (gMap != null && currentUserLocation != null) {
            if (radiusCircle != null) {
                radiusCircle.remove();
            }

            radiusCircle = gMap.addCircle(new CircleOptions()
                    .center(currentUserLocation)
                    .radius(RADIUS_METERS)
                    .strokeColor(Color.argb(70, 255, 215, 0)) // Gold color for VIP
                    .fillColor(Color.argb(30, 255, 215, 0))
                    .strokeWidth(2));
        }
    }

    private void updatePeopleCountInRadius() {
        if (currentUserLocation == null || currentUser == null) return;

        databaseReference.child("user_locations").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                int peopleCount = 0;
                int vipCount = 0;
                String currentUserId = currentUser.getUid();

                for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                    if (userSnapshot.getKey().equals(currentUserId)) continue;

                    Double latitude = userSnapshot.child("latitude").getValue(Double.class);
                    Double longitude = userSnapshot.child("longitude").getValue(Double.class);
                    Long timestamp = userSnapshot.child("timestamp").getValue(Long.class);
                    Boolean isVip = userSnapshot.child("isVip").getValue(Boolean.class);

                    if (latitude != null && longitude != null && timestamp != null) {
                        if (System.currentTimeMillis() - timestamp > 120000) continue;

                        LatLng userLocation = new LatLng(latitude, longitude);
                        float[] results = new float[1];
                        Location.distanceBetween(
                                currentUserLocation.latitude, currentUserLocation.longitude,
                                userLocation.latitude, userLocation.longitude,
                                results
                        );

                        if (results[0] <= RADIUS_METERS) {
                            peopleCount++;
                            if (isVip != null && isVip) {
                                vipCount++;
                            }
                        }
                    }
                }

                updatePeopleCountUI(peopleCount, vipCount);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("PeopleCount", "Error: " + databaseError.getMessage());
            }
        });
    }

    private void updatePeopleCountUI(int peopleCount, int vipCount) {
        String peopleText = "VIP Radius: " + peopleCount + " people (" + vipCount + " VIPs)";
        tvPeopleCount.setText(peopleText);

        if (peopleCount == 0) {
            tvSafetyStatus.setText("VIP Secure");
            tvSafetyStatus.setTextColor(Color.parseColor("#4CD964"));
        } else if (peopleCount <= 5) {
            tvSafetyStatus.setText("VIP Normal");
            tvSafetyStatus.setTextColor(Color.parseColor("#F59E0B"));
        } else {
            tvSafetyStatus.setText("VIP Crowded");
            tvSafetyStatus.setTextColor(Color.parseColor("#FF6B6B"));
        }
    }

    private void setupToggle(ImageView icon, String type, float colorHue) {
        icon.setOnClickListener(v -> {
            boolean selected = !icon.isSelected();
            icon.setSelected(selected);
            iconState.put(type, selected);

            if (selected) {
                icon.setColorFilter(Color.parseColor("#FFD700")); // Gold color for VIP
                showVipMarkers(type, colorHue);
            } else {
                icon.setColorFilter(null);
                hideMarkers(type);
            }
        });
    }

    private void showVipMarkers(String type, float colorHue) {
        if (!markerMap.get(type).isEmpty()) {
            for (Marker marker : markerMap.get(type)) {
                marker.setVisible(true);
            }
            return;
        }

        final String databasePath;
        final boolean isFacility;

        switch (type) {
            case "checkpoint":
            case "ghat":
            case "lounge":
                databasePath = type + "s";
                isFacility = false;
                break;
            case "toilet":
            case "changing-room":
            case "first-aid":
                databasePath = "facilities";
                isFacility = true;
                break;
            default:
                databasePath = type;
                isFacility = false;
        }

        databaseReference.child(databasePath).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    try {
                        if (isFacility) {
                            String facilityType = snapshot.child("type").getValue(String.class);
                            if (facilityType == null || !facilityType.equals(type)) continue;

                            // For VIP, only show VIP facilities
                            Boolean isVipFacility = snapshot.child("isVip").getValue(Boolean.class);
                            if (isVipFacility == null || !isVipFacility) continue;
                        } else {
                            // For checkpoints, only show VIP checkpoints
                            if (type.equals("checkpoint") || type.equals("lounge")) {
                                String checkpointType = snapshot.child("type").getValue(String.class);
                                if (!"vip".equals(checkpointType)) continue;
                            }
                        }

                        double latitude = 0.0;
                        double longitude = 0.0;
                        String title = type;

                        if (type.equals("ghat")) {
                            if (snapshot.child("points").exists() && snapshot.child("points").hasChildren()) {
                                DataSnapshot firstPoint = snapshot.child("points").getChildren().iterator().next();
                                latitude = firstPoint.child("lat").getValue(Double.class);
                                longitude = firstPoint.child("lng").getValue(Double.class);
                            }
                        } else {
                            latitude = snapshot.child("latitude").getValue(Double.class);
                            longitude = snapshot.child("longitude").getValue(Double.class);
                        }

                        if (snapshot.child("name").exists()) {
                            title = snapshot.child("name").getValue(String.class);
                        }

                        LatLng location = new LatLng(latitude, longitude);
                        BitmapDescriptor icon = BitmapDescriptorFactory.defaultMarker(colorHue);

                        Marker marker = gMap.addMarker(new MarkerOptions()
                                .position(location)
                                .title(title)
                                .icon(icon)
                                .visible(true));

                        marker.setTag(type);
                        markerMap.get(type).add(marker);
                    } catch (Exception e) {
                        Log.e("MarkerError", "Error creating marker: " + e.getMessage());
                    }
                }

                if (markerMap.get(type).isEmpty()) {
                    Toast.makeText(getContext(), "No VIP " + type + " facilities found", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(getContext(), "Failed to load VIP " + type + " data", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void hideMarkers(String type) {
        for (Marker marker : markerMap.get(type)) {
            marker.setVisible(false);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        gMap = googleMap;

        if (gMap == null) {
            Toast.makeText(getContext(), "Map failed to load. Please check your API key.", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            gMap.getUiSettings().setAllGesturesEnabled(true);
            gMap.getUiSettings().setZoomControlsEnabled(true);
            gMap.setOnMarkerClickListener(this);
            gMap.setOnMapClickListener(this);

            gMap.setOnMapLoadedCallback(() -> {
                Toast.makeText(getContext(), "VIP Map loaded successfully", Toast.LENGTH_SHORT).show();
                enableMyLocation();

                // Set custom map style for VIP
                setVipMapStyle();
            });

            // Set default location (Delhi)
            gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(28.6139, 77.2090), 10));

        } catch (Exception e) {
            Log.e("MapError", "Error setting up map: " + e.getMessage());
            Toast.makeText(getContext(), "Error setting up VIP map", Toast.LENGTH_SHORT).show();
        }
    }

    private void setVipMapStyle() {
        try {
            // Custom style for VIP map (dark theme with gold highlights)
            boolean success = gMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(
                    getContext(), R.raw.vip_map_style));

            if (!success) {
                Log.e("MapStyle", "Style parsing failed.");
            }
        } catch (Exception e) {
            Log.e("MapStyle", "Error setting VIP map style: " + e.getMessage());
        }
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        infoBar.setVisibility(View.VISIBLE);
        lastClickedMarker = marker;
        destinationLatLng = marker.getPosition();

        String type = (String) marker.getTag();
        tvInfoName.setText(marker.getTitle());
        tvInfoType.setText(type != null ? "VIP " + type.replace("-", " ") : "VIP Location");

        fetchVipMarkerDetails(marker.getTitle(), type);
        return false;
    }

    @Override
    public void onMapClick(LatLng latLng) {
        hideInfoBar();
    }

    private void fetchVipMarkerDetails(String title, String type) {
        if (type == null) {
            tvInfoDescription.setText("VIP location details");
            tvInfoTime.setText("VIP Hours: Not specified");
            return;
        }

        String databasePath = ("checkpoint".equals(type) || "ghat".equals(type) || "lounge".equals(type)) ? type + "s" : "facilities";

        databaseReference.child(databasePath).orderByChild("name").equalTo(title)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists()) {
                            for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                String description = snapshot.child("description").getValue(String.class);
                                String time = snapshot.child("time").getValue(String.class);
                                Boolean isExclusive = snapshot.child("isExclusive").getValue(Boolean.class);
                                Integer capacity = snapshot.child("capacity").getValue(Integer.class);

                                // Only show available information
                                if (description != null && !description.isEmpty()) {
                                    tvInfoDescription.setText(description);
                                } else {
                                    tvInfoDescription.setText("VIP exclusive access");
                                }

                                if (time != null && !time.isEmpty()) {
                                    tvInfoTime.setText("VIP Hours: " + time);
                                } else {
                                    tvInfoTime.setText("VIP Hours: Not specified");
                                }

                                // Show exclusive badge if applicable
                                if (isExclusive != null && isExclusive) {
                                    tvInfoType.setText(tvInfoType.getText() + " ⭐");
                                }

                                // Show reserve button for reservable locations
                                if (capacity != null && capacity > 0) {
                                    btnReserve.setVisibility(View.VISIBLE);
                                } else {
                                    btnReserve.setVisibility(View.GONE);
                                }

                                // Hide fields that don't have data to save space
                                if ((description == null || description.isEmpty()) &&
                                        (time == null || time.isEmpty())) {
                                    tvInfoDescription.setVisibility(View.GONE);
                                    tvInfoTime.setVisibility(View.GONE);
                                } else {
                                    tvInfoDescription.setVisibility(View.VISIBLE);
                                    tvInfoTime.setVisibility(View.VISIBLE);
                                }
                            }
                        } else {
                            // For markers without specific data, show minimal information
                            tvInfoDescription.setText("VIP location details");
                            tvInfoTime.setText("VIP Hours: Not specified");
                            btnReserve.setVisibility(View.GONE);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        tvInfoDescription.setText("VIP location information");
                        tvInfoTime.setText("VIP Hours: Not specified");
                        btnReserve.setVisibility(View.GONE);
                    }
                });
    }

    private void hideInfoBar() {
        infoBar.setVisibility(View.GONE);
    }

    private void getRouteToDestination(LatLng origin, LatLng destination) {
        if (currentRoute != null) {
            currentRoute.remove();
            currentRoute = null;
        }

        Toast.makeText(getContext(), "Calculating VIP route...", Toast.LENGTH_SHORT).show();

        // Use Directions API with proper Android key
        String url = getDirectionsUrl(origin, destination);
        new DirectionsTask().execute(url);
    }

    private String getDirectionsUrl(LatLng origin, LatLng destination) {
        return "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=" + origin.latitude + "," + origin.longitude +
                "&destination=" + destination.latitude + "," + destination.longitude +
                "&mode=driving" +
                "&key=" + DIRECTIONS_API_KEY;
    }

    private class DirectionsTask extends android.os.AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {
            String data = "";
            try {
                data = downloadUrl(urls[0]);
            } catch (Exception e) {
                Log.e("DirectionsTask", "Error: " + e.toString());
            }
            return data;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            new ParserTask().execute(result);
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
            StringBuffer sb = new StringBuffer();
            String line = "";
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            data = sb.toString();
            br.close();
        } catch (Exception e) {
            Log.e("downloadUrl", "Error: " + e.toString());
        } finally {
            if (iStream != null) iStream.close();
            if (urlConnection != null) urlConnection.disconnect();
        }
        return data;
    }

    private class ParserTask extends android.os.AsyncTask<String, Void, List<LatLng>> {
        @Override
        protected List<LatLng> doInBackground(String... jsonData) {
            JSONObject jObject;
            List<LatLng> path = new ArrayList<>();
            try {
                jObject = new JSONObject(jsonData[0]);
                // Check if response is OK
                if (!jObject.getString("status").equals("OK")) {
                    Log.e("ParserTask", "Directions API returned: " + jObject.getString("status"));
                    return path;
                }

                JSONArray routes = jObject.getJSONArray("routes");
                if (routes.length() > 0) {
                    JSONObject route = routes.getJSONObject(0);
                    JSONObject overviewPolyline = route.getJSONObject("overview_polyline");
                    String encodedPath = overviewPolyline.getString("points");

                    // Use PolyUtil to decode the polyline
                    path = PolyUtil.decode(encodedPath);
                }
            } catch (JSONException e) {
                Log.e("ParserTask", "JSON parsing error: " + e.getMessage());
            }
            return path;
        }

        @Override
        protected void onPostExecute(List<LatLng> result) {
            if (result != null && !result.isEmpty()) {
                // Draw the polyline on the map with VIP styling
                PolylineOptions options = new PolylineOptions()
                        .addAll(result)
                        .width(12)
                        .color(Color.parseColor("#FFD700")) // Gold color for VIP
                        .geodesic(true);

                currentRoute = gMap.addPolyline(options);

                // Zoom to show the entire route
                LatLngBounds.Builder builder = new LatLngBounds.Builder();
                for (LatLng point : result) {
                    builder.include(point);
                }
                try {
                    gMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100));
                } catch (Exception e) {
                    gMap.animateCamera(CameraUpdateFactory.newLatLngZoom(destinationLatLng, 15));
                }

                Toast.makeText(getContext(), "VIP route displayed successfully", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "No route found. Check API key and internet.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission();
            return;
        }

        try {
            gMap.setMyLocationEnabled(true);
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    LatLng userLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                    gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15));
                }
            });
        } catch (SecurityException e) {
            Log.e("Location", "SecurityException: " + e.getMessage());
        }
    }

    private void requestLocationPermission() {
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                LOCATION_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation();
            startLocationUpdates();
            startPeopleCountUpdates();
            loadVipCheckpoints();
        }
    }

    // VIP Exclusive Methods
    private void showFastTravelOptions() {
        if (allVipCheckpoints.isEmpty()) {
            Toast.makeText(getContext(), "No VIP checkpoints available", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create a dialog with list of VIP checkpoints for fast travel
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(requireContext());
        builder.setTitle("VIP Fast Travel");

        String[] checkpointNames = new String[allVipCheckpoints.size()];
        for (int i = 0; i < allVipCheckpoints.size(); i++) {
            checkpointNames[i] = allVipCheckpoints.get(i).name +
                    (allVipCheckpoints.get(i).isExclusive() ? " ⭐" : "");
        }

        builder.setItems(checkpointNames, (dialog, which) -> {
            fastTravelToCheckpoint(allVipCheckpoints.get(which));
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void fastTravelToCheckpoint(Checkpoint checkpoint) {
        // Simulate fast travel by moving camera to checkpoint
        LatLng checkpointLocation = new LatLng(checkpoint.latitude, checkpoint.longitude);
        gMap.animateCamera(CameraUpdateFactory.newLatLngZoom(checkpointLocation, 15));

        Toast.makeText(getContext(), "Fast traveling to " + checkpoint.name, Toast.LENGTH_SHORT).show();

        // Show info about the checkpoint
        tvCurrentCheckpoint.setText(checkpoint.name + " (#" + checkpoint.number + ")");
        getPeopleCountAtCheckpoint(checkpoint, CHECKPOINT_RADIUS, tvPeopleAtCurrent, true);
    }

    private void requestConciergeService() {
        // Implement VIP concierge service request
        Toast.makeText(getContext(), "VIP Concierge service requested", Toast.LENGTH_SHORT).show();

        // In a real implementation, this would send a notification to staff
        if (currentUser != null) {
            DatabaseReference conciergeRef = databaseReference.child("concierge_requests").push();
            Map<String, Object> request = new HashMap<>();
            request.put("userId", currentUser.getUid());
            request.put("userEmail", currentUser.getEmail());
            request.put("timestamp", System.currentTimeMillis());
            request.put("location", currentUserLocation != null ?
                    currentUserLocation.latitude + "," + currentUserLocation.longitude : "Unknown");
            request.put("status", "pending");

            conciergeRef.setValue(request);
        }
    }

    private void reserveVipLocation(String locationName) {
        // Implement VIP reservation system
        Toast.makeText(getContext(), "Reserving VIP location: " + locationName, Toast.LENGTH_SHORT).show();

        if (currentUser != null) {
            DatabaseReference reservationRef = databaseReference.child("reservations").push();
            Map<String, Object> reservation = new HashMap<>();
            reservation.put("userId", currentUser.getUid());
            reservation.put("userEmail", currentUser.getEmail());
            reservation.put("location", locationName);
            reservation.put("timestamp", System.currentTimeMillis());
            reservation.put("status", "confirmed");

            reservationRef.setValue(reservation);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // Clean up markers
        for (List<Marker> markers : markerMap.values()) {
            for (Marker marker : markers) {
                marker.remove();
            }
            markers.clear();
        }

        // Clean up route and circle
        if (currentRoute != null) currentRoute.remove();
        if (radiusCircle != null) radiusCircle.remove();

        // Clean up handlers
        stopLocationUpdates();
        stopPeopleCountUpdates();
        stopCheckpointDetection();
        pauseTimer();
    }
}