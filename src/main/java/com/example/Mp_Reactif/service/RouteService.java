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

    @Autowired
    private AdaptiveRedisCacheService cacheService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Variables pour stocker temporairement les noms des lieux
    private String currentStartPlaceName;
    private String currentEndPlaceName;

    public Mono<RouteResponse> routeWithPgRouting(List<Point> points, String mode, String startPlaceName, String endPlaceName) {
        if (points.size() != 2) {
            return Mono.just(createErrorResponse("Exactement deux points sont requis"));
        }

        // Stocker les noms pour utilisation dans les méthodes internes
        this.currentStartPlaceName = startPlaceName != null ? startPlaceName : "Départ";
        this.currentEndPlaceName = endPlaceName != null ? endPlaceName : "Arrivée";

        Point start = points.get(0);
        Point end = points.get(1);
        String cacheKey = cacheService.generateRouteKey(start, end, mode);

        System.out.println("🗺️  Recherche itinéraire dans Redis Cloud...");

        return cacheService.get(cacheKey, RouteResponse.class)
                .switchIfEmpty(
                        calculateRouteWithFallback(points, mode, cacheKey)
                )
                .doOnNext(response -> {
                    if (response.getError() == null && !response.getRoutes().isEmpty()) {
                        double distance = response.getRoutes().get(0).getDistance();
                        System.out.println("✅ Itinéraire trouvé: " + distance + " km");
                    }
                });
    }

    private Mono<RouteResponse> calculateRouteWithFallback(List<Point> points, String mode, String cacheKey) {
        System.out.println("🔄 Calcul nouvel itinéraire (pgRouting + OSRM fallback)...");

        return calculateWithPgRouting(points, mode)
                .flatMap(response -> {
                    if (response.getError() == null && !response.getRoutes().isEmpty()) {
                        System.out.println("💾 Stockage résultat pgRouting dans Redis Cloud");
                        return cacheService.set(cacheKey, response).thenReturn(response);
                    } else {
                        System.out.println("🔄 Fallback vers OSRM...");
                        return calculateWithOSRM(points, mode)
                                .flatMap(osrmResponse -> {
                                    if (osrmResponse.getError() == null && !osrmResponse.getRoutes().isEmpty()) {
                                        System.out.println("💾 Stockage résultat OSRM dans Redis Cloud");
                                        return cacheService.set(cacheKey, osrmResponse).thenReturn(osrmResponse);
                                    }
                                    return Mono.just(osrmResponse);
                                });
                    }
                })
                .onErrorResume(error -> {
                    System.out.println("⚠️  Erreur générale, fallback vers OSRM: " + error.getMessage());
                    return calculateWithOSRM(points, mode)
                            .flatMap(osrmResponse -> {
                                if (osrmResponse.getError() == null && !osrmResponse.getRoutes().isEmpty()) {
                                    return cacheService.set(cacheKey, osrmResponse).thenReturn(osrmResponse);
                                }
                                return Mono.just(osrmResponse);
                            });
                });
    }

    private Mono<RouteResponse> calculateWithPgRouting(List<Point> points, String mode) {
        for (Point point : points) {
            if (!isWithinCameroon(point.getLat(), point.getLng())) {
                return Mono.just(createErrorResponse("Un des points est hors des limites du Cameroun"));
            }
        }

        return Mono.zip(findNearestNode(points.get(0)), findNearestNode(points.get(1)))
                .flatMap(tuple -> {
                    Long source = tuple.getT1();
                    Long target = tuple.getT2();
                    if (source.equals(target)) {
                        return Mono.just(createErrorResponse("Les nœuds source et cible sont identiques"));
                    }
                    return executePgRoutingQuery(source, target, mode);
                })
                .onErrorResume(e -> Mono.just(createErrorResponse("Erreur pgRouting: " + e.getMessage())));
    }

    private Mono<RouteResponse> executePgRoutingQuery(Long source, Long target, String mode) {
        double vitesse = mode.equals("driving") ? 25 : mode.equals("walking") ? 2 : 8;

        String query = """
            WITH chemins AS (
                SELECT path_id, path_seq, node, edge, cost, agg_cost
                FROM pgr_ksp(
                    'SELECT id, source, target, cost, reverse_cost FROM routes WHERE cost IS NOT NULL AND cost > 0 AND ST_Contains(ST_SetSRID(ST_MakeBox2D(ST_Point(8.4, 1.65), ST_Point(16.2, 13.08)), 4326), geom)',
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
            AND ST_Contains(ST_SetSRID(ST_MakeBox2D(ST_Point(8.4, 1.65), ST_Point(16.2, 13.08)), 4326), r.geom)
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
                .map(this::buildRouteResponseFromSteps)
                .onErrorResume(e -> Mono.just(createErrorResponse("Erreur exécution pgRouting: " + e.getMessage())));
    }

    private RouteResponse buildRouteResponseFromSteps(List<RouteStepWrapper> steps) {
        List<RouteStep> routeStepsA = new ArrayList<>();
        List<RouteStep> routeStepsB = new ArrayList<>();
        List<RouteStep> routeStepsC = new ArrayList<>();
        double distanceA = 0, distanceB = 0, distanceC = 0;
        double durationA = 0, durationB = 0, durationC = 0;

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
            routes.add(createRoute(routeStepsA, distanceA, durationA, currentStartPlaceName, currentEndPlaceName));
        }
        if (!routeStepsB.isEmpty()) {
            routes.add(createRoute(routeStepsB, distanceB, durationB, currentStartPlaceName, currentEndPlaceName));
        }
        if (!routeStepsC.isEmpty()) {
            routes.add(createRoute(routeStepsC, distanceC, durationC, currentStartPlaceName, currentEndPlaceName));
        }

        RouteResponse response = new RouteResponse();
        if (routes.isEmpty()) {
            response.setError("Aucun itinéraire trouvé par pgRouting");
        } else {
            response.setRoutes(routes);
        }
        return response;
    }

    private Route createRoute(List<RouteStep> steps, double distance, double duration,
                              String startPlaceName, String endPlaceName) {
        Route route = new Route();
        route.setDistance(distance);
        route.setDuration(duration);
        route.setSteps(steps);
        route.setStartPlaceName(startPlaceName);
        route.setEndPlaceName(endPlaceName);

        // Construction de la géométrie à partir des étapes
        String geometry = steps.stream()
                .map(RouteStep::getGeometry)
                .filter(geom -> geom != null && !geom.isEmpty())
                .findFirst()
                .orElse("LINESTRING(0 0, 1 1)");
        route.setGeometry(geometry);

        return route;
    }

    private Mono<RouteResponse> calculateWithOSRM(List<Point> points, String mode) {
        return getRouteFromOSRM(points, mode)
                .map(routes -> {
                    RouteResponse response = new RouteResponse();
                    response.setRoutes(routes);
                    if (routes.isEmpty()) {
                        response.setError("Aucun itinéraire trouvé avec OSRM");
                    }
                    return response;
                })
                .onErrorResume(e -> Mono.just(createErrorResponse("Erreur OSRM: " + e.getMessage())));
    }

    private Mono<List<Route>> getRouteFromOSRM(List<Point> points, String mode) {
        for (Point point : points) {
            if (!isWithinCameroon(point.getLat(), point.getLng())) {
                return Mono.just(new ArrayList<>());
            }
        }

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
                .flatMap(this::parseOSRMResponse)
                .onErrorResume(e -> Mono.just(new ArrayList<>()));
    }

    private Mono<List<Route>> parseOSRMResponse(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
            return Mono.just(new ArrayList<>());
        }
        try {
            JsonNode data = objectMapper.readTree(jsonResponse);
            if (data.has("code") && !"Ok".equals(data.get("code").asText())) {
                return Mono.just(new ArrayList<>());
            }

            List<Route> routes = new ArrayList<>();
            if (data.has("routes") && data.get("routes").isArray()) {
                for (JsonNode routeNode : data.get("routes")) {
                    Route route = parseOSRMRoute(routeNode);
                    if (route != null) {
                        routes.add(route);
                    }
                }
            }
            return Mono.just(routes);
        } catch (Exception e) {
            return Mono.just(new ArrayList<>());
        }
    }

    private Route parseOSRMRoute(JsonNode routeNode) {
        try {
            List<RouteStep> steps = new ArrayList<>();
            StringBuilder geometry = new StringBuilder("LINESTRING(");

            // Parsing de la géométrie
            if (routeNode.has("geometry") && routeNode.get("geometry").has("coordinates")) {
                JsonNode coordinatesNode = routeNode.get("geometry").get("coordinates");
                if (coordinatesNode.isArray()) {
                    List<String> coords = new ArrayList<>();
                    for (JsonNode coord : coordinatesNode) {
                        if (coord.isArray() && coord.size() >= 2) {
                            double lng = coord.get(0).asDouble();
                            double lat = coord.get(1).asDouble();
                            if (isWithinCameroon(lat, lng)) {
                                coords.add(lng + " " + lat);
                            }
                        }
                    }
                    if (!coords.isEmpty()) {
                        geometry.append(String.join(", ", coords));
                    }
                }
            }
            geometry.append(")");

            // Parsing des étapes
            if (routeNode.has("legs") && routeNode.get("legs").isArray()) {
                for (JsonNode leg : routeNode.get("legs")) {
                    if (leg.has("steps") && leg.get("steps").isArray()) {
                        for (JsonNode step : leg.get("steps")) {
                            RouteStep routeStep = parseOSRMStep(step);
                            if (routeStep != null) {
                                steps.add(routeStep);
                            }
                        }
                    }
                }
            }

            if (steps.isEmpty()) {
                RouteStep defaultStep = new RouteStep();
                defaultStep.setGeometry(geometry.toString());
                defaultStep.setSource("Départ");
                defaultStep.setTarget("Arrivée");
                defaultStep.setDistance(routeNode.has("distance") ? routeNode.get("distance").asDouble() : 0.0);
                defaultStep.setDuration(routeNode.has("duration") ? routeNode.get("duration").asDouble() : 0.0);
                steps.add(defaultStep);
            }

            Route route = new Route();
            route.setDistance(routeNode.has("distance") ? routeNode.get("distance").asDouble() : 0.0);
            route.setDuration(routeNode.has("duration") ? routeNode.get("duration").asDouble() : 0.0);
            route.setSteps(steps);
            route.setStartPlaceName(currentStartPlaceName);
            route.setEndPlaceName(currentEndPlaceName);
            route.setGeometry(geometry.toString());

            return route;
        } catch (Exception e) {
            return null;
        }
    }

    private RouteStep parseOSRMStep(JsonNode step) {
        try {
            RouteStep routeStep = new RouteStep();

            // Géométrie de l'étape
            StringBuilder stepGeometry = new StringBuilder("LINESTRING(");
            if (step.has("geometry") && step.get("geometry").has("coordinates")) {
                JsonNode coordsNode = step.get("geometry").get("coordinates");
                List<String> coords = new ArrayList<>();
                for (JsonNode coord : coordsNode) {
                    if (coord.isArray() && coord.size() >= 2) {
                        double lng = coord.get(0).asDouble();
                        double lat = coord.get(1).asDouble();
                        if (isWithinCameroon(lat, lng)) {
                            coords.add(lng + " " + lat);
                        }
                    }
                }
                if (!coords.isEmpty()) {
                    stepGeometry.append(String.join(", ", coords));
                }
            }
            stepGeometry.append(")");
            routeStep.setGeometry(stepGeometry.toString());

            // Instructions
            String instruction = "Étape";
            if (step.has("maneuver") && step.get("maneuver").has("instruction")) {
                instruction = step.get("maneuver").get("instruction").asText("Étape");
            }
            routeStep.setSource(instruction);
            routeStep.setTarget(instruction);

            // Distance et durée
            routeStep.setDistance(step.has("distance") ? step.get("distance").asDouble() : 0.0);
            routeStep.setDuration(step.has("duration") ? step.get("duration").asDouble() : 0.0);

            return routeStep;
        } catch (Exception e) {
            return null;
        }
    }

    public Mono<RouteResponse> routeWithDetour(Point start, Point detour, Point end, String mode,
                                               String startPlaceName, String detourPlaceName, String endPlaceName) {
        String osrmMode = mode.equals("taxi") || mode.equals("bus") ? "driving" : mode.equals("moto") ? "cycling" : "driving";

        if (!isWithinCameroon(start.getLat(), start.getLng()) ||
                !isWithinCameroon(detour.getLat(), detour.getLng()) ||
                !isWithinCameroon(end.getLat(), end.getLng())) {
            return Mono.just(createErrorResponse("Un ou plusieurs points sont hors des limites du Cameroun"));
        }

        return Mono.zip(
                getRouteFromOSRM(List.of(start, detour), osrmMode),
                getRouteFromOSRM(List.of(detour, end), osrmMode)
        ).flatMap(tuple -> {
            List<Route> firstRoutes = tuple.getT1();
            List<Route> secondRoutes = tuple.getT2();

            if (firstRoutes.isEmpty()) {
                return Mono.just(createErrorResponse("Aucun itinéraire trouvé de départ à détour"));
            }
            if (secondRoutes.isEmpty()) {
                return Mono.just(createErrorResponse("Aucun itinéraire trouvé de détour à arrivée"));
            }

            Route combinedRoute = combineRoutes(firstRoutes.get(0), secondRoutes.get(0), startPlaceName, endPlaceName);
            List<Route> combinedRoutes = List.of(combinedRoute);

            RouteResponse response = new RouteResponse();
            response.setRoutes(combinedRoutes);
            return Mono.just(response);
        }).onErrorResume(e -> Mono.just(createErrorResponse("Erreur calcul détour: " + e.getMessage())));
    }

    private Route combineRoutes(Route firstRoute, Route secondRoute, String startPlaceName, String endPlaceName) {
        Route combined = new Route();
        combined.setStartPlaceName(startPlaceName);
        combined.setEndPlaceName(endPlaceName);

        List<RouteStep> combinedSteps = new ArrayList<>();
        combinedSteps.addAll(firstRoute.getSteps());
        combinedSteps.addAll(secondRoute.getSteps());
        combined.setSteps(combinedSteps);

        combined.setDistance(firstRoute.getDistance() + secondRoute.getDistance());
        combined.setDuration(firstRoute.getDuration() + secondRoute.getDuration());

        // Combinaison simplifiée de la géométrie
        combined.setGeometry(firstRoute.getGeometry() + ";" + secondRoute.getGeometry());

        return combined;
    }

    private Mono<Long> findNearestNode(Point point) {
        String query = """
            SELECT id FROM lieux 
            WHERE ST_Contains(ST_SetSRID(ST_MakeBox2D(ST_Point(8.4, 1.65), ST_Point(16.2, 13.08)), 4326), geom)
            ORDER BY geom <-> ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) 
            LIMIT 1
        """;

        return Mono.from(connectionFactory.create())
                .flatMap(connection -> Mono.from(connection.createStatement(query)
                                .bind("lng", point.getLng())
                                .bind("lat", point.getLat())
                                .execute())
                        .flatMap(result -> Mono.from(result.map((row, metadata) ->
                                row.get("id", Long.class))))
                        .doFinally(signal -> Mono.from(connection.close())))
                .switchIfEmpty(Mono.error(new Exception("Aucun nœud trouvé")));
    }

    private boolean isWithinCameroon(double lat, double lng) {
        return lat >= 1.65 && lat <= 13.08 && lng >= 8.4 && lng <= 16.2;
    }

    private RouteResponse createErrorResponse(String error) {
        RouteResponse response = new RouteResponse();
        response.setError(error);
        return response;
    }

    private static class RouteStepWrapper {
        final int pathId;
        final RouteStep step;

        RouteStepWrapper(int pathId, RouteStep step) {
            this.pathId = pathId;
            this.step = step;
        }
    }
}