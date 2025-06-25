package com.example.Mp_Reactif.model;

import java.util.List;

public class RouteResponse {

    private List<Route> routes;
    private String error;

    // Getters et setters

    public void setRoutes(List<Route> routes) {
        this.routes = routes;
    }

    public List<Route> getRoutes() {
        return routes;
    }


    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}