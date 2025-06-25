package com.example.Mp_Reactif.service;

import com.example.Mp_Reactif.model.Point;
import com.example.Mp_Reactif.model.Route;
import com.example.Mp_Reactif.model.RouteResponse;
import com.example.Mp_Reactif.model.RouteStep;
import com.example.Mp_Reactif.repository.PlaceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Service
public class RouteService {

    @Autowired
    private ConnectionFactory connectionFactory;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private WebClient.Builder webClientBuilder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Mono<Long> findNearestNode(Point point) {
        String roadNodeQuery = """
            SELECT DISTINCT source as id, ST_Distance(
                ST_Transform(geom, 3857),
                ST_Transform(ST_SetSRID(ST_Point(:lng, :lat), 4326), 3857)
            ) as distance
            FROM routes
            WHERE source IS NOT NULL
            UNION
            SELECT DISTINCT target as id, ST_Distance(
                ST_Transform(geom, 3857),
                ST_Transform(ST_SetSRID(ST_Point(:lng, :lat), 4326), 3857)
            ) as distance
            FROM routes
            WHERE target IS NOT NULL
            ORDER BY distance
            LIMIT 1
        """;

        String placeQuery = """
            SELECT id
            FROM lieux
            WHERE geom IS NOT NULL
            ORDER BY geom <-> ST_SetSRID(ST_Point(:lng, :lat), 4326)
            LIMIT 1
        """;

        return Mono.from(connectionFactory.create())
                .flatMap(connection -> Mono.from(connection.createStatement(roadNodeQuery)
                                .bind("lng", point.getLng())
                                .bind("lat", point.getLat())
                                .execute())
                        .flatMap(result -> Mono.from(result.map((row, metadata) -> row.get("id", Long.class))))
                        .switchIfEmpty(Mono.from(connection.createStatement(placeQuery)
                                        .bind("lng", point.getLng())
                                        .bind("lat", point.getLat())
                                        .execute())
                                .flatMap(result -> Mono.from(result.map((row, metadata) -> row.get("id", Long.class)))))
                        .doFinally(signal -> Mono.from(connection.close())))
                .switchIfEmpty(Mono.error(new Exception("No node found near the provided coordinates")));
    }

    private Mono<List<Route>> getRouteFromOSRM(List<Point> points, String mode, String startPlaceName, String endPlaceName) {
        String profile = mode.equals("walking") ? "foot" : mode.equals("cycling") ? "bike" : "car";
        String coordinates = points.stream()
                .map(p -> p.getLng() + "," + p.getLat())
                .reduce((a, b) -> a + ";" + b)
                .orElse("");

        String url = "https://router.project-osrm.org/route/v1/" + profile + "/" + coordinates + "?steps=true&geometries=geojson&alternatives=3";

        return webClientBuilder.build()
                .get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(jsonResponse -> {
                    if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
                        return Mono.error(new Exception("Empty response from OSRM"));
                    }
                    try {
                        JsonNode data = objectMapper.readTree(jsonResponse);
                        if (data.has("code") && !"Ok".equals(data.get("code").asText())) {
                            return Mono.error(new Exception("OSRM error: " + data.get("message").asText("Unknown error")));
                        }

                        List<Route> routes = new ArrayList<>();
                        if (data.has("routes") && data.get("routes").isArray()) {
                            for (JsonNode routeNode : data.get("routes")) {
                                List<RouteStep> steps = new ArrayList<>();
                                StringBuilder routeGeometry = new StringBuilder("LINESTRING(");

                                if (routeNode.has("geometry") && routeNode.get("geometry").has("coordinates")) {
                                    JsonNode coordinatesNode = routeNode.get("geometry").get("coordinates");
                                    if (coordinatesNode.isArray() && coordinatesNode.size() > 0) {
                                        for (int i = 0; i < coordinatesNode.size(); i++) {
                                            JsonNode coord = coordinatesNode.get(i);
                                            if (coord.isArray() && coord.size() >= 2) {
                                                routeGeometry.append(coord.get(0).asDouble()).append(" ")
                                                        .append(coord.get(1).asDouble());
                                                if (i < coordinatesNode.size() - 1) {
                                                    routeGeometry.append(", ");
                                                }
                                            }
                                        }
                                        routeGeometry.append(")");
                                    }
                                }

                                if (routeNode.has("legs") && routeNode.get("legs").isArray()) {
                                    for (JsonNode leg : routeNode.get("legs")) {
                                        if (leg.has("steps") && leg.get("steps").isArray()) {
                                            for (JsonNode step : leg.get("steps")) {
                                                StringBuilder stepGeometry = new StringBuilder("LINESTRING(");
                                                if (step.has("geometry") && step.get("geometry").has("coordinates")) {
                                                    JsonNode coordinatesNode = step.get("geometry").get("coordinates");
                                                    if (coordinatesNode.isArray() && coordinatesNode.size() > 0) {
                                                        for (int i = 0; i < coordinatesNode.size(); i++) {
                                                            JsonNode coord = coordinatesNode.get(i);
                                                            if (coord.isArray() && coord.size() >= 2) {
                                                                stepGeometry.append(coord.get(0).asDouble()).append(" ")
                                                                        .append(coord.get(1).asDouble());
                                                                if (i < coordinatesNode.size() - 1) {
                                                                    stepGeometry.append(", ");
                                                                }
                                                            }
                                                        }
                                                        stepGeometry.append(")");
                                                    }
                                                }

                                                RouteStep routeStep = new RouteStep();
                                                routeStep.setGeometry(stepGeometry.toString());
                                                String instruction = step.has("maneuver") && step.get("maneuver").has("instruction")
                                                        ? step.get("maneuver").get("instruction").asText("Step")
                                                        : "Step";
                                                routeStep.setSource(instruction);
                                                routeStep.setTarget(instruction);
                                                routeStep.setDistance(step.has("distance") ? step.get("distance").asDouble() : 0.0);
                                                routeStep.setDuration(step.has("duration") ? step.get("duration").asDouble() : 0.0);
                                                steps.add(routeStep);
                                            }
                                        }
                                    }
                                }

                                if (steps.isEmpty() && !routeGeometry.toString().equals("LINESTRING(")) {
                                    RouteStep defaultStep = new RouteStep();
                                    defaultStep.setGeometry(routeGeometry.toString());
                                    defaultStep.setSource("Start");
                                    defaultStep.setTarget("End");
                                    defaultStep.setDistance(routeNode.has("distance") ? routeNode.get("distance").asDouble() : 0.0);
                                    defaultStep.setDuration(routeNode.has("duration") ? routeNode.get("duration").asDouble() : 0.0);
                                    steps.add(defaultStep);
                                }

                                Route resultRoute = new Route();
                                resultRoute.setDistance(routeNode.has("distance") ? routeNode.get("distance").asDouble() : 0.0);
                                resultRoute.setDuration(routeNode.has("duration") ? routeNode.get("duration").asDouble() : 0.0);
                                resultRoute.setSteps(steps);
                                resultRoute.setStartPlaceName(startPlaceName);
                                resultRoute.setEndPlaceName(endPlaceName);
                                resultRoute.setGeometry(routeGeometry.toString());
                                routes.add(resultRoute);
                            }
                        }
                        return Mono.just(routes);
                    } catch (Exception e) {
                        return Mono.error(new Exception("Error parsing OSRM response: " + e.getMessage()));
                    }
                })
                .onErrorResume(e -> {
                    System.err.println("Error with OSRM: " + e.getMessage());
                    return Mono.just(new ArrayList<>());
                });
    }

    public Mono<RouteResponse> routeWithPgRouting(List<Point> points, String mode, String startPlaceName, String endPlaceName) {
        if (points.size() != 2) {
            RouteResponse response = new RouteResponse();
            response.setError("Exactly two points are required for routing");
            return Mono.just(response);
        }

        return Mono.zip(findNearestNode(points.get(0)), findNearestNode(points.get(1)))
                .flatMap(tuple -> {
                    Long source = tuple.getT1();
                    Long target = tuple.getT2();
                    if (source.equals(target)) {
                        return Mono.error(new Exception("Source and target nodes are the same"));
                    }

                    String nodeValidationQuery = """
                        SELECT COUNT(*) as count FROM (
                            SELECT source as node FROM routes WHERE source = :source OR target = :source
                            UNION
                            SELECT target as node FROM routes WHERE source = :target OR target = :target
                        ) nodes
                    """;

                    return Mono.from(connectionFactory.create())
                            .flatMap(connection -> Mono.from(connection.createStatement(nodeValidationQuery)
                                            .bind("source", source)
                                            .bind("target", target)
                                            .execute())
                                    .flatMap(result -> Mono.from(result.map((row, metadata) -> row.get("count", Integer.class))))
                                    .doFinally(signal -> Mono.from(connection.close())))
                            .flatMap(nodeCount -> {
                                if (nodeCount == null || nodeCount == 0) {
                                    return Mono.error(new Exception("Nodes not found in road network"));
                                }

                                double vitesse = mode.equals("driving") ? 25 : mode.equals("walking") ? 2 : 8;

                                String query = """
                                    WITH chemins AS (
                                        SELECT path_id, path_seq, node, edge, cost, agg_cost
                                        FROM pgr_ksp(
                                            'SELECT id, source, target, cost, reverse_cost FROM routes WHERE cost IS NOT NULL AND cost > 0',
                                            :source, :target, 3, false
                                        )
                                    )
                                    SELECT
                                        c.path_id,
                                        c.path_seq,
                                        ST_AsText(r.geom) as geometry,
                                        COALESCE(l1.nom, 'Node ' || r.source) as source,
                                        COALESCE(l2.nom, 'Node ' || r.target) as target,
                                        c.cost as distance
                                    FROM chemins c
                                    JOIN routes r ON c.edge = r.id
                                    LEFT JOIN lieux l1 ON r.source = l1.id
                                    LEFT JOIN lieux l2 ON r.target = l2.id
                                    WHERE c.edge > 0
                                    ORDER BY c.path_id, c.path_seq
                                """;

                                return Mono.from(connectionFactory.create())
                                        .flatMapMany(connection -> Flux.from(connection.createStatement(query)
                                                        .bind("source", source)
                                                        .bind("target", target)
                                                        .execute())
                                                .flatMap(result -> Flux.from(result.map((row, metadata) -> {
                                                    RouteStep step = new RouteStep();
                                                    step.setGeometry(row.get("geometry", String.class));
                                                    step.setSource(row.get("source", String.class));
                                                    step.setTarget(row.get("target", String.class));
                                                    step.setDistance(row.get("distance", Double.class));
                                                    step.setDuration(row.get("distance", Double.class) / vitesse);
                                                    return new RouteStepWrapper(row.get("path_id", Integer.class), step);
                                                })))
                                                .doFinally(signal -> Mono.from(connection.close())))
                                        .collectList()
                                        .map(steps -> {
                                            List<RouteStep> routeStepsA = new ArrayList<>();
                                            List<RouteStep> routeStepsB = new ArrayList<>();
                                            List<RouteStep> routeStepsC = new ArrayList<>();
                                            double distanceA = 0;
                                            double distanceB = 0;
                                            double distanceC = 0;
                                            double durationA = 0;
                                            double durationB = 0;
                                            double durationC = 0;

                                            for (RouteStepWrapper wrapper : steps) {
                                                RouteStep step = wrapper.step;
                                                switch (wrapper.pathId) {
                                                    case 1:
                                                        routeStepsA.add(step);
                                                        distanceA += step.getDistance();
                                                        durationA += step.getDuration();
                                                        break;
                                                    case 2:
                                                        routeStepsB.add(step);
                                                        distanceB += step.getDistance();
                                                        durationB += step.getDuration();
                                                        break;
                                                    case 3:
                                                        routeStepsC.add(step);
                                                        distanceC += step.getDistance();
                                                        durationC += step.getDuration();
                                                        break;
                                                }
                                            }

                                            List<Route> routes = new ArrayList<>();
                                            if (!routeStepsA.isEmpty()) {
                                                Route routeA = new Route();
                                                routeA.setDistance(distanceA);
                                                routeA.setDuration(durationA);
                                                routeA.setSteps(routeStepsA);
                                                routeA.setStartPlaceName(startPlaceName);
                                                routeA.setEndPlaceName(endPlaceName);
                                                routeA.setGeometry(String.join(",", routeStepsA.stream()
                                                        .map(RouteStep::getGeometry)
                                                        .toList()));
                                                routes.add(routeA);
                                            }
                                            if (!routeStepsB.isEmpty()) {
                                                Route routeB = new Route();
                                                routeB.setDistance(distanceB);
                                                routeB.setDuration(durationB);
                                                routeB.setSteps(routeStepsB);
                                                routeB.setStartPlaceName(startPlaceName);
                                                routeB.setEndPlaceName(endPlaceName);
                                                routeB.setGeometry(String.join(",", routeStepsB.stream()
                                                        .map(RouteStep::getGeometry)
                                                        .toList()));
                                                routes.add(routeB);
                                            }
                                            if (!routeStepsC.isEmpty()) {
                                                Route routeC = new Route();
                                                routeC.setDistance(distanceC);
                                                routeC.setDuration(durationC);
                                                routeC.setSteps(routeStepsC);
                                                routeC.setStartPlaceName(startPlaceName);
                                                routeC.setEndPlaceName(endPlaceName);
                                                routeC.setGeometry(String.join(",", routeStepsC.stream()
                                                        .map(RouteStep::getGeometry)
                                                        .toList()));
                                                routes.add(routeC);
                                            }

                                            RouteResponse response = new RouteResponse();
                                            if (routes.isEmpty()) {
                                                response.setError("No routes found by pgRouting");
                                            } else {
                                                response.setRoutes(routes);
                                            }
                                            return response;
                                        });
                            });
                })
                .onErrorResume(e -> getRouteFromOSRM(points, mode, startPlaceName, endPlaceName)
                        .map(routes -> {
                            RouteResponse response = new RouteResponse();
                            response.setRoutes(routes);
                            if (routes.isEmpty()) {
                                response.setError("No route found with local and external methods");
                            }
                            return response;
                        }));
    }

    public Mono<RouteResponse> routeWithDetour(Point start, Point detour, Point end, String mode, String startPlaceName, String detourPlaceName, String endPlaceName) {
        String osrmMode = mode.equals("taxi") || mode.equals("bus") ? "driving" : mode.equals("moto") ? "cycling" : "driving";

        return Mono.zip(
                getRouteFromOSRM(List.of(start, detour), osrmMode, startPlaceName, detourPlaceName),
                getRouteFromOSRM(List.of(detour, end), osrmMode, detourPlaceName, endPlaceName)
        ).flatMap(tuple -> {
            List<Route> firstRoutes = tuple.getT1();
            List<Route> secondRoutes = tuple.getT2();

            if (firstRoutes.isEmpty()) {
                RouteResponse response = new RouteResponse();
                response.setError("No route found for start to detour");
                return Mono.just(response);
            }
            if (secondRoutes.isEmpty()) {
                RouteResponse response = new RouteResponse();
                response.setError("No route found for detour to end");
                return Mono.just(response);
            }

            List<Route> combinedRoutes = new ArrayList<>();
            Route combinedRoute = new Route();
            combinedRoute.setStartPlaceName(startPlaceName);
            combinedRoute.setEndPlaceName(endPlaceName);

            List<RouteStep> combinedSteps = new ArrayList<>();
            combinedSteps.addAll(firstRoutes.get(0).getSteps());
            combinedSteps.addAll(secondRoutes.get(0).getSteps());

            double totalDistance = firstRoutes.get(0).getDistance() + secondRoutes.get(0).getDistance();
            double totalDuration = firstRoutes.get(0).getDuration() + secondRoutes.get(0).getDuration();

            combinedRoute.setSteps(combinedSteps);
            combinedRoute.setDistance(totalDistance);
            combinedRoute.setDuration(totalDuration);

            StringBuilder wkt = new StringBuilder("LINESTRING(");
            List<List<Double>> allCoordinates = new ArrayList<>();
            for (RouteStep step : combinedSteps) {
                String geometry = step.getGeometry();
                if (geometry != null && geometry.startsWith("LINESTRING")) {
                    String coords = geometry.substring("LINESTRING(".length(), geometry.length() - 1);
                    String[] coordPairs = coords.split(", ");
                    for (String coordPair : coordPairs) {
                        String[] parts = coordPair.split(" ");
                        if (parts.length == 2) {
                            List<Double> point = new ArrayList<>();
                            point.add(Double.parseDouble(parts[0]));
                            point.add(Double.parseDouble(parts[1]));
                            allCoordinates.add(point);
                        }
                    }
                }
            }

            for (int i = 0; i < allCoordinates.size(); i++) {
                List<Double> coord = allCoordinates.get(i);
                wkt.append(coord.get(0)).append(" ").append(coord.get(1));
                if (i < allCoordinates.size() - 1) {
                    wkt.append(", ");
                }
            }
            wkt.append(")");
            combinedRoute.setGeometry(wkt.toString());

            combinedRoutes.add(combinedRoute);

            RouteResponse response = new RouteResponse();
            response.setRoutes(combinedRoutes);
            return Mono.just(response);
        }).onErrorResume(e -> {
            RouteResponse response = new RouteResponse();
            response.setError("Unable to calculate route with detour: " + e.getMessage());
            return Mono.just(response);
        });
    }

    private static class RouteStepWrapper {
        int pathId;
        RouteStep step;

        RouteStepWrapper(int pathId, RouteStep step) {
            this.pathId = pathId;
            this.step = step;
        }
    }
}