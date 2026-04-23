package com.example.rescuenet;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.telephony.SmsManager;
import android.util.Log;

import java.util.ArrayList;

/**
 * NetworkSmsHelper
 *
 * Checks if the device has an active internet/cellular network.
 * If YES  → sends an SMS directly to EMERGENCY_NUMBER.
 * If NO   → returns false so the caller falls back to the mesh.
 */
public class NetworkSmsHelper {

    private static final String TAG              = "NetworkSmsHelper";
    public  static final String EMERGENCY_NUMBER = "9057503965";

    /**
     * @return true  if network is available (SMS was attempted via SmsManager)
     *         false if no network (caller should use mesh instead)
     */
    public static boolean sendIfNetworkAvailable(Context context, Message msg) {
        if (!isNetworkAvailable(context)) {
            Log.d(TAG, "No network — skipping direct SMS, using mesh.");
            return false;
        }

        String smsText = buildSmsText(msg);
        Log.d(TAG, "Network available — sending SMS to " + EMERGENCY_NUMBER);

        try {
            SmsManager smsManager = SmsManager.getDefault();
            // Split in case message is longer than 160 chars
            ArrayList<String> parts = smsManager.divideMessage(smsText);
            if (parts.size() == 1) {
                smsManager.sendTextMessage(EMERGENCY_NUMBER, null, smsText, null, null);
            } else {
                smsManager.sendMultipartTextMessage(EMERGENCY_NUMBER, null, parts, null, null);
            }
            Log.d(TAG, "SMS sent: " + smsText);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "SMS send failed: " + e.getMessage());
            // Return true anyway — we had network; SMS failure is a separate concern
            return true;
        }
    }

    // ─────────────────────────────────────────────
    //  Check active network (WiFi or cellular)
    // ─────────────────────────────────────────────
    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        android.net.Network network = cm.getActiveNetwork();
        if (network == null) return false;

        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        if (caps == null) return false;

        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
    }

    // ─────────────────────────────────────────────
    //  Build human-readable SMS text from a Message
    // ─────────────────────────────────────────────
    private static String buildSmsText(Message msg) {
        String coords = (msg.getLatitude() != 0.0 || msg.getLongitude() != 0.0)
                ? String.format(java.util.Locale.US,
                        "Loc: %.5f,%.5f", msg.getLatitude(), msg.getLongitude())
                : "Loc: unknown";

        return String.format("[RescueNet] %s | %s | From: %s | %s",
                msg.getTypeLabel(),
                msg.getContent(),
                msg.getSenderName(),
                coords);
    }
}
