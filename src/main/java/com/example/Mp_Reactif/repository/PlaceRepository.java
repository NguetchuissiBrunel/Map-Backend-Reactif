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

    public PlaceRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    public Flux<Place> findByNameContaining(String name) {
        String sql = "SELECT id, nom, ST_X(geom) as lng, ST_Y(geom) as lat FROM lieux " +
                "WHERE lower(nom) ILIKE :name " +
                "AND ST_Contains(ST_SetSRID(ST_MakeBox2D(ST_Point(8.4, 1.65), ST_Point(16.2, 13.08)), 4326), geom) " +
                "LIMIT 10";
        return databaseClient.sql(sql)
                .bind("name", "%" + name + "%")
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
                "AND ST_Contains(ST_SetSRID(ST_MakeBox2D(ST_Point(8.4, 1.65), ST_Point(16.2, 13.08)), 4326), geom)";
        return databaseClient.sql(sql)
                .bind("name", name)
                .map((row, metadata) -> {
                    Place place = new Place(row.get("id", Long.class), row.get("nom", String.class), null);
                    place.setCoordinates(new Coordinates(row.get("lat", Double.class), row.get("lng", Double.class)));
                    return place;
                })
                .one()
                .switchIfEmpty(Mono.empty());
    }

    public Mono<Place> findClosestPlace(double lat, double lng) {
        String sql = "SELECT id, nom, ST_X(geom) as lng, ST_Y(geom) as lat " +
                "FROM lieux " +
                "WHERE ST_Contains(ST_SetSRID(ST_MakeBox2D(ST_Point(8.4, 1.65), ST_Point(16.2, 13.08)), 4326), geom) " +
                "ORDER BY geom <-> ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) LIMIT 1";
        return databaseClient.sql(sql)
                .bind("lng", lng)
                .bind("lat", lat)
                .map((row, metadata) -> {
                    Place place = new Place(row.get("id", Long.class), row.get("nom", String.class), null);
                    place.setCoordinates(new Coordinates(row.get("lat", Double.class), row.get("lng", Double.class)));
                    return place;
                })
                .one()
                .switchIfEmpty(Mono.empty());
    }

    public Mono<Void> savePlace(Place place) {
        String sql = "INSERT INTO lieux (nom, geom) VALUES (:nom, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326))";
        return databaseClient.sql(sql)
                .bind("nom", place.getName())
                .bind("lng", place.getCoordinates().getLng())
                .bind("lat", place.getCoordinates().getLat())
                .then();
    }
}