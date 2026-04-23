package com.fursa.fursa_backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI fursaOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FURSA Backend API")
                        .version("1.0.0")
                        .description("""
                                API REST de FURSA Community - plateforme d'investissement immobilier fractionne en Afrique.

                                ## Modules
                                - **Authentification** : register/login, JWT, Spring Security
                                - **Catalogue & Fichiers** : CRUD Propriete, upload documents
                                - **Marche primaire** : achat de parts (intention + transaction simulee + possession)
                                - **Marche secondaire** : annonces de revente, achat entre investisseurs, notifications
                                - **Distribution** : calcul des dividendes au prorata des possessions

                                ## Authentification
                                La plupart des endpoints necessitent un JWT. Recuperer un token via `POST /api/user/auth/login`, puis cliquer sur **Authorize** (en haut a droite) et coller le token (sans le prefixe `Bearer`).
                                """)
                        .contact(new Contact()
                                .name("Equipe FURSA - UY1 M1")
                                .url("https://github.com/M1-Gl-UY1/FURSA-BACKEND"))
                        .license(new License().name("Usage academique - UE Projet M1")))
                .servers(List.of(
                        new Server().url("https://api.fursas.duckdns.org").description("Production (VPS Contabo)"),
                        new Server().url("http://localhost:8081").description("Local")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT obtenu via POST /api/user/auth/login")));
    }
}
