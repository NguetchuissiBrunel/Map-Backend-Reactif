package com.example.Mp_Reactif.event;

public class RouteCalculatedEvent {
    private String startPlace;
    private String endPlace;
    private double distance;
    private double duration;
    private String mode;
    private long timestamp;

    public RouteCalculatedEvent() {
    }

    public RouteCalculatedEvent(String startPlace, String endPlace, double distance, double duration, String mode) {
        this.startPlace = startPlace;
        this.endPlace = endPlace;
        this.distance = distance;
        this.duration = duration;
        this.mode = mode;
        this.timestamp = System.currentTimeMillis();
    }

    public String getStartPlace() {
        return startPlace;
    }

    public void setStartPlace(String startPlace) {
        this.startPlace = startPlace;
    }

    public String getEndPlace() {
        return endPlace;
    }

    public void setEndPlace(String endPlace) {
        this.endPlace = endPlace;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public double getDuration() {
        return duration;
    }

    public void setDuration(double duration) {
        this.duration = duration;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "RouteCalculatedEvent{" +
                "startPlace='" + startPlace + '\'' +
                ", endPlace='" + endPlace + '\'' +
                ", distance=" + distance +
                ", duration=" + duration +
                ", mode='" + mode + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
