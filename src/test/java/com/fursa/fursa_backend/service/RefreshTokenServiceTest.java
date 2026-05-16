package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.model.RefreshToken;
import com.fursa.fursa_backend.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @InjectMocks private RefreshTokenService refreshTokenService;

    private Investisseur alice;

    @BeforeEach
    void setUp() {
        alice = new Investisseur();
        alice.setId(1L);
        alice.setEmail("alice@example.com");
        // refresh-expiration-time injecte via @Value en prod ; on simule ici (7 jours).
        ReflectionTestUtils.setField(refreshTokenService, "refreshExpirationMs", 604_800_000L);
    }

    @Test
    void issue_creeTokenNonRevoqueAvecExpirationFuture() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> {
            RefreshToken rt = inv.getArgument(0);
            rt.setId(10L);
            return rt;
        });

        RefreshToken rt = refreshTokenService.issue(alice);

        assertNotNull(rt.getToken());
        assertFalse(rt.getToken().isBlank());
        assertEquals(alice, rt.getUser());
        assertFalse(rt.isRevoked());
        assertTrue(rt.getExpiresAt().isAfter(Instant.now()));
    }

    @Test
    void issue_genereTokensUniques() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        RefreshToken a = refreshTokenService.issue(alice);
        RefreshToken b = refreshTokenService.issue(alice);

        assertNotEquals(a.getToken(), b.getToken());
    }

    @Test
    void verify_tokenValide_retourneEntite() {
        RefreshToken rt = creerToken("abc", false, Instant.now().plus(1, ChronoUnit.HOURS));
        when(refreshTokenRepository.findByToken("abc")).thenReturn(Optional.of(rt));

        RefreshToken result = refreshTokenService.verify("abc");

        assertEquals(rt, result);
    }

    @Test
    void verify_tokenInconnu_leveException() {
        when(refreshTokenRepository.findByToken("ghost")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> refreshTokenService.verify("ghost"));
        assertTrue(ex.getMessage().toLowerCase().contains("inconnu"));
    }

    @Test
    void verify_tokenRevoque_leveException() {
        RefreshToken rt = creerToken("rev", true, Instant.now().plus(1, ChronoUnit.HOURS));
        when(refreshTokenRepository.findByToken("rev")).thenReturn(Optional.of(rt));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> refreshTokenService.verify("rev"));
        assertTrue(ex.getMessage().toLowerCase().contains("revoque"));
    }

    @Test
    void verify_tokenExpire_leveException() {
        RefreshToken rt = creerToken("old", false, Instant.now().minus(1, ChronoUnit.MINUTES));
        when(refreshTokenRepository.findByToken("old")).thenReturn(Optional.of(rt));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> refreshTokenService.verify("old"));
        assertTrue(ex.getMessage().toLowerCase().contains("expire"));
    }

    @Test
    void rotate_revoqueAncienEtCreeNouveau() {
        RefreshToken old = creerToken("oldT", false, Instant.now().plus(1, ChronoUnit.HOURS));
        old.setUser(alice);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        RefreshToken rotated = refreshTokenService.rotate(old);

        assertTrue(old.isRevoked(), "ancien token doit etre revoque");
        assertNotEquals(old.getToken(), rotated.getToken());
        assertEquals(alice, rotated.getUser());
        assertFalse(rotated.isRevoked());
        // 1 save pour la revocation + 1 pour l'issue
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void revoke_marqueLeTokenRevoque() {
        RefreshToken rt = creerToken("toKill", false, Instant.now().plus(1, ChronoUnit.HOURS));
        when(refreshTokenRepository.findByToken("toKill")).thenReturn(Optional.of(rt));

        refreshTokenService.revoke("toKill");

        assertTrue(rt.isRevoked());
        verify(refreshTokenRepository).save(rt);
    }

    @Test
    void revoke_tokenInconnu_estIdempotent() {
        when(refreshTokenRepository.findByToken("ghost")).thenReturn(Optional.empty());

        // Ne doit pas lever : logout doit etre tolerant.
        refreshTokenService.revoke("ghost");
    }

    @Test
    void revokeAllForUser_delegueAuRepo() {
        when(refreshTokenRepository.revokeAllForUser(alice)).thenReturn(3);

        refreshTokenService.revokeAllForUser(alice);

        verify(refreshTokenRepository).revokeAllForUser(alice);
    }

    private RefreshToken creerToken(String value, boolean revoked, Instant expiresAt) {
        RefreshToken rt = new RefreshToken();
        rt.setId(1L);
        rt.setToken(value);
        rt.setRevoked(revoked);
        rt.setExpiresAt(expiresAt);
        rt.setCreatedAt(Instant.now());
        return rt;
    }
}
