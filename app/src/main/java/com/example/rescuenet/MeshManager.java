package com.example.rescuenet;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;
import com.google.gson.Gson;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MeshManager {

    private static final String TAG           = "MeshManager";
    private static final String SERVICE_ID    = "com.example.rescuenet.mesh";
    private static final int    MAX_HOPS      = 5;
    private static final int    MAX_MSG_CACHE = 200;
    // FIX: Queue up to 50 messages while offline so they aren't lost
    private static final int    MAX_QUEUE     = 50;

    public interface MeshListener {
        void onMessageReceived(Message message);
        void onPeerConnected(String endpointId, String endpointName);
        void onPeerDisconnected(String endpointId);
        void onConnectionStatusChanged(int connectedCount);
    }

    private final Context           context;
    private final ConnectionsClient connectionsClient;
    private final Gson              gson;
    private final String            localUserId;
    private final String            localUserName;
    private       MeshListener      listener;

    // endpointId → name
    private final Map<String, String> connectedPeers = new ConcurrentHashMap<>();
    // message IDs we've already seen (dedup)
    private final Set<String>         seenMessageIds = new HashSet<>();

    // FIX: Outbound queue for messages that arrive when no peers are connected
    private final List<Message> pendingQueue = new ArrayList<>();

    // FIX: Track advertising/discovery state so restartMesh() avoids double-start
    private boolean isAdvertising = false;
    private boolean isDiscovering = false;

    public MeshManager(Context context, String localUserId, String localUserName) {
        this.context       = context.getApplicationContext();
        this.localUserId   = localUserId;
        this.localUserName = localUserName;
        this.connectionsClient = Nearby.getConnectionsClient(context);
        this.gson = new Gson();
    }

    public void setListener(MeshListener listener) {
        this.listener = listener;
    }

    // ─────────────────────────────────────────────
    //  START mesh
    // ─────────────────────────────────────────────
    public void startMesh() {
        startAdvertising();
        startDiscovery();
    }

    // FIX: restartMesh — stops then restarts, called periodically for airplane mode recovery
    public void restartMesh() {
        Log.d(TAG, "Restarting mesh...");
        connectionsClient.stopAdvertising();
        connectionsClient.stopDiscovery();
        isAdvertising = false;
        isDiscovering = false;
        startAdvertising();
        startDiscovery();
    }

    public void stopMesh() {
        connectionsClient.stopAdvertising();
        connectionsClient.stopDiscovery();
        connectionsClient.stopAllEndpoints();
        connectedPeers.clear();
        isAdvertising = false;
        isDiscovering = false;
    }

    // ─────────────────────────────────────────────
    //  ADVERTISING
    // ─────────────────────────────────────────────
    private void startAdvertising() {
        if (isAdvertising) return; // FIX: Prevent double-start
        AdvertisingOptions options = new AdvertisingOptions.Builder()
                .setStrategy(Strategy.P2P_CLUSTER)
                .build();
        connectionsClient.startAdvertising(
                localUserName,
                SERVICE_ID,
                connectionLifecycleCallback,
                options
        ).addOnSuccessListener(unused -> {
            isAdvertising = true;
            Log.d(TAG, "Advertising started");
        }).addOnFailureListener(e -> {
            isAdvertising = false;
            Log.e(TAG, "Advertising failed: " + e.getMessage());
        });
    }

    // ─────────────────────────────────────────────
    //  DISCOVERY
    // ─────────────────────────────────────────────
    private void startDiscovery() {
        if (isDiscovering) return; // FIX: Prevent double-start
        DiscoveryOptions options = new DiscoveryOptions.Builder()
                .setStrategy(Strategy.P2P_CLUSTER)
                .build();
        connectionsClient.startDiscovery(
                SERVICE_ID,
                endpointDiscoveryCallback,
                options
        ).addOnSuccessListener(unused -> {
            isDiscovering = true;
            Log.d(TAG, "Discovery started");
        }).addOnFailureListener(e -> {
            isDiscovering = false;
            Log.e(TAG, "Discovery failed: " + e.getMessage());
        });
    }

    // ─────────────────────────────────────────────
    //  ENDPOINT DISCOVERY CALLBACK
    // ─────────────────────────────────────────────
    private final EndpointDiscoveryCallback endpointDiscoveryCallback =
            new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(@NonNull String endpointId,
                                    @NonNull DiscoveredEndpointInfo info) {
            Log.d(TAG, "Found endpoint: " + endpointId);
            connectionsClient.requestConnection(
                    localUserName,
                    endpointId,
                    connectionLifecycleCallback
            ).addOnFailureListener(e ->
                    Log.e(TAG, "Connection request failed: " + e.getMessage()));
        }

        @Override
        public void onEndpointLost(@NonNull String endpointId) {
            Log.d(TAG, "Lost endpoint: " + endpointId);
        }
    };

    // ─────────────────────────────────────────────
    //  CONNECTION LIFECYCLE CALLBACK
    // ─────────────────────────────────────────────
    private final ConnectionLifecycleCallback connectionLifecycleCallback =
            new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(@NonNull String endpointId,
                                          @NonNull ConnectionInfo info) {
            // Auto-accept all connections
            connectionsClient.acceptConnection(endpointId, payloadCallback);
        }

        @Override
        public void onConnectionResult(@NonNull String endpointId,
                                       @NonNull ConnectionResolution result) {
            if (result.getStatus().getStatusCode() == ConnectionsStatusCodes.STATUS_OK) {
                connectedPeers.put(endpointId, endpointId);
                Log.d(TAG, "Connected to: " + endpointId);
                if (listener != null) {
                    listener.onPeerConnected(endpointId, endpointId);
                    listener.onConnectionStatusChanged(connectedPeers.size());
                }
                // FIX: Flush queued messages now that we have a peer
                flushPendingQueue();
            } else {
                Log.w(TAG, "Connection failed: " + result.getStatus().getStatusCode());
            }
        }

        @Override
        public void onDisconnected(@NonNull String endpointId) {
            connectedPeers.remove(endpointId);
            Log.d(TAG, "Disconnected from: " + endpointId);
            if (listener != null) {
                listener.onPeerDisconnected(endpointId);
                listener.onConnectionStatusChanged(connectedPeers.size());
            }
        }
    };

    // ─────────────────────────────────────────────
    //  PAYLOAD CALLBACK
    // ─────────────────────────────────────────────
    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String endpointId,
                                      @NonNull Payload payload) {
            if (payload.getType() == Payload.Type.BYTES && payload.asBytes() != null) {
                String json = new String(payload.asBytes(), StandardCharsets.UTF_8);
                try {
                    Message msg = gson.fromJson(json, Message.class);
                    handleIncomingMessage(msg, endpointId);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse message: " + e.getMessage());
                }
            }
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointId,
                                            @NonNull PayloadTransferUpdate update) {
            // No-op for small byte payloads
        }
    };

    // ─────────────────────────────────────────────
    //  MESSAGE HANDLING
    // ─────────────────────────────────────────────
    private void handleIncomingMessage(Message msg, String fromEndpointId) {
        if (msg == null || msg.getId() == null) return;
        if (seenMessageIds.contains(msg.getId())) return;
        if (seenMessageIds.size() > MAX_MSG_CACHE) {
            seenMessageIds.clear();
        }
        seenMessageIds.add(msg.getId());
        if (listener != null) {
            listener.onMessageReceived(msg);
        }
        if (msg.getHopCount() < MAX_HOPS) {
            msg.incrementHop();
            relayMessage(msg, fromEndpointId);
        }
    }

    // ─────────────────────────────────────────────
    //  SEND — FIX: queue if no peers connected
    // ─────────────────────────────────────────────
    public void sendMessage(Message msg) {
        if (seenMessageIds.contains(msg.getId())) return;
        seenMessageIds.add(msg.getId());

        if (connectedPeers.isEmpty()) {
            // FIX: Queue message instead of dropping it silently
            if (pendingQueue.size() < MAX_QUEUE) {
                pendingQueue.add(msg);
                Log.d(TAG, "No peers — queued message: " + msg.getType() +
                        " (queue size: " + pendingQueue.size() + ")");
            } else {
                Log.w(TAG, "Queue full — dropping oldest message");
                pendingQueue.remove(0);
                pendingQueue.add(msg);
            }
        } else {
            relayMessage(msg, null);
        }
    }

    // FIX: Send all queued messages when a new peer connects
    private void flushPendingQueue() {
        if (pendingQueue.isEmpty()) return;
        Log.d(TAG, "Flushing " + pendingQueue.size() + " queued message(s)");
        List<Message> toFlush = new ArrayList<>(pendingQueue);
        pendingQueue.clear();
        for (Message msg : toFlush) {
            relayMessage(msg, null);
        }
    }

    private void relayMessage(Message msg, String excludeEndpointId) {
        String json    = gson.toJson(msg);
        byte[] bytes   = json.getBytes(StandardCharsets.UTF_8);
        Payload payload = Payload.fromBytes(bytes);
        for (String endpointId : connectedPeers.keySet()) {
            if (endpointId.equals(excludeEndpointId)) continue;
            connectionsClient.sendPayload(endpointId, payload)
                    .addOnSuccessListener(unused ->
                            Log.d(TAG, "Sent to " + endpointId))
                    .addOnFailureListener(e ->
                            Log.e(TAG, "Send failed to " + endpointId + ": " + e.getMessage()));
        }
    }

    public int getConnectedPeerCount() {
        return connectedPeers.size();
    }

    public boolean isConnected() {
        return !connectedPeers.isEmpty();
    }

    // FIX: Expose pending queue size for UI
    public int getPendingQueueSize() {
        return pendingQueue.size();
    }
}
