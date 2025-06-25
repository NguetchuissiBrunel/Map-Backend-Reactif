package com.example.Mp_Reactif.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();

        // Origines autorisées (remplacez par vos domaines spécifiques en production)
        corsConfiguration.setAllowedOriginPatterns(Arrays.asList("*"));

        // Méthodes HTTP autorisées
        corsConfiguration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // Headers autorisés
        corsConfiguration.setAllowedHeaders(Arrays.asList("*"));

        // Permettre les credentials (cookies, authorization headers)
        corsConfiguration.setAllowCredentials(true);

        // Durée de cache pour les requêtes preflight
        corsConfiguration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", corsConfiguration);

        return new CorsWebFilter(source);
    }
}