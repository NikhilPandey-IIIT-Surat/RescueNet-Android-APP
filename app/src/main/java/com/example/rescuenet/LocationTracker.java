package com.example.rescuenet;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.os.Looper;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

public class LocationTracker {

    public interface LocationListener {
        void onLocationUpdated(double latitude, double longitude);
    }

    private static final long UPDATE_INTERVAL    = 10_000L; // 10 seconds
    private static final long FASTEST_INTERVAL   = 5_000L;  // 5 seconds

    private final FusedLocationProviderClient fusedClient;
    private       LocationCallback            locationCallback;
    private       LocationListener            listener;
    private       double                      lastLatitude  = 0.0;
    private       double                      lastLongitude = 0.0;

    public LocationTracker(Context context) {
        this.fusedClient = LocationServices.getFusedLocationProviderClient(context);
    }

    public void setListener(LocationListener listener) {
        this.listener = listener;
    }

    @SuppressLint("MissingPermission")
    public void startTracking() {
        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL)
                .setMinUpdateIntervalMillis(FASTEST_INTERVAL)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                if (result == null) return;
                Location loc = result.getLastLocation();
                if (loc != null) {
                    lastLatitude  = loc.getLatitude();
                    lastLongitude = loc.getLongitude();
                    if (listener != null) {
                        listener.onLocationUpdated(lastLatitude, lastLongitude);
                    }
                }
            }
        };

        fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
    }

    public void stopTracking() {
        if (locationCallback != null) {
            fusedClient.removeLocationUpdates(locationCallback);
        }
    }

    @SuppressLint("MissingPermission")
    public void getLastKnownLocation(LocationListener callback) {
        fusedClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null && callback != null) {
                lastLatitude  = location.getLatitude();
                lastLongitude = location.getLongitude();
                callback.onLocationUpdated(lastLatitude, lastLongitude);
            }
        });
    }

    public double getLastLatitude()  { return lastLatitude; }
    public double getLastLongitude() { return lastLongitude; }

    public boolean hasLocation() {
        return lastLatitude != 0.0 || lastLongitude != 0.0;
    }
}
