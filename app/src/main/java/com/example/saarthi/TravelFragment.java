package com.example.saarthi;

import android.Manifest;
import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class TravelFragment extends Fragment implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    private View routeInfoCard;
    private TextView tvRouteStatus, tvNextTurn, tvCongestionInfo;
    private TextView tvInfoTitle, tvInfoContent;
    private View parkingInfo, transportInfo;
    private RecyclerView transportRecycler, parkingRecycler, stayRecycler;
    private Spinner userTypeSpinner;

    // Firebase variables
    private FirebaseAuth mAuth;
    private DatabaseReference databaseReference;
    private String userId;

    // Date planning variables
    private TextView tvDatePrompt;
    private MaterialButton btnSelectDate;
    private TextView tvDateStats;
    private Calendar selectedDate;
    private SimpleDateFormat dateFormatter;
    private boolean isDateSelectionVisible = false;

    // Sample data for demonstration
    private List<TransportOption> transportOptions;
    private List<ParkingOption> parkingOptions;
    private List<StayOption> stayOptions;

    // Kumbh Mela Ujjain coordinates
    private static final LatLng UJJAIN = new LatLng(23.1765, 75.7885);
    private static final LatLng ENTRY_GATE_1 = new LatLng(23.1780, 75.7900);
    private static final LatLng ENTRY_GATE_2 = new LatLng(23.1750, 75.7870);

    private Polyline currentRoute;
    private ProgressDialog progressDialog;

    // User type
    private String selectedUserType = "Normal";

    // Handler for live updates
    private Handler liveUpdateHandler = new Handler();
    private Runnable liveUpdateRunnable;

    public TravelFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_travel, container, false);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        // Use consistent database initialization
        FirebaseDatabase database = FirebaseDatabase.getInstance("https://saarthi-1d760-default-rtdb.firebaseio.com/");
        databaseReference = database.getReference();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            userId = currentUser.getUid();
        }

        // Initialize progress dialog
        progressDialog = new ProgressDialog(getContext());
        progressDialog.setMessage("Saving date...");
        progressDialog.setCancelable(false);

        // Initialize date formatter
        dateFormatter = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        selectedDate = Calendar.getInstance();

        initializeViews(view);
        setupMap();
        setupTransportData();
        setupParkingData();
        setupStayData();
        setupDatePlanning();

        // Start live updates
        startLiveUpdates();

        return view;
    }

    private void initializeViews(View view) {
        routeInfoCard = view.findViewById(R.id.route_info_card);
        tvRouteStatus = view.findViewById(R.id.tv_route_status);
        tvNextTurn = view.findViewById(R.id.tv_next_turn);
        tvCongestionInfo = view.findViewById(R.id.tv_congestion_info);

        tvInfoTitle = view.findViewById(R.id.tv_info_title);
        tvInfoContent = view.findViewById(R.id.tv_info_content);

        parkingInfo = view.findViewById(R.id.parking_info);
        transportInfo = view.findViewById(R.id.transport_info);
        transportRecycler = view.findViewById(R.id.transport_recycler);
        parkingRecycler = view.findViewById(R.id.parking_recycler);
        stayRecycler = view.findViewById(R.id.stay_recycler);

        // User type spinner
        userTypeSpinner = view.findViewById(R.id.user_type_spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getContext(),
                R.array.user_types, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        userTypeSpinner.setAdapter(adapter);
        userTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedUserType = parent.getItemAtPosition(position).toString();
                // Refresh data based on user type
                refreshDataForUserType();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedUserType = "Normal";
            }
        });

        // Date planning views
        tvDatePrompt = view.findViewById(R.id.tv_date_prompt);
        btnSelectDate = view.findViewById(R.id.btn_select_date);
        tvDateStats = view.findViewById(R.id.tv_date_stats);

        MaterialButton btnBestTime = view.findViewById(R.id.btn_best_time);
        MaterialButton btnParking = view.findViewById(R.id.btn_parking);
        MaterialButton btnStays = view.findViewById(R.id.btn_stays);
        MaterialButton btnTransport = view.findViewById(R.id.btn_transport);
        MaterialButton btnVip = view.findViewById(R.id.btn_vip);

        btnBestTime.setOnClickListener(v -> showBestTimeInfo());
        btnParking.setOnClickListener(v -> showParkingInfo());
        btnStays.setOnClickListener(v -> showStaysInfo());
        btnTransport.setOnClickListener(v -> showTransportInfo());
        btnVip.setOnClickListener(v -> showVipInfo());

        view.findViewById(R.id.fab_directions).setOnClickListener(v -> getDirectionsToEntryGate());
    }

    private void refreshDataForUserType() {
        // Refresh transport data
        setupTransportData();

        // Refresh parking data
        setupParkingData();

        // Refresh stay data
        setupStayData();

        // Refresh map markers
        if (mMap != null) {
            mMap.clear();
            setupMapFeatures();
        }
    }

    private void setupDatePlanning() {
        // Set click listener for the prompt text
        tvDatePrompt.setOnClickListener(v -> {
            if (!isDateSelectionVisible) {
                // Show date selection button when prompt is clicked
                btnSelectDate.setVisibility(View.VISIBLE);
                isDateSelectionVisible = true;
            }
            // Show date picker directly when prompt is clicked
            showDatePickerDialog();
        });

        btnSelectDate.setOnClickListener(v -> showDatePickerDialog());

        // Load date statistics
        loadDateStats();
    }

    private void showDatePickerDialog() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    selectedDate.set(year, month, dayOfMonth);
                    String formattedDate = dateFormatter.format(selectedDate.getTime());
                    saveUserVisitDate(formattedDate);
                },
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH)
        );

        // Allow selection of any date from 2024 to 2028
        Calendar minDate = Calendar.getInstance();
        minDate.set(2024, Calendar.JANUARY, 1);
        Calendar maxDate = Calendar.getInstance();
        maxDate.set(2028, Calendar.DECEMBER, 31);

        datePickerDialog.getDatePicker().setMinDate(minDate.getTimeInMillis());
        datePickerDialog.getDatePicker().setMaxDate(maxDate.getTimeInMillis());

        datePickerDialog.show();
    }

    private boolean isUserLoggedIn() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(getContext(), "Please log in to save your visit date", Toast.LENGTH_SHORT).show();
            return false;
        }
        userId = currentUser.getUid();
        return true;
    }

    private void saveUserVisitDate(String date) {
        if (!isUserLoggedIn()) {
            return;
        }

        try {
            // Format date for consistent storage (YYYY-MM-DD)
            SimpleDateFormat inputFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date parsedDate = inputFormat.parse(date);
            String formattedDate = outputFormat.format(parsedDate);

            // Create visit data
            Map<String, Object> visitData = new HashMap<>();
            visitData.put("selectedDate", formattedDate);
            visitData.put("timestamp", System.currentTimeMillis());
            visitData.put("displayDate", date);
            visitData.put("userId", userId);

            // Show progress dialog
            progressDialog.show();

            // Save to Firebase - using a push() to create unique entry
            DatabaseReference userVisitsRef = databaseReference.child("user_visits").child(userId);
            String visitId = userVisitsRef.push().getKey();

            if (visitId != null) {
                userVisitsRef.child(visitId).setValue(visitData)
                        .addOnCompleteListener(task -> {
                            progressDialog.dismiss();

                            if (task.isSuccessful()) {
                                Toast.makeText(getContext(), "Visit date saved: " + date, Toast.LENGTH_SHORT).show();
                                Log.d("RealtimeDB", "Visit date saved for user: " + userId);

                                // Update the date count statistics
                                updateDateCount(formattedDate);
                            } else {
                                String errorMessage = task.getException() != null ?
                                        task.getException().getMessage() : "Unknown error";
                                Toast.makeText(getContext(), "Failed to save date: " + errorMessage,
                                        Toast.LENGTH_SHORT).show();
                                Log.e("RealtimeDB", "Error saving visit date: " + errorMessage);
                            }
                        });
            }
        } catch (Exception e) {
            progressDialog.dismiss();
            Toast.makeText(getContext(), "Error processing date format", Toast.LENGTH_SHORT).show();
            Log.e("DateFormat", "Error formatting date: " + e.getMessage());
        }
    }

    private void updateDateCount(String formattedDate) {
        DatabaseReference dateRef = databaseReference.child("date_counts").child(formattedDate);
        dateRef.setValue(ServerValue.increment(1))
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        loadDateStats(); // Refresh statistics
                    } else {
                        Toast.makeText(getContext(), "Failed to update date count",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadDateStats() {
        databaseReference.child("date_counts").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                // Count visitors per date
                Map<String, Integer> dateCounts = new HashMap<>();

                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    String date = snapshot.getKey();
                    Integer count = snapshot.getValue(Integer.class);
                    if (date != null && count != null) {
                        // Format date for display (DD-MM-YYYY)
                        try {
                            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                            SimpleDateFormat outputFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
                            Date parsedDate = inputFormat.parse(date);
                            String displayDate = outputFormat.format(parsedDate);
                            dateCounts.put(displayDate, count);
                        } catch (Exception e) {
                            dateCounts.put(date, count); // Fallback to original format
                        }
                    }
                }

                // Display statistics
                displayDateStatistics(dateCounts);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(getContext(), "Failed to load date statistics: " + databaseError.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void displayDateStatistics(Map<String, Integer> dateCounts) {
        if (dateCounts.isEmpty()) {
            tvDateStats.setText("No visitors have selected dates yet. Be the first to plan your visit!");
            return;
        }

        StringBuilder statsText = new StringBuilder("Visitor Planning Statistics:\n\n");

        for (Map.Entry<String, Integer> entry : dateCounts.entrySet()) {
            statsText.append(entry.getKey())
                    .append(": ")
                    .append(entry.getValue())
                    .append(" visitors\n");
        }

        // Find the least crowded date
        String leastCrowdedDate = "";
        int minVisitors = Integer.MAX_VALUE;

        for (Map.Entry<String, Integer> entry : dateCounts.entrySet()) {
            if (entry.getValue() < minVisitors) {
                minVisitors = entry.getValue();
                leastCrowdedDate = entry.getKey();
            }
        }

        statsText.append("\nLeast crowded date: ")
                .append(leastCrowdedDate)
                .append(" (")
                .append(minVisitors)
                .append(" visitors)");

        tvDateStats.setText(statsText.toString());
    }

    private void setupMap() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    private void setupTransportData() {
        transportOptions = new ArrayList<>();

        // Different transport options based on user type
        if ("VIP".equals(selectedUserType)) {
            transportOptions.add(new TransportOption("VIP Shuttle", "5 mins", "1 km away", "Live Tracking Available", "23.1770,75.7890", "5 mins"));
            transportOptions.add(new TransportOption("Premium Taxi", "On demand", "Contact concierge", "Luxury vehicles available", "23.1780,75.7900", "On demand"));
            transportOptions.add(new TransportOption("Helicopter Service", "By appointment", "Ujjain Airport", "For special guests", "23.1790,75.7910", "By appointment"));
        } else if ("Disabled".equals(selectedUserType)) {
            transportOptions.add(new TransportOption("Accessible Shuttle", "10 mins", "0.8 km away", "Wheelchair accessible", "23.1770,75.7890", "10 mins"));
            transportOptions.add(new TransportOption("Special Transport", "15 mins", "1.2 km away", "Assistance available", "23.1780,75.7900", "15 mins"));
            transportOptions.add(new TransportOption("Disabled Parking Shuttle", "7 mins", "0.5 km away", "Direct to entry gate", "23.1790,75.7910", "7 mins"));
        } else {
            transportOptions.add(new TransportOption("Shuttle Bus", "8 mins", "1.5 km away", "Live Tracking Available", "23.1770,75.7890", "8 mins"));
            transportOptions.add(new TransportOption("Auto Rickshaw", "2 mins", "0.5 km away", "₹50-70 to entry gate", "23.1780,75.7900", "2 mins"));
            transportOptions.add(new TransportOption("Cycle Rickshaw", "3 mins", "0.3 km away", "Eco-friendly option", "23.1790,75.7910", "3 mins"));
            transportOptions.add(new TransportOption("Local Bus", "12 mins", "2 km away", "₹20 per person", "23.1800,75.7920", "12 mins"));
        }

        TransportAdapter adapter = new TransportAdapter(transportOptions);
        transportRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        transportRecycler.setAdapter(adapter);
    }

    private void setupParkingData() {
        parkingOptions = new ArrayList<>();

        // At least 7 parking places with different availability based on user type
        if ("VIP".equals(selectedUserType)) {
            parkingOptions.add(new ParkingOption("VIP Parking 1", "23.1790,75.7910", "100/100", "Reserved", "Dedicated VIP area with security"));
            parkingOptions.add(new ParkingOption("VIP Parking 2", "23.1800,75.7920", "80/100", "Available", "Covered parking with valet"));
            parkingOptions.add(new ParkingOption("Executive Parking", "23.1810,75.7930", "90/100", "Limited", "Near main entrance"));
        } else if ("Disabled".equals(selectedUserType)) {
            parkingOptions.add(new ParkingOption("Disabled Parking 1", "23.1790,75.7910", "20/20", "Available", "Wheelchair accessible near gate"));
            parkingOptions.add(new ParkingOption("Disabled Parking 2", "23.1800,75.7920", "15/20", "Available", "Covered accessible parking"));
            parkingOptions.add(new ParkingOption("Special Needs Parking", "23.1810,75.7930", "18/20", "Available", "With assistance services"));
        } else {
            parkingOptions.add(new ParkingOption("Main Parking Lot", "23.1790,75.7910", "650/800", "Available", "Closest to entrance"));
            parkingOptions.add(new ParkingOption("North Parking", "23.1800,75.7920", "420/500", "Available", "Shaded area"));
            parkingOptions.add(new ParkingOption("South Parking", "23.1810,75.7930", "300/400", "Available", "Economy parking"));
            parkingOptions.add(new ParkingOption("East Parking", "23.1820,75.7940", "200/300", "Available", "Free shuttle service"));
            parkingOptions.add(new ParkingOption("West Parking", "23.1830,75.7950", "150/200", "Limited", "Budget option"));
            parkingOptions.add(new ParkingOption("Satellite Parking", "23.1840,75.7960", "800/1000", "Available", "With free shuttle"));
            parkingOptions.add(new ParkingOption("Event Parking", "23.1850,75.7970", "350/500", "Available", "Premium location"));
        }

        ParkingAdapter adapter = new ParkingAdapter(parkingOptions);
        parkingRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        parkingRecycler.setAdapter(adapter);
    }

    private void setupStayData() {
        stayOptions = new ArrayList<>();

        // Government provided stays with capacity information
        if ("VIP".equals(selectedUserType)) {
            stayOptions.add(new StayOption("VIP Guest House", "23.1860,75.7980", "50/50", "Available", "Luxury accommodation for dignitaries"));
            stayOptions.add(new StayOption("Government Retreat", "23.1870,75.7990", "30/30", "Available", "Exclusive government facility"));
        } else if ("Disabled".equals(selectedUserType)) {
            stayOptions.add(new StayOption("Accessible Shelter", "23.1860,75.7980", "80/100", "Available", "Wheelchair accessible with medical support"));
            stayOptions.add(new StayOption("Special Needs Camp", "23.1870,75.7990", "60/80", "Available", "Dedicated staff for assistance"));
        } else {
            stayOptions.add(new StayOption("Government Shelter 1", "23.1860,75.7980", "350/500", "Available", "Basic amenities, free of cost"));
            stayOptions.add(new StayOption("Dharmasala", "23.1870,75.7990", "200/300", "Available", "Traditional accommodation"));
            stayOptions.add(new StayOption("Community Hall", "23.1880,75.8000", "150/200", "Limited", "Mat flooring, shared facilities"));
            stayOptions.add(new StayOption("Tent City", "23.1890,75.8010", "800/1000", "Available", "Large capacity with basic facilities"));
            stayOptions.add(new StayOption("School Campus", "23.1900,75.8020", "300/400", "Available", "Converted for festival period"));
            stayOptions.add(new StayOption("Public Shelter", "23.1910,75.8030", "400/500", "Available", "Government-run facility"));
        }

        StayAdapter adapter = new StayAdapter(stayOptions);
        stayRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        stayRecycler.setAdapter(adapter);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Check for location permissions
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        enableUserLocation();
        setupMapFeatures();
    }

    private void enableUserLocation() {
        if (mMap != null && ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);

            // Get current location and center map
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(requireActivity(), new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            if (location != null) {
                                LatLng currentLatLng = new LatLng(
                                        location.getLatitude(),
                                        location.getLongitude());
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15));
                            }
                        }
                    });
        }
    }

    private void setupMapFeatures() {
        // Add markers for important locations
        mMap.addMarker(new MarkerOptions()
                .position(ENTRY_GATE_1)
                .title("Main Entry Gate")
                .snippet("Primary entrance to Kumbh Mela")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

        mMap.addMarker(new MarkerOptions()
                .position(ENTRY_GATE_2)
                .title("Secondary Entry Gate")
                .snippet("Alternative entrance")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

        // Add other important markers based on user type
        addParkingMarkers();
        addStayMarkers();
        addTransportMarkers();

        // Set map click listeners
        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                // Handle marker clicks
                return false;
            }
        });
    }

    private void addParkingMarkers() {
        for (ParkingOption parking : parkingOptions) {
            String[] coords = parking.getLocation().split(",");
            if (coords.length == 2) {
                try {
                    LatLng position = new LatLng(Double.parseDouble(coords[0]), Double.parseDouble(coords[1]));

                    // Different marker color based on availability
                    float hue = BitmapDescriptorFactory.HUE_BLUE;
                    if (parking.getAvailability().equals("Limited")) {
                        hue = BitmapDescriptorFactory.HUE_ORANGE;
                    } else if (parking.getAvailability().equals("Full")) {
                        hue = BitmapDescriptorFactory.HUE_RED;
                    }

                    mMap.addMarker(new MarkerOptions()
                            .position(position)
                            .title(parking.getName())
                            .snippet("Capacity: " + parking.getCapacity() + ", Status: " + parking.getAvailability())
                            .icon(BitmapDescriptorFactory.defaultMarker(hue)));
                } catch (NumberFormatException e) {
                    Log.e("MapMarker", "Invalid coordinates for parking: " + parking.getLocation());
                }
            }
        }
    }

    private void addStayMarkers() {
        for (StayOption stay : stayOptions) {
            String[] coords = stay.getLocation().split(",");
            if (coords.length == 2) {
                try {
                    LatLng position = new LatLng(Double.parseDouble(coords[0]), Double.parseDouble(coords[1]));

                    // Different marker color based on capacity
                    float hue = BitmapDescriptorFactory.HUE_ORANGE;
                    if (stay.getAvailability().equals("Limited")) {
                        hue = BitmapDescriptorFactory.HUE_ORANGE;
                    } else if (stay.getAvailability().equals("Full")) {
                        hue = BitmapDescriptorFactory.HUE_RED;
                    }

                    mMap.addMarker(new MarkerOptions()
                            .position(position)
                            .title(stay.getName())
                            .snippet("Capacity: " + stay.getCapacity() + ", Status: " + stay.getAvailability())
                            .icon(BitmapDescriptorFactory.defaultMarker(hue)));
                } catch (NumberFormatException e) {
                    Log.e("MapMarker", "Invalid coordinates for stay: " + stay.getLocation());
                }
            }
        }
    }

    private void addTransportMarkers() {
        for (TransportOption transport : transportOptions) {
            String[] coords = transport.getLocation().split(",");
            if (coords.length == 2) {
                try {
                    LatLng position = new LatLng(Double.parseDouble(coords[0]), Double.parseDouble(coords[1]));

                    mMap.addMarker(new MarkerOptions()
                            .position(position)
                            .title(transport.getType())
                            .snippet("ETA: " + transport.getEta() + ", " + transport.getDetails())
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
                } catch (NumberFormatException e) {
                    Log.e("MapMarker", "Invalid coordinates for transport: " + transport.getLocation());
                }
            }
        }
    }

    private void getDirectionsToEntryGate() {
        // In a real app, this would use the Directions API
        // For demonstration, we'll draw a simple line

        if (mMap != null) {
            // Clear previous route
            if (currentRoute != null) {
                currentRoute.remove();
            }

            // Get user's current location
            if (ActivityCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.getLastLocation()
                        .addOnSuccessListener(requireActivity(), new OnSuccessListener<Location>() {
                            @Override
                            public void onSuccess(Location location) {
                                if (location != null) {
                                    LatLng currentLatLng = new LatLng(
                                            location.getLatitude(),
                                            location.getLongitude());

                                    // Draw route from current location to entry gate
                                    PolylineOptions routeOptions = new PolylineOptions()
                                            .add(currentLatLng, ENTRY_GATE_1)
                                            .width(10)
                                            .color(getResources().getColor(R.color.black));

                                    currentRoute = mMap.addPolyline(routeOptions);

                                    // Show route information
                                    routeInfoCard.setVisibility(View.VISIBLE);
                                    updateRouteInfo("Clear", "Right turn in 500m", "Low congestion");
                                }
                            }
                        });
            }
        }
    }

    private void updateRouteInfo(String status, String nextTurn, String congestion) {
        tvRouteStatus.setText("Route Status: " + status);
        tvNextTurn.setText("Next turn: " + nextTurn);
        tvCongestionInfo.setText("Congestion: " + congestion);

        // Change color based on status
        int color;
        switch (status.toLowerCase()) {
            case "clear":
                color = getResources().getColor(R.color.green);
                break;
            case "moderate":
                color = getResources().getColor(R.color.orange);
                break;
            case "congested":
                color = getResources().getColor(R.color.red);
                break;
            default:
                color = getResources().getColor(R.color.green);
        }

        tvRouteStatus.setTextColor(color);
    }

    private void showBestTimeInfo() {
        hideAllInfoPanels();
        tvInfoTitle.setText("Best Time to Visit");
        tvInfoContent.setText("The best time to visit Kumbh Mela Ujjain is:\n\n" +
                "• Early mornings (5 AM - 8 AM) for peaceful darshan\n" +
                "• Weekdays instead of weekends\n" +
                "• Avoid peak hours (11 AM - 3 PM)\n\n" +
                "Let us know when you're planning to come so we can provide better recommendations " +
                "and you can see how many people are planning to visit on specific days.");

        // Show date selection section
        View dateSelectionSection = getView().findViewById(R.id.date_selection_section);
        dateSelectionSection.setVisibility(View.VISIBLE);

        // Reset date selection visibility
        isDateSelectionVisible = false;
        btnSelectDate.setVisibility(View.GONE);

        // Load the latest statistics
        loadDateStats();
    }

    private void showParkingInfo() {
        hideAllInfoPanels();
        parkingInfo.setVisibility(View.VISIBLE);
        tvInfoTitle.setText("Parking Information - " + selectedUserType);
        tvInfoContent.setText("Real-time parking availability for " + selectedUserType + " visitors:");
    }

    private void showStaysInfo() {
        hideAllInfoPanels();
        View staysInfo = getView().findViewById(R.id.stays_info);
        staysInfo.setVisibility(View.VISIBLE);
        tvInfoTitle.setText("Nearby Stays - " + selectedUserType);
        tvInfoContent.setText("Government provided accommodation for " + selectedUserType + " visitors:");
    }

    private void showTransportInfo() {
        hideAllInfoPanels();
        transportInfo.setVisibility(View.VISIBLE);
        tvInfoTitle.setText("Transport Options - " + selectedUserType);
        tvInfoContent.setText("Available transportation for " + selectedUserType + " visitors:");
    }

    private void showVipInfo() {
        hideAllInfoPanels();
        tvInfoTitle.setText("VIP & Disabled Access");
        tvInfoContent.setText("Special facilities for VIP and disabled visitors:\n\n" +
                "• Dedicated entry gates with wheelchair access\n" +
                "• Special transportation services\n" +
                "• Reserved parking areas near entry points\n" +
                "• Assistance volunteers available\n\n" +
                "Contact help desk at: 0755-XXXXXX");
    }

    private void hideAllInfoPanels() {
        parkingInfo.setVisibility(View.GONE);
        transportInfo.setVisibility(View.GONE);

        View staysInfo = getView().findViewById(R.id.stays_info);
        staysInfo.setVisibility(View.GONE);

        // Hide date selection section
        View dateSelectionSection = getView().findViewById(R.id.date_selection_section);
        dateSelectionSection.setVisibility(View.GONE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableUserLocation();
            } else {
                Toast.makeText(getContext(), "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Start live updates for transport data
    private void startLiveUpdates() {
        liveUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                updateLiveTransportData();
                liveUpdateHandler.postDelayed(this, 30000); // Update every 30 seconds
            }
        };
        liveUpdateHandler.postDelayed(liveUpdateRunnable, 30000);
    }

    // Update transport data with live information
    private void updateLiveTransportData() {
        Random random = new Random();

        for (TransportOption option : transportOptions) {
            // Simulate changing ETA and distance
            int etaChange = random.nextInt(5) - 2; // -2 to +2 minutes change
            String currentEta = option.getEta();

            try {
                if (!currentEta.equals("On demand") && !currentEta.equals("By appointment")) {
                    int currentEtaValue = Integer.parseInt(currentEta.replace(" mins", "").replace(" min", ""));
                    int newEta = Math.max(1, currentEtaValue + etaChange);
                    option.setEta(newEta + " mins");

                    // Update distance based on ETA change
                    double distanceChange = etaChange * 0.2; // Approximate distance change
                    String currentDistance = option.getDistance();
                    if (currentDistance.contains("km")) {
                        double currentDistanceValue = Double.parseDouble(currentDistance.replace(" km away", "").replace(" km", ""));
                        double newDistance = Math.max(0.1, currentDistanceValue - distanceChange);
                        option.setDistance(String.format(Locale.getDefault(), "%.1f km away", newDistance));
                    }
                }
            } catch (NumberFormatException e) {
                Log.e("LiveUpdate", "Error parsing ETA or distance: " + e.getMessage());
            }
        }

        // Notify adapter of data change
        if (transportRecycler.getAdapter() != null) {
            transportRecycler.getAdapter().notifyDataSetChanged();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Stop live updates when fragment is destroyed
        liveUpdateHandler.removeCallbacks(liveUpdateRunnable);
    }

    // Transport option data class
    private static class TransportOption {
        private String type;
        private String availability;
        private String distance;
        private String details;
        private String location;
        private String eta;

        TransportOption(String type, String availability, String distance, String details, String location, String eta) {
            this.type = type;
            this.availability = availability;
            this.distance = distance;
            this.details = details;
            this.location = location;
            this.eta = eta;
        }

        public String getType() { return type; }
        public String getAvailability() { return availability; }
        public String getDistance() { return distance; }
        public String getDetails() { return details; }
        public String getLocation() { return location; }
        public String getEta() { return eta; }

        public void setEta(String eta) { this.eta = eta; }
        public void setDistance(String distance) { this.distance = distance; }
    }

    // Parking option data class
    private static class ParkingOption {
        private String name;
        private String location;
        private String capacity;
        private String availability;
        private String details;

        ParkingOption(String name, String location, String capacity, String availability, String details) {
            this.name = name;
            this.location = location;
            this.capacity = capacity;
            this.availability = availability;
            this.details = details;
        }

        public String getName() { return name; }
        public String getLocation() { return location; }
        public String getCapacity() { return capacity; }
        public String getAvailability() { return availability; }
        public String getDetails() { return details; }
    }

    // Stay option data class
    private static class StayOption {
        private String name;
        private String location;
        private String capacity;
        private String availability;
        private String details;

        StayOption(String name, String location, String capacity, String availability, String details) {
            this.name = name;
            this.location = location;
            this.capacity = capacity;
            this.availability = availability;
            this.details = details;
        }

        public String getName() { return name; }
        public String getLocation() { return location; }
        public String getCapacity() { return capacity; }
        public String getAvailability() { return availability; }
        public String getDetails() { return details; }
    }

    // Transport adapter for RecyclerView
    private class TransportAdapter extends RecyclerView.Adapter<TransportAdapter.ViewHolder> {
        private List<TransportOption> transportOptions;

        public TransportAdapter(List<TransportOption> transportOptions) {
            this.transportOptions = transportOptions;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_transport, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            TransportOption option = transportOptions.get(position);
            // Bind data to views (implementation depends on your item_transport.xml layout)
            // Example:
            // holder.typeTextView.setText(option.getType());
            // holder.etaTextView.setText("ETA: " + option.getEta());
            // holder.distanceTextView.setText(option.getDistance());
            // holder.detailsTextView.setText(option.getDetails());
        }

        @Override
        public int getItemCount() {
            return transportOptions.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            // View holder implementation
            // Example:
            // TextView typeTextView, etaTextView, distanceTextView, detailsTextView;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                // Initialize views
                // typeTextView = itemView.findViewById(R.id.tv_transport_type);
                // etaTextView = itemView.findViewById(R.id.tv_eta);
                // distanceTextView = itemView.findViewById(R.id.tv_distance);
                // detailsTextView = itemView.findViewById(R.id.tv_details);
            }
        }
    }

    // Parking adapter for RecyclerView
    private class ParkingAdapter extends RecyclerView.Adapter<ParkingAdapter.ViewHolder> {
        private List<ParkingOption> parkingOptions;

        public ParkingAdapter(List<ParkingOption> parkingOptions) {
            this.parkingOptions = parkingOptions;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_parking, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ParkingOption option = parkingOptions.get(position);
            // Bind data to views
        }

        @Override
        public int getItemCount() {
            return parkingOptions.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
            }
        }
    }

    // Stay adapter for RecyclerView
    private class StayAdapter extends RecyclerView.Adapter<StayAdapter.ViewHolder> {
        private List<StayOption> stayOptions;

        public StayAdapter(List<StayOption> stayOptions) {
            this.stayOptions = stayOptions;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_stay, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            StayOption option = stayOptions.get(position);
            // Bind data to views
        }

        @Override
        public int getItemCount() {
            return stayOptions.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
            }
        }
    }
}