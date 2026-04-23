package com.example.rescuenet;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends AppCompatActivity
        implements MeshManager.MeshListener, LocationTracker.LocationListener {

    private static final int PERMISSION_REQUEST = 100;

    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.INTERNET,
            Manifest.permission.SEND_SMS,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private MeshManager    meshManager;
    private LocationTracker locationTracker;

    private String localUserId;
    private String localUserName;

    private double currentLat = 0.0;
    private double currentLng = 0.0;

    private MapView          mapView;
    private Marker           selfMarker;
    private final Map<String, Marker> peerMarkers = new HashMap<>();

    private RecyclerView     recyclerView;
    private MessageAdapter   messageAdapter;
    private final List<Message> messageList = new ArrayList<>();

    private final Map<String, UserNode> peerNodes = new HashMap<>();

    private LinearLayout sosButton;
    private LinearLayout safeButton;
    private LinearLayout medicalButton;
    private LinearLayout trappedButton;
    private TextView     tvLocationText;
    private View         gpsDot;
    private TextView     tvResponderCount;
    private TextView     tvSignalStatus;
    private TextView     tvNetworkStatus;

    // Periodic location broadcast
    private final Handler locationBroadcastHandler = new Handler(Looper.getMainLooper());
    private final Runnable locationBroadcastRunnable = new Runnable() {
        @Override
        public void run() {
            broadcastLocationUpdate();
            locationBroadcastHandler.postDelayed(this, 15_000);
        }
    };

    // FIX: Retry mesh every 10s so it reconnects after airplane mode is turned off
    private final Handler meshRetryHandler = new Handler(Looper.getMainLooper());
    private final Runnable meshRetryRunnable = new Runnable() {
        @Override
        public void run() {
            if (meshManager != null && !meshManager.isConnected()) {
                meshManager.restartMesh();
            }
            meshRetryHandler.postDelayed(this, 10_000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().load(
                getApplicationContext(),
                getSharedPreferences("osmdroid", MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_main);
        initUserIdentity();
        bindViews();
        setupMap();
        setupRecyclerView();
        setupClickListeners();
        if (hasAllPermissions()) {
            startCoreServices();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST);
        }
    }

    private void initUserIdentity() {
        SharedPreferences prefs = getSharedPreferences("rescuenet_prefs", MODE_PRIVATE);
        localUserId = prefs.getString("user_id", null);
        if (localUserId == null) {
            localUserId = UUID.randomUUID().toString().substring(0, 8);
            prefs.edit().putString("user_id", localUserId).apply();
        }
        localUserName = prefs.getString("user_name", "Worker-" + localUserId.substring(0, 4));
    }

    private void bindViews() {
        sosButton        = findViewById(R.id.sosButton);
        safeButton       = findViewById(R.id.safeButton);
        medicalButton    = findViewById(R.id.medicalButton);
        trappedButton    = findViewById(R.id.trappedButton);
        tvLocationText   = findViewById(R.id.locationText);
        gpsDot           = findViewById(R.id.gpsDot);
        tvResponderCount = findViewById(R.id.responderCount);
        tvSignalStatus   = findViewById(R.id.signalStatus);
        tvNetworkStatus  = findViewById(R.id.tvNetworkStatus);
        mapView          = findViewById(R.id.mapView);
        recyclerView     = findViewById(R.id.recyclerMessages);
    }

    private void setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(15.0);
        mapView.getController().setCenter(new GeoPoint(28.6139, 77.2090));
    }

    private void setupRecyclerView() {
        messageAdapter = new MessageAdapter(this, messageList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(messageAdapter);
    }

    private void setupClickListeners() {
        // FIX: Buttons always functional regardless of mesh/GPS state
        sosButton.setOnClickListener(v -> sendAlert(Message.Type.SOS,
                "SOS! Emergency! I need immediate help!"));
        safeButton.setOnClickListener(v -> sendAlert(Message.Type.SAFE,
                "I am safe at this location."));
        medicalButton.setOnClickListener(v -> sendAlert(Message.Type.MEDICAL,
                "I need medical assistance urgently!"));
        trappedButton.setOnClickListener(v -> sendAlert(Message.Type.TRAPPED,
                "I am trapped and cannot move!"));
    }

    private void startCoreServices() {
        locationTracker = new LocationTracker(this);
        locationTracker.setListener(this);
        locationTracker.startTracking();
        locationTracker.getLastKnownLocation(this);

        // FIX: MeshManager now has a message queue for offline/airplane mode
        meshManager = new MeshManager(this, localUserId, localUserName);
        meshManager.setListener(this);
        meshManager.startMesh();

        locationBroadcastHandler.postDelayed(locationBroadcastRunnable, 5_000);
        // FIX: Start mesh retry loop
        meshRetryHandler.postDelayed(meshRetryRunnable, 10_000);
        updateSignalUI(0);
    }

    // ════════════════════════════════════════════
    //  SEND ALERT
    //  Priority 1: Network available  → SMS directly to emergency number
    //  Priority 2: No network         → mesh (queues if no peers yet)
    // ════════════════════════════════════════════
    private void sendAlert(Message.Type type, String content) {
        // Fallback to last known location if currentLat/Lng is still 0
        double lat = currentLat;
        double lng = currentLng;
        if (lat == 0.0 && lng == 0.0 && locationTracker != null) {
            lat = locationTracker.getLastLatitude();
            lng = locationTracker.getLastLongitude();
        }

        Message msg = new Message(type, lat, lng, localUserId, localUserName, content);

        // Always show in local feed immediately — user sees action taken
        addMessageToFeed(msg);
        updateSelfMarker(type);

        // ── Network path: send SMS directly ──────────────────────────────
        if (NetworkSmsHelper.isNetworkAvailable(this)) {
            boolean smsSent = NetworkSmsHelper.sendIfNetworkAvailable(this, msg);
            if (smsSent) {
                Toast.makeText(this,
                        msg.getTypeLabel() + " sent via SMS to emergency contact!",
                        Toast.LENGTH_SHORT).show();
                // Also relay on mesh for redundancy (won't hurt)
                if (meshManager != null) meshManager.sendMessage(msg);
                return;
            }
        }

        // ── Mesh path: no network available ──────────────────────────────
        if (meshManager != null) {
            meshManager.sendMessage(msg);
            if (!meshManager.isConnected()) {
                Toast.makeText(this,
                        msg.getTypeLabel() + " saved! Will send when mesh connects.\n" +
                        "(Enable Bluetooth/WiFi even in Airplane Mode)",
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this,
                        msg.getTypeLabel() + " sent to " +
                        meshManager.getConnectedPeerCount() + " mesh peer(s)!",
                        Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this,
                    msg.getTypeLabel() + " recorded. Services initializing...",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void broadcastLocationUpdate() {
        if (meshManager == null) return;
        if (currentLat == 0.0 && currentLng == 0.0) return;
        // FIX: Send always — MeshManager queues if not connected
        Message msg = new Message(
                Message.Type.LOCATION_UPDATE,
                currentLat, currentLng,
                localUserId, localUserName,
                "Location update");
        meshManager.sendMessage(msg);
    }

    @Override
    public void onLocationUpdated(double latitude, double longitude) {
        currentLat = latitude;
        currentLng = longitude;
        runOnUiThread(() -> {
            tvLocationText.setText(
                    String.format(java.util.Locale.US, "%.5f, %.5f", latitude, longitude));
            gpsDot.setBackgroundResource(R.drawable.circle_green);
            GeoPoint point = new GeoPoint(latitude, longitude);
            mapView.getController().animateTo(point);
            if (selfMarker == null) {
                selfMarker = new Marker(mapView);
                selfMarker.setTitle("You (" + localUserName + ")");
                selfMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                mapView.getOverlays().add(selfMarker);
            }
            selfMarker.setPosition(point);
            mapView.invalidate();
        });
    }

    @Override
    public void onMessageReceived(Message message) {
        runOnUiThread(() -> {
            addMessageToFeed(message);
            updatePeerOnMap(message);
        });
    }

    @Override
    public void onPeerConnected(String endpointId, String endpointName) {
        runOnUiThread(() ->
            Toast.makeText(this, "Peer connected: " + endpointName, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onPeerDisconnected(String endpointId) {
        runOnUiThread(() -> {
            peerNodes.remove(endpointId);
            Marker marker = peerMarkers.remove(endpointId);
            if (marker != null) {
                mapView.getOverlays().remove(marker);
                mapView.invalidate();
            }
        });
    }

    @Override
    public void onConnectionStatusChanged(int connectedCount) {
        runOnUiThread(() -> updateSignalUI(connectedCount));
    }

    private void addMessageToFeed(Message msg) {
        messageList.add(0, msg);
        if (messageList.size() > 50) messageList.remove(messageList.size() - 1);
        messageAdapter.notifyItemInserted(0);
        recyclerView.scrollToPosition(0);
    }

    private void updatePeerOnMap(Message msg) {
        if (msg.getLatitude() == 0.0 && msg.getLongitude() == 0.0) return;
        if (Message.Type.LOCATION_UPDATE != msg.getType()
                && msg.getType() != Message.Type.SOS
                && msg.getType() != Message.Type.SAFE
                && msg.getType() != Message.Type.MEDICAL
                && msg.getType() != Message.Type.TRAPPED
                && msg.getType() != Message.Type.GOVT_ALERT) return;
        String key = msg.getSenderId();
        GeoPoint point = new GeoPoint(msg.getLatitude(), msg.getLongitude());
        Marker marker = peerMarkers.get(key);
        if (marker == null) {
            marker = new Marker(mapView);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            mapView.getOverlays().add(marker);
            peerMarkers.put(key, marker);
        }
        marker.setPosition(point);
        marker.setTitle(msg.getSenderName() + " — " + msg.getTypeLabel());
        mapView.invalidate();
    }

    private void updateSelfMarker(Message.Type type) {
        if (selfMarker != null) {
            selfMarker.setTitle("You (" + localUserName + ") — "
                    + new Message(type, 0, 0, "", "", "").getTypeLabel());
            mapView.invalidate();
        }
    }

    private void updateSignalUI(int count) {
        tvResponderCount.setText(String.valueOf(count));
        if (count == 0) {
            tvSignalStatus.setText("No Signal");
            tvSignalStatus.setTextColor(Color.parseColor("#E74C3C"));
            if (tvNetworkStatus != null) {
                tvNetworkStatus.setText("● Mesh Offline");
                tvNetworkStatus.setTextColor(Color.parseColor("#E74C3C"));
            }
        } else if (count <= 2) {
            tvSignalStatus.setText("Weak");
            tvSignalStatus.setTextColor(Color.parseColor("#F1C40F"));
            if (tvNetworkStatus != null) {
                tvNetworkStatus.setText("● Mesh Active");
                tvNetworkStatus.setTextColor(Color.parseColor("#F1C40F"));
            }
        } else {
            tvSignalStatus.setText("Strong");
            tvSignalStatus.setTextColor(Color.parseColor("#2ECC71"));
            if (tvNetworkStatus != null) {
                tvNetworkStatus.setText("● Mesh Strong");
                tvNetworkStatus.setTextColor(Color.parseColor("#2ECC71"));
            }
        }
    }

    private boolean hasAllPermissions() {
        for (String perm : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, perm)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST) {
            startCoreServices();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
        // FIX: Try to reconnect mesh when app comes back to foreground
        if (meshManager != null && !meshManager.isConnected()) {
            meshManager.restartMesh();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        locationBroadcastHandler.removeCallbacks(locationBroadcastRunnable);
        meshRetryHandler.removeCallbacks(meshRetryRunnable);
        if (locationTracker != null) locationTracker.stopTracking();
        if (meshManager != null) meshManager.stopMesh();
        if (mapView != null) mapView.onDetach();
    }
}
