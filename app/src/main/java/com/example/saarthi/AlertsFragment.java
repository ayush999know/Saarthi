package com.example.saarthi;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class AlertsFragment extends Fragment {

    private Button logoutButton, submitReportButton;
    private EditText contactInput, reportInput;
    private FirebaseAuth mAuth;
    private DatabaseReference databaseReference;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    private ActivityResultLauncher<String> locationPermissionLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_alerts, container, false);

        mAuth = FirebaseAuth.getInstance();
        // Use the same database reference as your HomeFragment
        databaseReference = FirebaseDatabase.getInstance("https://saarthi-1d760-default-rtdb.firebaseio.com/").getReference();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        contactInput = rootView.findViewById(R.id.contactInput);
        reportInput = rootView.findViewById(R.id.reportInput);
        submitReportButton = rootView.findViewById(R.id.submitReportButton);
        logoutButton = rootView.findViewById(R.id.logoutButton);

        // Initialize location callback
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult != null && locationResult.getLastLocation() != null) {
                    saveReport(locationResult.getLastLocation());
                    fusedLocationClient.removeLocationUpdates(this);
                } else {
                    Toast.makeText(getContext(), "Unable to get location. Please try again.", Toast.LENGTH_SHORT).show();
                }
            }
        };

        // Handle location permission result
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        sendReportWithLocation();
                    } else {
                        Toast.makeText(getContext(), "Location permission is required to submit report", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        // Submit Report
        submitReportButton.setOnClickListener(v -> submitReport());

        // Logout
        logoutButton.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(getActivity(), MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            getActivity().finish();
        });

        return rootView;
    }

    private void submitReport() {
        String contact = contactInput.getText().toString().trim();
        String report = reportInput.getText().toString().trim();

        if (TextUtils.isEmpty(contact)) {
            Toast.makeText(getContext(), "Please enter your phone number", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(report)) {
            Toast.makeText(getContext(), "Please write your report", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if user is authenticated
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(getContext(), "Please sign in to submit a report", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check permission
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            sendReportWithLocation();
        } else {
            // Ask for permission
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void sendReportWithLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        saveReport(location);
                    } else {
                        // Request a new location if last location is null
                        requestNewLocation();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Failed to get location: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e("Location", "Location error: " + e.getMessage());
                });
    }

    private void requestNewLocation() {
        // Create location request
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(5000);

        // Request location updates
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());

            // Set a timeout for location request (10 seconds)
            new android.os.Handler(Looper.getMainLooper()).postDelayed(() -> {
                fusedLocationClient.removeLocationUpdates(locationCallback);
                Toast.makeText(getContext(), "Location request timeout. Please try again.", Toast.LENGTH_SHORT).show();
            }, 10000);
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void saveReport(Location location) {
        FirebaseUser user = mAuth.getCurrentUser();

        // Check if user is authenticated
        if (user == null) {
            Toast.makeText(getContext(), "Please sign in to submit a report", Toast.LENGTH_SHORT).show();
            return;
        }

        String username = (user.getDisplayName() != null)
                ? user.getDisplayName()
                : "Unknown User";

        // Create a unique key for the report
        String reportId = databaseReference.child("reports").push().getKey();

        if (reportId == null) {
            Toast.makeText(getContext(), "Failed to generate report ID", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> reportData = new HashMap<>();
        reportData.put("username", username);
        reportData.put("contact", contactInput.getText().toString().trim());
        reportData.put("report", reportInput.getText().toString().trim());
        reportData.put("latitude", location.getLatitude());
        reportData.put("longitude", location.getLongitude());
        reportData.put("timestamp", System.currentTimeMillis());
        reportData.put("userId", user.getUid());

        // Add a debug log
        Log.d("RealtimeDB", "Attempting to save report: " + reportData.toString());

        // Save to Firebase Realtime Database
        databaseReference.child("reports").child(reportId).setValue(reportData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Report submitted successfully", Toast.LENGTH_SHORT).show();
                    contactInput.setText("");
                    reportInput.setText("");
                    Log.d("RealtimeDB", "Report saved with ID: " + reportId);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Failed to submit report: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e("RealtimeDB", "Error saving report", e);
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Remove location updates to prevent memory leaks
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }
}