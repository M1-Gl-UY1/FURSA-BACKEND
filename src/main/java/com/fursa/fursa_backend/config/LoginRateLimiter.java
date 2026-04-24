package com.fursa.fursa_backend.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Rate limit simple en memoire sur les tentatives de login, par cle (IP ou email).
 * Limite : 5 tentatives / minute, recharge progressive.
 *
 * Pour un deploiement multi-instance, remplacer par un bucket4j backe par Redis
 * (bucket4j-redis) pour un compteur partage.
 */
@Component
public class LoginRateLimiter {

    private static final Bandwidth LIMIT = Bandwidth.builder()
            .capacity(5)
            .refillIntervally(5, Duration.ofMinutes(1))
            .build();

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean tryConsume(String key) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> Bucket.builder().addLimit(LIMIT).build());
        return bucket.tryConsume(1);
    }
}
