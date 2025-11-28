package com.example.Mp_Reactif.service;

import com.example.Mp_Reactif.model.Point;
import com.example.Mp_Reactif.model.RouteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AdaptiveRedisCacheService {

    private static final Logger logger = LoggerFactory.getLogger(AdaptiveRedisCacheService.class);

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final Map<String, AtomicInteger> usageStatistics = new ConcurrentHashMap<>();
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private boolean redisAvailable = true;

    public AdaptiveRedisCacheService(ReactiveRedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        testConnection();
    }

    public String generatePlacesKey(String searchTerm) {
        String key = "p:" + normalizeKey(searchTerm);
        return key.length() > 80 ? key.substring(0, 80) : key;
    }

    public String generateRouteKey(Point start, Point end, String mode) {
        return String.format("r:%.4f:%.4f:%.4f:%.4f:%s",
                start.getLat(), start.getLng(), end.getLat(), end.getLng(),
                getModeCode(mode));
    }

    public Duration getIntelligentTTL(String key, Object data) {
        int usageCount = getUsageCount(key);

        if (key.startsWith("p:")) {
            if (usageCount > 10) return Duration.ofHours(24);
            if (usageCount > 5) return Duration.ofHours(12);
            if (usageCount > 2) return Duration.ofHours(6);
            return Duration.ofHours(2);

        } else if (key.startsWith("r:")) {
            double distance = extractDistance(data);

            if (usageCount > 15) {
                return Duration.ofHours(72);
            } else if (usageCount > 8) {
                return Duration.ofHours(48);
            } else if (distance > 100) {
                return Duration.ofHours(48);
            } else if (distance > 50) {
                return Duration.ofHours(24);
            } else if (distance > 20) {
                return Duration.ofHours(12);
            } else {
                return Duration.ofHours(6);
            }
        }

        return Duration.ofHours(1);
    }

    private double extractDistance(Object data) {
        try {
            if (data instanceof RouteResponse) {
                RouteResponse response = (RouteResponse) data;
                if (response.getRoutes() != null && !response.getRoutes().isEmpty()) {
                    return response.getRoutes().get(0).getDistance();
                }
            }
        } catch (Exception e) {
            logger.debug("Impossible d'extraire la distance");
        }
        return 0.0;
    }

    private int getUsageCount(String key) {
        AtomicInteger counter = usageStatistics.get(key);
        return counter != null ? counter.get() : 0;
    }

    private void recordUsage(String key) {
        usageStatistics.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
    }

    public <T> Mono<T> get(String key, Class<T> type) {
        if (!redisAvailable) {
            cacheMisses.incrementAndGet();
            return Mono.empty();
        }

        return redisTemplate.opsForValue()
                .get(key)
                .flatMap(result -> {
                    try {
                        if (result != null) {
                            // Conversion sûre
                            T typedResult = type.cast(result);
                            cacheHits.incrementAndGet();
                            recordUsage(key);
                            logger.debug("✅ CACHE HIT: {} (usage: {})",
                                    getShortKey(key), getUsageCount(key));
                            return Mono.just(typedResult);
                        } else {
                            cacheMisses.incrementAndGet();
                            return Mono.empty();
                        }
                    } catch (ClassCastException e) {
                        logger.warn("⚠️ Type mismatch in cache for key {}: {}", key, e.getMessage());
                        cacheMisses.incrementAndGet();
                        return Mono.empty();
                    }
                })
                .onErrorResume(error -> {
                    logger.error("❌ Cache get error for {}: {}", key, error.getMessage());
                    cacheMisses.incrementAndGet();
                    return Mono.empty();
                });
    }

    public Mono<Boolean> set(String key, Object value) {
        return set(key, value, Duration.ofHours(1));
    }

    public Mono<Boolean> set(String key, Object value, Duration ttl) {
        if (!redisAvailable) {
            return Mono.just(false);
        }

        Duration intelligentTtl = getIntelligentTTL(key, value);

        return redisTemplate.opsForValue()
                .set(key, value, intelligentTtl)
                .doOnSuccess(success -> {
                    if (success) {
                        logger.debug("💾 CACHE SET: {} (TTL: {}h)",
                                getShortKey(key), intelligentTtl.toHours());
                    }
                })
                .onErrorResume(error -> {
                    logger.warn("Erreur cache set: {}", error.getMessage());
                    return Mono.just(false);
                });
    }

    private String getModeCode(String mode) {
        if (mode == null) return "d";
        return switch (mode) {
            case "walking" -> "w";
            case "cycling" -> "c";
            default -> "d";
        };
    }

    private String normalizeKey(String input) {
        if (input == null) return "";
        return input.toLowerCase()
                .replace(" ", "_")
                .replaceAll("[^a-z0-9_]", "")
                .trim();
    }

    private String getShortKey(String key) {
        return key.length() > 30 ? key.substring(0, 30) + "..." : key;
    }

    private void testConnection() {
        System.out.println("🔍 Début du test de connexion Redis...");
        System.out.println("📍 Host: " + "redis-13330.c341.af-south-1-1.ec2.cloud.redislabs.com");
        System.out.println("🚪 Port: 13330");

        String testKey = "health-check-rediscloud";
        String testValue = "test-" + System.currentTimeMillis();

        redisTemplate.opsForValue().set(testKey, testValue, Duration.ofSeconds(30))
                .timeout(Duration.ofSeconds(10)) // Timeout explicite
                .doOnSuccess(success -> {
                    System.out.println("📝 Résultat SET: " + success);
                    redisAvailable = success;
                    if (success) {
                        System.out.println("✅ Redis Cloud: SET réussi");

                        // Vérification de la lecture
                        redisTemplate.opsForValue().get(testKey)
                                .timeout(Duration.ofSeconds(5))
                                .doOnNext(retrieved -> {
                                    System.out.println("✅ Redis Cloud: GET réussi - " + retrieved);
                                })
                                .doOnError(error -> {
                                    System.out.println("❌ Redis Cloud: GET échoué - " + error.getMessage());
                                })
                                .subscribe();
                    } else {
                        System.out.println("❌ Redis Cloud: SET a échoué (sans erreur)");
                    }
                })
                .doOnError(error -> {
                    redisAvailable = false;
                    System.out.println("💥 Erreur de connexion Redis:");
                    System.out.println("💥 Type: " + error.getClass().getSimpleName());
                    System.out.println("💥 Message: " + error.getMessage());

                    if (error.getMessage().contains("SSL")) {
                        System.out.println("🔧 Conseil: Problème SSL détecté");
                    }
                    if (error.getMessage().contains("auth")) {
                        System.out.println("🔧 Conseil: Problème d'authentification");
                    }
                    if (error.getMessage().contains("connection refused")) {
                        System.out.println("🔧 Conseil: Connexion refusée - vérifiez host/port");
                    }
                })
                .subscribe();
    }

    public Mono<Map<String, Object>> getCacheStats() {
        return redisTemplate.keys("*")
                .collectList()
                .flatMap(keys -> {
                    long totalRequests = cacheHits.get() + cacheMisses.get();
                    double hitRatio = totalRequests > 0 ?
                            (double) cacheHits.get() / totalRequests * 100 : 0;

                    long routesCount = keys.stream().filter(k -> k.startsWith("r:")).count();
                    long placesCount = keys.stream().filter(k -> k.startsWith("p:")).count();

                    Map<String, Object> stats = new HashMap<>();
                    stats.put("total_keys", keys.size());
                    stats.put("cache_hits", cacheHits.get());
                    stats.put("cache_misses", cacheMisses.get());
                    stats.put("hit_ratio", String.format("%.1f%%", hitRatio));
                    stats.put("redis_available", redisAvailable);
                    stats.put("tracked_keys", usageStatistics.size());
                    stats.put("cached_routes", routesCount);
                    stats.put("cached_places", placesCount);

                    return Mono.just(stats);
                })
                .onErrorResume(error -> {
                    Map<String, Object> errorStats = new HashMap<>();
                    errorStats.put("error", "Unable to get stats: " + error.getMessage());
                    return Mono.just(errorStats);
                });
    }

    public boolean isRedisAvailable() {
        return redisAvailable;
    }
}