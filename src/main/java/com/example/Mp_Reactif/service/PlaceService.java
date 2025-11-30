package com.example.Mp_Reactif.service;

import com.example.Mp_Reactif.model.Coordinates;
import com.example.Mp_Reactif.model.Place;
import com.example.Mp_Reactif.repository.PlaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.text.Normalizer;
import java.util.List;
import java.util.logging.Logger;

@Service
public class PlaceService {

    private static final Logger LOGGER = Logger.getLogger(PlaceService.class.getName());

    private final PlaceRepository placeRepository;
    private final WebClient webClient;
    private final AdaptiveRedisCacheService cacheService;

    public PlaceService(PlaceRepository placeRepository,
                        WebClient.Builder webClientBuilder,
                        AdaptiveRedisCacheService cacheService) {
        this.placeRepository = placeRepository;
        this.webClient = webClientBuilder.baseUrl("https://nominatim.openstreetmap.org").build();
        this.cacheService = cacheService;
    }

    public Mono<String> getAllPlaceNamesFormatted() {
        LOGGER.info("📋 Récupération de tous les noms de lieux depuis la base de données");

        return placeRepository.findAllPlaceNames()
                .collectList()
                .map(names -> {
                    if (names.isEmpty()) {
                        return "";
                    }

                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < names.size(); i++) {
                        sb.append(names.get(i));
                        if (i < names.size() - 1) {
                            sb.append(",\n");
                        }
                    }
                    return sb.toString();
                });
    }

    public Flux<Place> searchPlaces(String name) {
        if (name == null || name.trim().isEmpty()) {
            return Flux.error(new IllegalArgumentException("Le paramètre name est requis"));
        }

        String normalizedName = normalizeName(name);
        String cacheKey = cacheService.generatePlacesKey(normalizedName);

        LOGGER.info("🔍 Recherche lieux: " + normalizedName);

        return cacheService.get(cacheKey, List.class)
                .flatMapMany(cachedPlaces -> {
                    LOGGER.info("✅ CACHE HIT - Lieux depuis Redis Cloud: " + normalizedName);
                    return Flux.fromIterable(cachedPlaces)
                            .cast(Place.class);
                })
                .switchIfEmpty(
                        searchInDatabaseAndCache(normalizedName, cacheKey)
                )
                ;
    }

    private Flux<Place> searchInDatabaseAndCache(String normalizedName, String cacheKey) {
        return placeRepository.findByNameContaining(normalizedName)
                .switchIfEmpty(
                        searchPlaceInOSM(normalizedName)
                                .filter(osmPlace -> isWithinCameroon(
                                        osmPlace.getCoordinates().getLat(),
                                        osmPlace.getCoordinates().getLng()))
                                .flatMap(osmPlace ->
                                        placeRepository.savePlace(osmPlace)
                                                .then(Mono.just(osmPlace)))
                                .flatMapMany(savedPlace ->
                                        placeRepository.findByNameContaining(normalizedName))
                )
                .collectList()
                .flatMapMany(places -> {
                    if (!places.isEmpty()) {
                        LOGGER.info("💾 CACHE MISS - Stockage dans Redis Cloud: " + normalizedName);
                        return cacheService.set(cacheKey, places)
                                .thenMany(Flux.fromIterable(places));
                    }
                    return Flux.fromIterable(places);
                });
    }

    public Mono<Place> findClosestPlace(double lat, double lng) {
        if (Double.isNaN(lat) || Double.isNaN(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            return Mono.error(new IllegalArgumentException("Coordonnées invalides"));
        }
        return placeRepository.findClosestPlace(lat, lng);
    }

    private Mono<Place> searchPlaceInOSM(String name) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search")
                        .queryParam("q", name + ", Cameroon")
                        .queryParam("format", "json")
                        .queryParam("limit", 1)
                        .queryParam("bounded", 1)
                        .queryParam("viewbox", "8.4,13.08,16.2,1.65")
                        .queryParam("accept-language", "fr")
                        .build())
                .header("User-Agent", "map-backend/1.0")
                .retrieve()
                .bodyToFlux(OsmPlace.class)
                .next()
                .map(osmPlace -> {
                    String formattedName = normalizeName(osmPlace.getName());
                    return new Place(null, formattedName, new Coordinates(
                            Double.parseDouble(osmPlace.getLat()),
                            Double.parseDouble(osmPlace.getLon())
                    ));
                })
                .onErrorResume(e -> {
                    LOGGER.severe("Erreur lors de la requête OSM : " + e.getMessage());
                    return Mono.empty();
                });
    }

    private boolean isWithinCameroon(double lat, double lng) {
        return lat >= 1.65 && lat <= 13.08 && lng >= 8.4 && lng <= 16.2;
    }

    private String normalizeName(String name) {
        if (name == null) return "";
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFD);
        return normalized.replaceAll("[\\p{M}]", "").toLowerCase().trim();
    }

    private static class OsmPlace {
        private String lat;
        private String lon;
        private String name;
        private String display_name;

        public String getLat() { return lat; }
        public void setLat(String lat) { this.lat = lat; }
        public String getLon() { return lon; }
        public void setLon(String lon) { this.lon = lon; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDisplayName() { return display_name; }
        public void setDisplayName(String display_name) { this.display_name = display_name; }
    }
}