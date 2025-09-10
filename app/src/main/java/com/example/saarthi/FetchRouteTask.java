package com.example.saarthi;

import android.graphics.Color;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class FetchRouteTask extends AsyncTask<String, Void, List<LatLng>> {

    private GoogleMap mMap;
    private List<Marker> pathMarkers;
    private boolean isChangePath;
    private static final String TAG = "FetchRouteTask";

    public FetchRouteTask(GoogleMap map, List<Marker> pathMarkers, boolean isChangePath) {
        this.mMap = map;
        this.pathMarkers = pathMarkers;
        this.isChangePath = isChangePath;
    }

    @Override
    protected List<LatLng> doInBackground(String... strings) {
        String data = "";
        List<LatLng> path = new ArrayList<>();
        try {
            URL url = new URL(strings[0]);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.connect();

            InputStream inputStream = conn.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            data = sb.toString();
            br.close();
            conn.disconnect();

            JSONObject jsonObject = new JSONObject(data);
            JSONArray routes = jsonObject.getJSONArray("routes");
            if (routes.length() > 0) {
                JSONArray legs = routes.getJSONObject(0).getJSONArray("legs");
                for (int i = 0; i < legs.length(); i++) {
                    JSONArray steps = legs.getJSONObject(i).getJSONArray("steps");
                    for (int j = 0; j < steps.length(); j++) {
                        JSONObject step = steps.getJSONObject(j);
                        JSONObject startLoc = step.getJSONObject("start_location");
                        double lat = startLoc.getDouble("lat");
                        double lng = startLoc.getDouble("lng");
                        path.add(new LatLng(lat, lng));
                        JSONObject endLoc = step.getJSONObject("end_location");
                        double endLat = endLoc.getDouble("lat");
                        double endLng = endLoc.getDouble("lng");
                        path.add(new LatLng(endLat, endLng));
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error fetching route: " + e.getMessage());
            e.printStackTrace();
        }
        return path;
    }

    @Override
    protected void onPostExecute(List<LatLng> latLngs) {
        super.onPostExecute(latLngs);

        if (latLngs.isEmpty()) return;

        // Remove previous path markers if changing path
        if (isChangePath && pathMarkers != null) {
            for (Marker m : pathMarkers) if (m != null) m.remove();
            pathMarkers.clear();
        }

        // Draw polyline
        PolylineOptions polylineOptions = new PolylineOptions()
                .addAll(latLngs)
                .width(12)
                .color(Color.BLUE)
                .geodesic(true);
        Polyline polyline = mMap.addPolyline(polylineOptions);

        // Add "0" marker on the path (people count)
        for (LatLng point : latLngs) {
            Marker marker = mMap.addMarker(new MarkerOptions()
                    .position(point)
                    .title("0") // zero people on path
                    .icon(null)); // default small marker
            pathMarkers.add(marker);
        }
    }
}
