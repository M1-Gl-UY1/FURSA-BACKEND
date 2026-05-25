package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.model.enumeration.RaisonRecalculPrix;
import com.fursa.fursa_backend.model.enumeration.StatutPropriete;
import com.fursa.fursa_backend.repository.ProprieteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * P1 (Hugh 22/05/2026) : filet de securite trimestriel pour le prix dynamique.
 *
 * Voir PRIX_DYNAMIQUE_FURSA.md §6.1 :
 *   "Si pour une raison quelconque aucun hook ne s'est declenche, le cron du
 *    16 du mois d'ouverture du trimestre suivant garantit qu'au moins un
 *    snapshot est cree par trimestre pour chaque propriete publiee."
 *
 * Le 16 (janv/avril/juill/oct) a 10h00, on declenche un snapshot
 * CRON_TRIMESTRIEL sur toutes les proprietes PUBLIEE ou FINANCEE.
 *
 * Idempotent par jour : meme si le job tourne plusieurs fois la meme journee,
 * chaque appel produira un snapshot. Pas un drame, mais on accepte le bruit.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PrixPartScheduler {

    private final ProprieteRepository proprieteRepository;
    private final PrixPartService prixPartService;

    @Scheduled(cron = "0 0 10 16 1,4,7,10 *", zone = "Europe/Paris")
    public void snapshotTrimestriel() {
        log.info("[PrixPart] Cron snapshot trimestriel : recalcul de toutes les proprietes actives");

        // Un bien dont l'escrow est passe a FINANCEE garde son statut propriete a PUBLIEE.
        // On couvre donc les deux cas en filtrant uniquement sur PUBLIEE.
        List<Propriete> actives = proprieteRepository.findAll().stream()
                .filter(p -> p.getStatut() == StatutPropriete.PUBLIEE)
                .toList();

        int ok = 0;
        int errs = 0;
        for (Propriete p : actives) {
            try {
                prixPartService.recalculer(p.getId(), RaisonRecalculPrix.CRON_TRIMESTRIEL, null);
                ok++;
            } catch (Exception e) {
                log.error("[PrixPart] Echec recalcul cron pour prop={} : {}", p.getId(), e.getMessage());
                errs++;
            }
        }
        log.info("[PrixPart] Cron snapshot trimestriel termine : {} ok / {} erreurs", ok, errs);
    }
}
