// RevenueObserver.java - Corrigé
package com.fursa.fursa_backend.feature.observer;

import com.fursa.fursa_backend.service.RevenueTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RevenueObserver implements Observer {

    private final RevenueTrackingService revenueTrackingService;

    @Override
    public void update(String event, Object data) {
        if ("DIVIDEND_DISTRIBUTED".equals(event)) {
            revenueTrackingService.trackDividendDistribution(data);
        } else if ("PART_SOLD".equals(event)) {
            revenueTrackingService.updatePortfolioValue(data);
        }
    }
}
