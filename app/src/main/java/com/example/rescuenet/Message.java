package com.example.rescuenet;

import java.util.UUID;

public class Message {

    public enum Type {
        SOS, SAFE, MEDICAL, TRAPPED, GOVT_ALERT, LOCATION_UPDATE
    }

    private String id;
    private Type type;
    private double latitude;
    private double longitude;
    private long timestamp;
    private String senderId;
    private String senderName;
    private String content;
    private int hopCount;

    // Required empty constructor for Gson
    public Message() {}

    public Message(Type type, double latitude, double longitude,
                   String senderId, String senderName, String content) {
        this.id        = UUID.randomUUID().toString();
        this.type      = type;
        this.latitude  = latitude;
        this.longitude = longitude;
        this.timestamp = System.currentTimeMillis();
        this.senderId  = senderId;
        this.senderName = senderName;
        this.content   = content;
        this.hopCount  = 0;
    }

    // ── Getters ──
    public String getId()          { return id; }
    public Type getType()          { return type; }
    public double getLatitude()    { return latitude; }
    public double getLongitude()   { return longitude; }
    public long getTimestamp()     { return timestamp; }
    public String getSenderId()    { return senderId; }
    public String getSenderName()  { return senderName; }
    public String getContent()     { return content; }
    public int getHopCount()       { return hopCount; }

    // ── Setters ──
    public void setId(String id)             { this.id = id; }
    public void setType(Type type)           { this.type = type; }
    public void setLatitude(double lat)      { this.latitude = lat; }
    public void setLongitude(double lng)     { this.longitude = lng; }
    public void setTimestamp(long ts)        { this.timestamp = ts; }
    public void setSenderId(String sid)      { this.senderId = sid; }
    public void setSenderName(String name)   { this.senderName = name; }
    public void setContent(String content)   { this.content = content; }
    public void setHopCount(int hopCount)    { this.hopCount = hopCount; }

    public void incrementHop() { this.hopCount++; }

    public String getTypeLabel() {
        switch (type) {
            case SOS:           return "🚨 SOS";
            case SAFE:          return "✓ Safe";
            case MEDICAL:       return "+ Medical Help";
            case TRAPPED:       return "! Trapped";
            case GOVT_ALERT:    return "⚑ Authority Alert";
            case LOCATION_UPDATE: return "📍 Location";
            default:            return "Message";
        }
    }
}
