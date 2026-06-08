package com.anupam.reminiscence.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.*;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;   // <-- correct import

import java.util.List;

/**
 * Central CORS configuration.
 *
 * Spring‑Security will automatically pick up the {@link CorsConfigurationSource}
 * bean defined here (via {@code http.cors(...)}).  No separate {@code CorsFilter}
 * is required for the API endpoints.
 */
@Configuration
public class CorsConfig {

    /** Allowed origins for dev and production */
    private static final List<String> ALLOWED_ORIGINS = List.of(
            "http://localhost:5173",
            "https://reminiscence-react.vercel.app",
            "https://reminiscence-prototype.vercel.app/",
            "https://localhost",
            "http://localhost",
            "https://anupam.com",
            "https://reminiscence.com",
            "capacitor://localhost",
            "ionic://localhost"
    );

    /** Core CORS rules used by Spring‑Security */
    @Bean
    public CorsConfiguration corsConfiguration() {
        CorsConfiguration cfg = new CorsConfiguration();
        // exact allowed origins
        // also accept as patterns (covers exact matches)
        cfg.setAllowedOrigins(ALLOWED_ORIGINS);
        cfg.setAllowedOriginPatterns(ALLOWED_ORIGINS);
        cfg.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setExposedHeaders(List.of(
                "X-Timezone", "Authorization", "Content-Disposition"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);
        return cfg;
    }

    /** Bean that Spring‑Security looks for when you call {@code http.cors(...)} */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource(); // <-- correct class
        // Apply the same CORS rules to every endpoint under /api/**
        source.registerCorsConfiguration("/api/**", corsConfiguration());
        return source;   // this object **does** implement CorsConfigurationSource
    }

    // ----------------------------------------------------------------------
    // OPTIONAL – keep a standalone CorsFilter only if you need it elsewhere.
    // Not required for the security‑chain based CORS handling.
    // ----------------------------------------------------------------------
    // @Bean
    // public CorsFilter corsFilter() {
    //     return new CorsFilter(corsConfigurationSource());
    // }
}