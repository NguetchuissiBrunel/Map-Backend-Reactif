package com.example.Mp_Reactif.model;

import java.util.List;

public class RouteResponse {
    private List<Route> routes;
    private String error;

    // Constructeur par défaut REQUIS
    public RouteResponse() {
    }

    public RouteResponse(List<Route> routes) {
        this.routes = routes;
    }

    public RouteResponse(String error) {
        this.error = error;
    }

    // Getters et setters
    public List<Route> getRoutes() {
        return routes;
    }

    public void setRoutes(List<Route> routes) {
        this.routes = routes;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}