package com.fursa.fursa_backend.config;

import com.fursa.fursa_backend.filter.JwtFilter;
import com.fursa.fursa_backend.service.CustomUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final CustomUserService customUserService;
    private final JwtUtils jwtUtils;

    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http, PasswordEncoder passwordEncoder) throws Exception{
        AuthenticationManagerBuilder authenticationManagerBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
        authenticationManagerBuilder.userDetailsService(customUserService).passwordEncoder(passwordEncoder);
        return authenticationManagerBuilder.build();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource) throws Exception{
        return http
                .cors(c -> c.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(
                        org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth->
                        auth.requestMatchers(
                                "/api/user/auth/*",
                                "/api/health",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/actuator/prometheus",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/api/fichiers/*",
                                "/api/webhooks/**"
                        ).permitAll()
                                // Catalogue public : un visiteur non connecte doit pouvoir
                                // voir les biens sur la landing + le catalogue + une fiche.
                                // Fix 29/05/2026 : ces endpoints renvoyaient 403 -> aucune
                                // propriete affichee sur la landing pour les visiteurs.
                                .requestMatchers(HttpMethod.GET,
                                        "/api/proprietes/public",
                                        "/api/proprietes/public/**",
                                        "/api/proprietes/*/historique-prix",
                                        "/api/partenaires-gestion",
                                        // V2 G.1 (04/06/2026) : liste des
                                        // equipements lisible publiquement
                                        // (wizard non-auth + catalogue). Le
                                        // CRUD /admin reste protege.
                                        "/api/equipements",
                                        // V2 H.4 (06/06/2026) : settings
                                        // publics whitelistes (limites
                                        // fichiers, age KYC) pour rendre la
                                        // validation frontend dynamique.
                                        "/api/app-settings/public"
                                ).permitAll()
                                .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtFilter(customUserService, jwtUtils), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
