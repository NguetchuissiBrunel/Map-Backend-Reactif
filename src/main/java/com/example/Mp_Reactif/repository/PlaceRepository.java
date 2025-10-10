package com.example.Mp_Reactif.repository;

import com.example.Mp_Reactif.model.Coordinates;
import com.example.Mp_Reactif.model.Place;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class PlaceRepository {

    private final DatabaseClient databaseClient;

    // Bornes du Cameroun
    private static final double CAMEROON_MIN_LAT = 1.65;
    private static final double CAMEROON_MAX_LAT = 13.08;
    private static final double CAMEROON_MIN_LNG = 8.45;
    private static final double CAMEROON_MAX_LNG = 16.19;

    public PlaceRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    public Flux<Place> findByNameContaining(String name) {
        String sql = "SELECT id, nom, ST_X(geom) as lng, ST_Y(geom) as lat FROM lieux " +
                "WHERE lower(nom) ILIKE :name " +
                "AND ST_Within(geom, ST_MakeEnvelope(:minLng, :minLat, :maxLng, :maxLat, 4326)) " +
                "LIMIT 10";
        return databaseClient.sql(sql)
                .bind("name", "%" + name + "%")
                .bind("minLng", CAMEROON_MIN_LNG)
                .bind("minLat", CAMEROON_MIN_LAT)
                .bind("maxLng", CAMEROON_MAX_LNG)
                .bind("maxLat", CAMEROON_MAX_LAT)
                .map((row, metadata) -> {
                    Place place = new Place(row.get("id", Long.class), row.get("nom", String.class), null);
                    place.setCoordinates(new Coordinates(row.get("lat", Double.class), row.get("lng", Double.class)));
                    return place;
                })
                .all();
    }

    public Mono<Place> findPlaceByExactName(String name) {
        String sql = "SELECT id, nom, ST_X(geom) as lng, ST_Y(geom) as lat FROM lieux " +
                "WHERE lower(nom) = :name " +
                "AND ST_Within(geom, ST_MakeEnvelope(:minLng, :minLat, :maxLng, :maxLat, 4326))";
        return databaseClient.sql(sql)
                .bind("name", name)
                .bind("minLng", CAMEROON_MIN_LNG)
                .bind("minLat", CAMEROON_MIN_LAT)
                .bind("maxLng", CAMEROON_MAX_LNG)
                .bind("maxLat", CAMEROON_MAX_LAT)
                .map((row, metadata) -> {
                    Place place = new Place(row.get("id", Long.class), row.get("nom", String.class), null);
                    place.setCoordinates(new Coordinates(row.get("lat", Double.class), row.get("lng", Double.class)));
                    return place;
                })
                .one()
                .switchIfEmpty(Mono.empty());
    }

    public Mono<Place> findClosestPlace(double lat, double lng) {
        // Vérifier d'abord si les coordonnées sont au Cameroun
        if (!isInCameroon(lat, lng)) {
            return Mono.empty();
        }

        String sql = "SELECT id, nom, ST_X(geom) as lng, ST_Y(geom) as lat " +
                "FROM lieux " +
                "WHERE ST_Within(geom, ST_MakeEnvelope(:minLng, :minLat, :maxLng, :maxLat, 4326)) " +
                "ORDER BY geom <-> ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) LIMIT 1";
        return databaseClient.sql(sql)
                .bind("lng", lng)
                .bind("lat", lat)
                .bind("minLng", CAMEROON_MIN_LNG)
                .bind("minLat", CAMEROON_MIN_LAT)
                .bind("maxLng", CAMEROON_MAX_LNG)
                .bind("maxLat", CAMEROON_MAX_LAT)
                .map((row, metadata) -> {
                    Place place = new Place(row.get("id", Long.class), row.get("nom", String.class), null);
                    place.setCoordinates(new Coordinates(row.get("lat", Double.class), row.get("lng", Double.class)));
                    return place;
                })
                .one()
                .switchIfEmpty(Mono.empty());
    }

    public Mono<Void> savePlace(Place place) {
        // Vérifier que le lieu à sauvegarder est au Cameroun
        if (!isInCameroon(place.getCoordinates().getLat(), place.getCoordinates().getLng())) {
            return Mono.empty();
        }

        String sql = "INSERT INTO lieux (nom, geom) VALUES (:nom, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326))";
        return databaseClient.sql(sql)
                .bind("nom", place.getName())
                .bind("lng", place.getCoordinates().getLng())
                .bind("lat", place.getCoordinates().getLat())
                .then();
    }

    // Méthode utilitaire pour vérifier les coordonnées
    private boolean isInCameroon(double lat, double lng) {
        return lat >= CAMEROON_MIN_LAT && lat <= CAMEROON_MAX_LAT &&
                lng >= CAMEROON_MIN_LNG && lng <= CAMEROON_MAX_LNG;
    }
}