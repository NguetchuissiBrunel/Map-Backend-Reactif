package com.example.Mp_Reactif.service;

import com.example.Mp_Reactif.model.Coordinates;
import com.example.Mp_Reactif.model.Place;
import com.example.Mp_Reactif.repository.PlaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.text.Normalizer;
import java.util.logging.Logger;

@Service
public class PlaceService {

    private static final Logger LOGGER = Logger.getLogger(PlaceService.class.getName());

    private final PlaceRepository placeRepository;
    private final WebClient webClient;

    public PlaceService(PlaceRepository placeRepository, WebClient.Builder webClientBuilder) {
        this.placeRepository = placeRepository;
        this.webClient = webClientBuilder.baseUrl("https://nominatim.openstreetmap.org").build();
    }

    public Flux<Place> searchPlaces(String name) {
        if (name == null || name.trim().isEmpty()) {
            return Flux.error(new IllegalArgumentException("Le paramètre name est requis"));
        }
        String normalizedName = normalizeName(name);
        LOGGER.info("Recherche dans la base pour : " + normalizedName + " (original: " + name + ")");
        return placeRepository.findByNameContaining(normalizedName)
                .switchIfEmpty(
                        searchPlaceInOSM(name)
                                .flatMap(osmPlace ->
                                        placeRepository.savePlace(osmPlace)
                                                .then(Mono.just(osmPlace)))
                                .flatMapMany(savedPlace -> placeRepository.findByNameContaining(normalizedName))
                )
                .doOnNext(place -> LOGGER.info("Lieu trouvé dans la base : " + place.getName()));
    }

    public Mono<Place> findClosestPlace(double lat, double lng) {
        if (Double.isNaN(lat) || Double.isNaN(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            return Mono.error(new IllegalArgumentException("Coordonnées invalides : latitude doit être entre -90 et 90, longitude entre -180 et 180"));
        }
        return placeRepository.findClosestPlace(lat, lng);
    }

    private Mono<Place> searchPlaceInOSM(String name) {
        return webClient.get()
                .uri(uriBuilder -> {
                    java.net.URI uri = uriBuilder
                            .path("/search")
                            .queryParam("q", name)
                            .queryParam("format", "json")
                            .queryParam("limit", 1)
                            .queryParam("accept-language", "fr")
                            .build();
                    LOGGER.info("URL OSM : " + uri.toString());
                    return uri;
                })
                .header("User-Agent", "map-backend/1.0")
                .retrieve()
                .bodyToFlux(OsmPlace.class)
                .doOnNext(osmPlace -> LOGGER.info("Réponse OSM brute : lat=" + osmPlace.getLat() + ", lon=" + osmPlace.getLon() + ", name=" + osmPlace.getName() + ", display_name=" + osmPlace.getDisplayName()))
                .next()
                .map(osmPlace -> {
                    String formattedName = normalizeName(osmPlace.getName());
                    LOGGER.info("Nom formaté : " + formattedName);
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

        public String getLat() {
            return lat;
        }

        public void setLat(String lat) {
            this.lat = lat;
        }

        public String getLon() {
            return lon;
        }

        public void setLon(String lon) {
            this.lon = lon;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDisplayName() {
            return display_name;
        }

        public void setDisplayName(String display_name) {
            this.display_name = display_name;
        }
    }
}