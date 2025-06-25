package com.example.Mp_Reactif.model;

public class RouteWithDetourRequestBody {
    private Point start;
    private Point detour;
    private Point end;
    private String transportMode;
    private String startPlaceName;
    private String detourPlaceName;
    private String endPlaceName;

    // Getters et setters
    public Point getStart() {
        return start;
    }

    public void setStart(Point start) {
        this.start = start;
    }

    public Point getDetour() {
        return detour;
    }

    public void setDetour(Point detour) {
        this.detour = detour;
    }

    public Point getEnd() {
        return end;
    }

    public void setEnd(Point end) {
        this.end = end;
    }

    public String getTransportMode() {
        return transportMode;
    }

    public void setTransportMode(String transportMode) {
        this.transportMode = transportMode;
    }

    public String getStartPlaceName() {
        return startPlaceName != null ? startPlaceName : "Départ";
    }

    public void setStartPlaceName(String startPlaceName) {
        this.startPlaceName = startPlaceName;
    }

    public String getDetourPlaceName() {
        return detourPlaceName != null ? detourPlaceName : "Détour";
    }

    public void setDetourPlaceName(String detourPlaceName) {
        this.detourPlaceName = detourPlaceName;
    }

    public String getEndPlaceName() {
        return endPlaceName != null ? endPlaceName : "Destination";
    }

    public void setEndPlaceName(String endPlaceName) {
        this.endPlaceName = endPlaceName;
    }
}