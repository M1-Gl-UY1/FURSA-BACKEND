package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.model.Dividende;

import java.util.List;

public interface DistributionService {
    List<Dividende> distribuer(Long revenuId);
}
