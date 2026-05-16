package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.model.RefreshToken;
import com.fursa.fursa_backend.model.User;
import com.fursa.fursa_backend.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

/**
 * Gere le cycle de vie des refresh tokens (Facebook-style).
 *
 * - issue : cree un nouveau refresh token apres un login.
 * - rotate : a chaque /refresh, on revoque l'ancien et on en cree un nouveau (sliding window).
 * - revoke : pour /logout.
 *
 * Pattern : access token JWT court (1h) + refresh token UUID long (7 jours), revocable.
 * Stocker le refresh token en DB nous permet de le revoquer (vrai logout serveur).
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom RNG = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.refresh-expiration-time}")
    private long refreshExpirationMs;

    @Transactional
    public RefreshToken issue(User user) {
        RefreshToken rt = new RefreshToken();
        rt.setToken(generateOpaqueToken());
        rt.setUser(user);
        rt.setExpiresAt(Instant.now().plus(refreshExpirationMs, ChronoUnit.MILLIS));
        rt.setRevoked(false);
        return refreshTokenRepository.save(rt);
    }

    /**
     * Verifie un refresh token et retourne l'entite si elle est valide.
     * Throws IllegalArgumentException si invalide, expire ou revoque.
     */
    @Transactional(readOnly = true)
    public RefreshToken verify(String token) {
        RefreshToken rt = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Refresh token inconnu"));
        if (rt.isRevoked()) {
            throw new IllegalArgumentException("Refresh token revoque");
        }
        if (rt.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Refresh token expire");
        }
        return rt;
    }

    /**
     * Rotation : revoque l'ancien token et en cree un nouveau pour le meme user.
     * Standard de securite : un refresh token = un seul usage.
     */
    @Transactional
    public RefreshToken rotate(RefreshToken oldToken) {
        oldToken.setRevoked(true);
        refreshTokenRepository.save(oldToken);
        return issue(oldToken.getUser());
    }

    @Transactional
    public void revoke(String token) {
        refreshTokenRepository.findByToken(token).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        });
    }

    @Transactional
    public void revokeAllForUser(User user) {
        refreshTokenRepository.revokeAllForUser(user);
    }

    public long getRefreshExpirationMs() {
        return refreshExpirationMs;
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        RNG.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }
}
