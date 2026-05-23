package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.dto.StatutDeclarationResponse;
import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.model.enumeration.Role;
import com.fursa.fursa_backend.model.enumeration.TypeMessage;
import com.fursa.fursa_backend.repository.ProprieteRepository;
import com.fursa.fursa_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * P3 (Hugh 22/05/2026) : notifications automatiques pour la declaration
 * TRIMESTRIELLE des revenus.
 *
 * 3 jobs * 4 trimestres = 12 declenchements annuels :
 *  - Le 1er du mois d'ouverture (janv/avril/juill/oct) a 09h00 : notif proprietaires
 *  - Le 15 du mois d'ouverture a 18h00 : notif rappel retardataires
 *  - Le 16 du mois d'ouverture a 09h00 : notif admin "Voici les biens en retard"
 *
 * Le 1er mois du trimestre N+1 declenche la declaration du trimestre N.
 * Ex : le 1er avril, on ouvre la fenetre pour declarer Q1 (janv-fev-mars).
 *
 * Idempotent : si pour une raison X le job tourne 2 fois, on accepte d'envoyer
 * 2 notifs.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeclarationReminderScheduler {

    private final RevenuService revenuService;
    private final ProprieteRepository proprieteRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    // =========================================================================
    // Job 1 : le 1er des mois d'ouverture (janv/avril/juill/oct) a 09h00
    // -> notif proprietaires que la fenetre trimestrielle est ouverte
    // =========================================================================
    @Scheduled(cron = "0 0 9 1 1,4,7,10 *", zone = "Europe/Paris")
    public void notifierOuvertureFenetre() {
        LocalDate today = LocalDate.now();
        YearQuarter trimestre = DeclarationWindowRules.trimestreADeclarer(today);
        log.info("[P3] Cron ouverture fenetre declaration trimestrielle : {}", trimestre);

        Map<Long, List<Propriete>> parProposeur = proprieteRepository.findAll().stream()
                .filter(p -> p.getProposeurId() != null)
                .collect(Collectors.groupingBy(Propriete::getProposeurId));

        for (var entry : parProposeur.entrySet()) {
            Long proposeurId = entry.getKey();
            Optional<Investisseur> propriOpt = userRepository.findById(proposeurId)
                    .filter(u -> u instanceof Investisseur)
                    .map(u -> (Investisseur) u);
            if (propriOpt.isEmpty()) continue;

            int nbBiens = entry.getValue().size();
            notificationService.envoyer(
                    propriOpt.get(),
                    "Periode de declaration trimestrielle ouverte (" + trimestre + ")",
                    "La fenetre de declaration pour le trimestre " + trimestre
                            + " est ouverte du 1er au 15. Declarez vos revenus pour "
                            + nbBiens + " bien(s) avant le 15 pour eviter la penalite de 300 USD.",
                    TypeMessage.ANNONCE
            );
        }
        log.info("[P3] Notif ouverture envoyee a {} proprietaire(s)", parProposeur.size());
    }

    // =========================================================================
    // Job 2 : le 15 des mois d'ouverture a 18h00 -> rappel retardataires
    // =========================================================================
    @Scheduled(cron = "0 0 18 15 1,4,7,10 *", zone = "Europe/Paris")
    public void rappelDernierJour() {
        log.info("[P3] Cron rappel dernier jour (J-0 fenetre trimestrielle)");
        List<StatutDeclarationResponse> tous = revenuService.statutsTouteLaPlateforme();
        long retardCount = 0;

        for (StatutDeclarationResponse s : tous) {
            if (s.statut() != StatutDeclarationResponse.Statut.DECLARE) {
                Propriete p = proprieteRepository.findById(s.proprieteId()).orElse(null);
                if (p == null || p.getProposeurId() == null) continue;
                Optional<Investisseur> propriOpt = userRepository.findById(p.getProposeurId())
                        .filter(u -> u instanceof Investisseur)
                        .map(u -> (Investisseur) u);
                if (propriOpt.isEmpty()) continue;

                notificationService.envoyer(
                        propriOpt.get(),
                        "Derniere chance : declaration trimestrielle avant minuit",
                        "Le bien \"" + s.proprieteNom() + "\" n'a toujours pas ete declare pour "
                                + s.moisADeclarer() + ". Apres minuit, une penalite de 300 USD sera appliquee.",
                        TypeMessage.AVERTISSEMENT
                );
                retardCount++;
            }
        }
        log.info("[P3] Rappel dernier jour envoye pour {} declaration(s) en attente", retardCount);
    }

    // =========================================================================
    // Job 3 : le 16 des mois d'ouverture a 09h00 -> rapport admin retardataires
    // =========================================================================
    @Scheduled(cron = "0 0 9 16 1,4,7,10 *", zone = "Europe/Paris")
    public void rapportRetardsPourAdmin() {
        log.info("[P3] Cron rapport retards aux admins (J+1 apres fermeture)");
        List<StatutDeclarationResponse> tous = revenuService.statutsTouteLaPlateforme();
        List<StatutDeclarationResponse> enRetard = tous.stream()
                .filter(s -> s.statut() == StatutDeclarationResponse.Statut.EN_RETARD)
                .toList();

        if (enRetard.isEmpty()) {
            log.info("[P3] Aucun retard a signaler aux admins.");
            return;
        }

        String resume = enRetard.size() + " propriete(s) n'ont pas declare leurs revenus "
                + "pour " + enRetard.get(0).moisADeclarer() + " : "
                + enRetard.stream().map(StatutDeclarationResponse::proprieteNom).limit(5)
                        .collect(Collectors.joining(", "))
                + (enRetard.size() > 5 ? "..." : "");

        userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN && u instanceof Investisseur)
                .forEach(u -> notificationService.envoyer(
                        (Investisseur) u,
                        "Retards de declaration trimestrielle",
                        resume,
                        TypeMessage.AVERTISSEMENT
                ));
        log.info("[P3] Rapport retards envoye aux admins ({} retards)", enRetard.size());
    }
}
