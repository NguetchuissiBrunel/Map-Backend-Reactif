package com.example.Mp_Reactif.event;

public class PlaceSearchedEvent {
    private String query;
    private int resultsCount;
    private long timestamp;

    public PlaceSearchedEvent() {
    }

    public PlaceSearchedEvent(String query, int resultsCount) {
        this.query = query;
        this.resultsCount = resultsCount;
        this.timestamp = System.currentTimeMillis();
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public int getResultsCount() {
        return resultsCount;
    }

    public void setResultsCount(int resultsCount) {
        this.resultsCount = resultsCount;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "PlaceSearchedEvent{" +
                "query='" + query + '\'' +
                ", resultsCount=" + resultsCount +
                ", timestamp=" + timestamp +
                '}';
    }
}
