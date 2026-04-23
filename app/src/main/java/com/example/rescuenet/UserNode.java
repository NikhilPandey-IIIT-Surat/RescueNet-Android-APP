package com.example.rescuenet;

public class UserNode {

    public enum Role { WORKER, RESPONDER, GOVT_AUTHORITY, MINE_MANAGEMENT }
    public enum Status { UNKNOWN, SAFE, SOS, MEDICAL, TRAPPED }

    private String endpointId;
    private String userId;
    private String name;
    private double latitude;
    private double longitude;
    private long lastSeen;
    private Status status;
    private Role role;

    public UserNode() {}

    public UserNode(String endpointId, String userId, String name, Role role) {
        this.endpointId = endpointId;
        this.userId     = userId;
        this.name       = name;
        this.role       = role;
        this.status     = Status.UNKNOWN;
        this.lastSeen   = System.currentTimeMillis();
    }

    // ── Getters ──
    public String getEndpointId()  { return endpointId; }
    public String getUserId()      { return userId; }
    public String getName()        { return name; }
    public double getLatitude()    { return latitude; }
    public double getLongitude()   { return longitude; }
    public long getLastSeen()      { return lastSeen; }
    public Status getStatus()      { return status; }
    public Role getRole()          { return role; }

    // ── Setters ──
    public void setEndpointId(String eid)   { this.endpointId = eid; }
    public void setUserId(String uid)       { this.userId = uid; }
    public void setName(String name)        { this.name = name; }
    public void setLatitude(double lat)     { this.latitude = lat; }
    public void setLongitude(double lng)    { this.longitude = lng; }
    public void setLastSeen(long ts)        { this.lastSeen = ts; }
    public void setStatus(Status status)    { this.status = status; }
    public void setRole(Role role)          { this.role = role; }

    public void updateLocation(double lat, double lng) {
        this.latitude = lat;
        this.longitude = lng;
        this.lastSeen = System.currentTimeMillis();
    }

    public void updateFromMessage(Message msg) {
        updateLocation(msg.getLatitude(), msg.getLongitude());
        switch (msg.getType()) {
            case SOS:     this.status = Status.SOS;     break;
            case SAFE:    this.status = Status.SAFE;    break;
            case MEDICAL: this.status = Status.MEDICAL; break;
            case TRAPPED: this.status = Status.TRAPPED; break;
            default: break;
        }
    }
}
