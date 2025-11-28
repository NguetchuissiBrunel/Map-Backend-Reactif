package com.example.Mp_Reactif.model;

public class Point {
    private double lat;
    private double lng;

    // Constructeur par défaut REQUIS
    public Point() {
    }

    public Point(double lat, double lng) {
        this.lat = lat;
        this.lng = lng;
    }

    // Getters et setters
    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLng() {
        return lng;
    }

    public void setLng(double lng) {
        this.lng = lng;
    }
}