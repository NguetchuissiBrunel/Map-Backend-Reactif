package com.example.Mp_Reactif.model;

import java.util.List;

public class RouteRequestBody {
    private List<Point> points;
    private String mode;
    private String startPlaceName;
    private String endPlaceName;

    // Constructeur par défaut REQUIS
    public RouteRequestBody() {
    }

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

    public String getStartPlaceName() {
        return startPlaceName;
    }

    public void setStartPlaceName(String startPlaceName) {
        this.startPlaceName = startPlaceName;
    }

    public String getEndPlaceName() {
        return endPlaceName;
    }

    public void setEndPlaceName(String endPlaceName) {
        this.endPlaceName = endPlaceName;
    }
}