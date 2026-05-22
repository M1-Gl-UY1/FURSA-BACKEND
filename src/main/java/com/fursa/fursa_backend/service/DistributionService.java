package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.dto.DistributionPreviewItem;
import com.fursa.fursa_backend.model.Dividende;

import java.util.List;

public interface DistributionService {

    List<Dividende> distribuer(Long revenuId);

    List<Dividende> distribuerViaBlockchain(Long revenuId);

    /**
     * Calcule la repartition prevue sans persister.
     * Sert a l'admin pour visualiser qui va recevoir combien AVANT de declencher la distribution.
     */
    List<DistributionPreviewItem> preview(Long revenuId);
}
