package com.example.Mp_Reactif.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Coordinates {
    private Double lat;
    private Double lng;

    // Constructeur par défaut REQUIS pour Jackson
    public Coordinates() {
    }

    public Coordinates(Double lat, Double lng) {
        this.lat = lat;
        this.lng = lng;
    }

    // Getters et setters
    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Double getLng() {
        return lng;
    }

    public void setLng(Double lng) {
        this.lng = lng;
    }
}