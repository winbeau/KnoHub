package com.knohub.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * CORS configuration for frontend integration
 */
@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origin-patterns:}")
    private String additionalAllowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // Allow local frontend origins on any port (dev/prod previews)
        List<String> allowedOrigins = new ArrayList<>(Arrays.asList(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "http://0.0.0.0:*",
                "http://192.168.*:*",
                "https://localhost:*",
                "https://127.0.0.1:*",
                "https://*.trycloudflare.com"
        ));

        if (StringUtils.hasText(additionalAllowedOrigins)) {
            Arrays.stream(additionalAllowedOrigins.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .forEach(allowedOrigins::add);
        }

        config.setAllowedOriginPatterns(allowedOrigins);

        // Allow all common HTTP methods
        config.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));

        // Allow all headers
        config.setAllowedHeaders(Arrays.asList("*"));

        // Allow credentials (cookies, authorization headers)
        config.setAllowCredentials(true);

        // Expose custom headers to frontend
        config.setExposedHeaders(Arrays.asList(
                "Content-Disposition",
                "X-Total-Count"
        ));

        // Cache preflight response for 1 hour
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);

        return new CorsFilter(source);
    }
}
