package com.fursa.fursa_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration CORS robuste pour Fursa.
 *
 * Pattern utilisé : CorsFilter enregistré avec {@code Ordered.HIGHEST_PRECEDENCE}.
 * Cela garantit que les headers CORS (Access-Control-Allow-Origin, etc.) sont
 * ajoutés à TOUTES les réponses, y compris les erreurs renvoyées par :
 *   - Spring Security (401, 403)
 *   - Le parser multipart (413, 415, MultipartException)
 *   - GlobalExceptionHandler (400, 404, 409, 500)
 *
 * Sans ce filtre, une exception levée AVANT que la chaîne de filtres Spring MVC
 * arrive jusqu'au filtre CORS produit une réponse SANS header CORS, ce qui apparaît
 * côté client comme "Access-Control-Allow-Origin missing" alors que la vraie
 * cause est ailleurs (token expiré, fichier trop gros, etc.).
 *
 * Référence : https://docs.spring.io/spring-framework/reference/web/webmvc-cors.html#mvc-cors-filter
 */
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin",
                "X-Requested-With", "X-Idempotency-Key"));
        config.setExposedHeaders(List.of("Location", "X-Total-Count"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * CorsFilter au niveau le plus haut de la chaîne pour que les headers CORS
     * soient écrits AVANT que les exceptions Spring Security / multipart / metier
     * ne soient levées. Sans ça, une 401/415/500 renvoie une réponse sans
     * Access-Control-Allow-Origin → message "CORS policy" trompeur côté browser.
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration(
            UrlBasedCorsConfigurationSource corsConfigurationSource) {
        FilterRegistrationBean<CorsFilter> bean =
                new FilterRegistrationBean<>(new CorsFilter(corsConfigurationSource));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}
