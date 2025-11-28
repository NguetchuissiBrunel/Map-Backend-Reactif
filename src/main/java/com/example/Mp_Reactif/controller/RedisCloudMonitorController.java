package com.example.Mp_Reactif.controller;

import com.example.Mp_Reactif.model.Point;
import com.example.Mp_Reactif.service.AdaptiveRedisCacheService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/redis-cloud")
public class RedisCloudMonitorController {

    private final AdaptiveRedisCacheService cacheService;

    public RedisCloudMonitorController(AdaptiveRedisCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @GetMapping("/health")
    public Mono<Map<String, Object>> health() {
        Map<String, Object> healthStatus = new HashMap<>();
        healthStatus.put("status", cacheService.isRedisAvailable() ? "UP" : "DOWN");
        healthStatus.put("service", "Redis Cloud");
        healthStatus.put("timestamp", System.currentTimeMillis());

        return Mono.just(healthStatus);
    }

    @GetMapping("/stats")
    public Mono<Map<String, Object>> stats() {
        return cacheService.getCacheStats();
    }
    @GetMapping("/test-serialization")
    public Mono<Map<String, Object>> testSerialization() {
        // Test avec un objet simple
        Point testPoint = new Point(3.8480, 11.5021);
        String testKey = "serialization-test";

        return cacheService.set(testKey, testPoint, Duration.ofMinutes(5))
                .flatMap(success -> {
                    if (success) {
                        return cacheService.get(testKey, Point.class)
                                .map(retrieved -> {
                                    Map<String, Object> response = new HashMap<>();
                                    response.put("success", true);
                                    response.put("original", testPoint);
                                    response.put("retrieved", retrieved);
                                    response.put("equals", testPoint.getLat() == retrieved.getLat() &&
                                            testPoint.getLng() == retrieved.getLng());
                                    return response;
                                })
                                .switchIfEmpty(Mono.just(createErrorResponse("Échec de la récupération")));
                    } else {
                        return Mono.just(createErrorResponse("Échec du stockage"));
                    }
                });
    }

    @GetMapping("/test")
    public Mono<Map<String, Object>> testConnection() {
        String testKey = "redis-cloud-test";
        String testValue = "Redis Cloud connection successful - " + System.currentTimeMillis();

        return cacheService.set(testKey, testValue, Duration.ofMinutes(5))
                .flatMap(success -> {
                    if (success) {
                        return cacheService.get(testKey, String.class)
                                .map(result -> {
                                    Map<String, Object> response = new HashMap<>();
                                    response.put("success", true);
                                    response.put("message", "Redis Cloud is working properly");
                                    response.put("stored_value", testValue);
                                    response.put("retrieved_value", result);
                                    return response;
                                })
                                .switchIfEmpty(Mono.just(createErrorResponse("Failed to retrieve value from Redis")));
                    } else {
                        return Mono.just(createErrorResponse("Failed to store value in Redis"));
                    }
                })
                .onErrorResume(error -> {
                    return Mono.just(createErrorResponse("Redis Cloud connection failed: " + error.getMessage()));
                });
    }

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("message", message);
        return errorResponse;
    }
}