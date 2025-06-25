package com.example.Mp_Reactif.model;

import java.util.List;

public class RouteRequestBody {
    private List<Point> points;
    private String mode;

    public String getStartPlaceName() {
        return startPlaceName;
    }

    public String getEndPlaceName() {
        return endPlaceName;
    }

    private String startPlaceName;
    private String endPlaceName;

    // Getters et setters
    public List<Point> getPoints() {
        return points;
    }

    public void setPoints(List<Point> points) {
        this.points = points;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }
}
