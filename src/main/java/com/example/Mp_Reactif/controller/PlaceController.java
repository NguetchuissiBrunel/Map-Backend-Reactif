
package com.example.Mp_Reactif.controller;


import com.example.Mp_Reactif.service.PlaceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/places")
public class PlaceController {

    private final PlaceService placeService;

    public PlaceController(PlaceService placeService) {
        this.placeService = placeService;
    }

    @GetMapping
    public Mono<ResponseEntity<Map<String, Object>>> searchPlaces(@RequestParam String name) {
        return placeService.searchPlaces(name)
                .collectList()
                .map(places -> {
                    Map<String, Object> response = new HashMap<>();
                    if (places.isEmpty()) {
                        response.put("success", false);
                        response.put("error", "Aucun lieu trouvé");
                        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
                    }
                    response.put("success", true);
                    response.put("data", places);
                    return new ResponseEntity<>(response, HttpStatus.OK);
                })
                .onErrorResume(IllegalArgumentException.class, e -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("error", e.getMessage());
                    return Mono.just(new ResponseEntity<>(response, HttpStatus.BAD_REQUEST));
                })
                .onErrorResume(Exception.class, e -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("error", "Erreur serveur");
                    return Mono.just(new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR));
                });
    }

    @GetMapping("/names")
    public Mono<String> getAllPlaceNames() {
        return placeService.getAllPlaceNamesFormatted();
    }

    @GetMapping("/closest")
    public Mono<ResponseEntity<Map<String, Object>>> findClosestPlace(@RequestParam double lat, @RequestParam double lng) {
        return placeService.findClosestPlace(lat, lng)
                .map(place -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("data", place);
                    return new ResponseEntity<>(response, HttpStatus.OK);
                })
                .switchIfEmpty(Mono.just(new ResponseEntity<>(Map.of(
                        "success", false,
                        "error", "Aucun lieu trouvé près des coordonnées fournies"
                ), HttpStatus.NOT_FOUND)))
                .onErrorResume(IllegalArgumentException.class, e -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("error", e.getMessage());
                    return Mono.just(new ResponseEntity<>(response, HttpStatus.BAD_REQUEST));
                })
                .onErrorResume(Exception.class, e -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("error", "Erreur serveur");
                    return Mono.just(new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR));
                });
    }
}
