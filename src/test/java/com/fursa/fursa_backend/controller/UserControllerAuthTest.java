package com.fursa.fursa_backend.controller;

import tools.jackson.databind.ObjectMapper;

import com.fursa.fursa_backend.config.JwtUtils;
import com.fursa.fursa_backend.config.LoginRateLimiter;
import com.fursa.fursa_backend.config.SecurityConfig;
import com.fursa.fursa_backend.dto.LoginRequest;
import com.fursa.fursa_backend.dto.RefreshRequest;
import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.model.RefreshToken;
import com.fursa.fursa_backend.model.enumeration.Role;
import com.fursa.fursa_backend.repository.UserRepository;
import com.fursa.fursa_backend.service.AuthenticatedInvestisseurService;
import com.fursa.fursa_backend.service.CustomUserService;
import com.fursa.fursa_backend.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerAuthTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private UserRepository userRepository;
    @MockitoBean private PasswordEncoder passwordEncoder;
    @MockitoBean private JwtUtils jwtUtils;
    @MockitoBean private AuthenticationManager authenticationManager;
    @MockitoBean private AuthenticatedInvestisseurService authInvestisseur;
    @MockitoBean private LoginRateLimiter loginRateLimiter;
    @MockitoBean private RefreshTokenService refreshTokenService;
    @MockitoBean private CustomUserService customUserService;

    private Investisseur alice;

    @BeforeEach
    void setUp() {
        alice = new Investisseur();
        alice.setId(1L);
        alice.setEmail("alice@example.com");
        alice.setPassword("hash");
        alice.setRole(Role.INVESTISSEUR);

        when(loginRateLimiter.tryConsume(any())).thenReturn(true);
        when(jwtUtils.getExpirationMs()).thenReturn(3_600_000L);
    }

    // ── /auth/login ──────────────────────────────────────────────────────────

    @Test
    void login_succes_retourneAccessEtRefreshToken() throws Exception {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(alice, "pwd", alice.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(jwtUtils.generateToken("alice@example.com")).thenReturn("ACCESS_JWT");

        RefreshToken rt = creerRefreshToken("REFRESH_OPAQUE");
        when(refreshTokenService.issue(alice)).thenReturn(rt);

        LoginRequest body = new LoginRequest("alice@example.com", "pwd");

        mockMvc.perform(post("/api/user/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("ACCESS_JWT"))
                .andExpect(jsonPath("$.refreshToken").value("REFRESH_OPAQUE"))
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    void login_mauvaisIdentifiants_retourne401() throws Exception {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new org.springframework.security.authentication.BadCredentialsException("bad"));

        LoginRequest body = new LoginRequest("alice@example.com", "wrong");

        mockMvc.perform(post("/api/user/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Identifiants invalides"));
    }

    @Test
    void login_rateLimit_retourne429() throws Exception {
        when(loginRateLimiter.tryConsume(any())).thenReturn(false);

        LoginRequest body = new LoginRequest("alice@example.com", "pwd");

        mockMvc.perform(post("/api/user/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isTooManyRequests());
    }

    // ── /auth/refresh ────────────────────────────────────────────────────────

    @Test
    void refresh_tokenValide_retourneNouvellePaire() throws Exception {
        RefreshToken old = creerRefreshToken("OLD_REFRESH");
        old.setUser(alice);
        RefreshToken rotated = creerRefreshToken("NEW_REFRESH");
        rotated.setUser(alice);

        when(refreshTokenService.verify("OLD_REFRESH")).thenReturn(old);
        when(refreshTokenService.rotate(old)).thenReturn(rotated);
        when(jwtUtils.generateToken("alice@example.com")).thenReturn("NEW_ACCESS_JWT");

        RefreshRequest body = new RefreshRequest("OLD_REFRESH");

        mockMvc.perform(post("/api/user/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("NEW_ACCESS_JWT"))
                .andExpect(jsonPath("$.refreshToken").value("NEW_REFRESH"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    void refresh_tokenInvalide_retourne401() throws Exception {
        when(refreshTokenService.verify("BAD"))
                .thenThrow(new IllegalArgumentException("Refresh token inconnu"));

        RefreshRequest body = new RefreshRequest("BAD");

        mockMvc.perform(post("/api/user/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token inconnu"));
    }

    @Test
    void refresh_tokenExpire_retourne401() throws Exception {
        when(refreshTokenService.verify("EXPIRED"))
                .thenThrow(new IllegalArgumentException("Refresh token expire"));

        RefreshRequest body = new RefreshRequest("EXPIRED");

        mockMvc.perform(post("/api/user/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_corpsVide_retourne400() throws Exception {
        RefreshRequest body = new RefreshRequest("");

        mockMvc.perform(post("/api/user/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isBadRequest());
    }

    // ── /auth/logout ─────────────────────────────────────────────────────────

    @Test
    void logout_revoqueRefreshToken_retourne204() throws Exception {
        RefreshRequest body = new RefreshRequest("TO_REVOKE");

        mockMvc.perform(post("/api/user/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isNoContent());

        verify(refreshTokenService).revoke("TO_REVOKE");
    }

    @Test
    void logout_tokenInconnu_resteIdempotent_204() throws Exception {
        // Service ne leve pas pour token inconnu : on s'assure que le contrat reste 204.
        RefreshRequest body = new RefreshRequest("GHOST");

        mockMvc.perform(post("/api/user/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isNoContent());
    }

    private RefreshToken creerRefreshToken(String value) {
        RefreshToken rt = new RefreshToken();
        rt.setId(1L);
        rt.setToken(value);
        rt.setRevoked(false);
        rt.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        rt.setCreatedAt(Instant.now());
        return rt;
    }
}
