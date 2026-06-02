package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.model.enumeration.StatutPropriete;
import com.fursa.fursa_backend.model.enumeration.TypeMessage;
import com.fursa.fursa_backend.repository.ProprieteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Worker scheduled qui transforme les proprietes en {@link StatutPropriete#EN_TOKENISATION}
 * en {@link StatutPropriete#PUBLIEE} des que la transaction Ethereum est minee.
 *
 * <p>Resout le bug ou demander le receipt 2s apres le broadcast renvoie result:null
 * (Sepolia block time ~12s, parfois plusieurs minutes). Au lieu de bloquer la requete
 * admin, on fait du polling async toutes les 15 secondes.
 *
 * <p>Behaviour :
 * <ul>
 *   <li>Tick toutes les 15s</li>
 *   <li>Liste les proprietes en EN_TOKENISATION avec un txHash</li>
 *   <li>Pour chacune : essaie de recuperer le receipt</li>
 *   <li>Si receipt OK → enregistre adresseContrat, statut PUBLIEE, notif au proposeur</li>
 *   <li>Si tx echouee → bascule en REFUSEE avec motif, notif au proposeur</li>
 *   <li>Si toujours en mempool → laisse en EN_TOKENISATION pour le prochain tick</li>
 * </ul>
 *
 * <p>Resilient au crash serveur : au redemarrage, le worker reprend toutes les
 * proprietes en EN_TOKENISATION puisque txHash est deja persiste en BDD.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenisationWorker {

    private final ProprieteRepository proprieteRepository;
    private final BlockchainRpcClient blockchainRpcClient;
    private final NotificationService notificationService;
    private final com.fursa.fursa_backend.repository.InvestisseurRepository investisseurRepository;

    /** Fenetre max d'attente : au-dela, on considere la tx perdue et on alerte. */
    private static final long TIMEOUT_MINUTES = 10;

    @Scheduled(fixedDelay = 15_000, initialDelay = 30_000)
    public void pollPendingTokenisations() {
        List<Propriete> enCours = proprieteRepository.findByStatut(StatutPropriete.EN_TOKENISATION);
        if (enCours.isEmpty()) {
            return;
        }
        log.info("Polling tokenisation : {} propriete(s) en attente", enCours.size());
        for (Propriete p : enCours) {
            try {
                checkOne(p);
            } catch (Exception e) {
                log.error("Erreur tokenisation propriete {} : {}", p.getId(), e.getMessage(), e);
                marquerEchec(p, "Erreur RPC : " + e.getMessage());
            }
        }
    }

    @Transactional
    protected void checkOne(Propriete p) throws Exception {
        if (p.getTransactionHash() == null || p.getTransactionHash().isBlank()) {
            log.warn("Propriete {} en EN_TOKENISATION sans txHash, repassage en ACCEPTEE", p.getId());
            p.setStatut(StatutPropriete.ACCEPTEE);
            proprieteRepository.save(p);
            return;
        }

        // Timeout : si la tx traine depuis trop longtemps, on alerte (gas trop bas ?)
        if (p.getSoumiseLe() != null
                && p.getSoumiseLe().isBefore(LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES))) {
            log.warn("Propriete {} en tokenisation depuis > {} min, possible probleme",
                    p.getId(), TIMEOUT_MINUTES);
        }

        Optional<String> contractAddr = blockchainRpcClient
                .getContractAddressIfMined(p.getTransactionHash());

        if (contractAddr.isEmpty()) {
            // Pas encore mine, on retentera au prochain tick
            log.debug("Propriete {} : tx pas encore minee, retry au prochain tick", p.getId());
            return;
        }

        // Mine + adresse OK → on publie
        String addr = contractAddr.get();
        log.info("Propriete {} tokenisee avec succes : contractAddress={}", p.getId(), addr);
        p.setAdresseContrat(addr);
        p.setStatut(StatutPropriete.PUBLIEE);
        Propriete saved = proprieteRepository.save(p);

        // Notif au proposeur + broadcast aux investisseurs
        notifierPublication(saved);
    }

    @Transactional
    protected void marquerEchec(Propriete p, String motif) {
        // On garde le txHash pour debug, on rebascule sur ACCEPTEE pour permettre un retry admin.
        p.setStatut(StatutPropriete.ACCEPTEE);
        p.setMotifRefus("Tokenisation echouee : " + motif);
        proprieteRepository.save(p);
    }

    private void notifierPublication(Propriete p) {
        if (p.getProposeurId() == null) return;
        investisseurRepository.findById(p.getProposeurId()).ifPresent(proposeur -> {
            String lien = "/opportunites/" + p.getId();
            notificationService.envoyer(
                proposeur,
                "Propriete tokenisee et publiee",
                "Votre bien \"" + p.getNom() + "\" est maintenant disponible a l'investissement.",
                TypeMessage.ANNONCE,
                lien
            );
            // Broadcast aux autres investisseurs : nouvelle opportunite dispo
            notificationService.broadcastInvestisseurs(
                "Nouvelle opportunite",
                "« " + p.getNom() + " » vient d'etre publie. Decouvrez-le.",
                TypeMessage.ANNONCE,
                lien,
                proposeur.getId()
            );
        });
    }
}
