package com.fursa.fursa_backend.payment;

import java.time.LocalDateTime;

public record ProviderSessionResponse(
        String externalId,
        String widgetUrl,
        LocalDateTime expiresAt
) {}
