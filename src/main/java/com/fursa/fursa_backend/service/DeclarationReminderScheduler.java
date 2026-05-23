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
 * Phase 10b : envoie automatiquement les notifications de declaration mensuelle.
 *
 * 3 jobs :
 *  - Le 1er du mois 09h00 : notif aux proprietaires "La fenetre est ouverte"
 *  - Le 5 du mois  18h00 : notif rappel aux retardataires
 *  - Le 6 du mois  09h00 : notif admin "Voici les proprietes en retard"
 *
 * Idempotent : si pour une raison X le job tourne 2 fois, on accepte d'envoyer
 * 2 notifs. Une dedup plus stricte (table envois) viendra en V2 si besoin.
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
    // Job 1 : le 1er du mois a 09h00 -> proprietaires
    // =========================================================================
    @Scheduled(cron = "0 0 9 1 * *", zone = "Europe/Paris")
    public void notifierOuvertureFenetre() {
        LocalDate today = LocalDate.now();
        log.info("[Phase 10b] Cron ouverture fenetre declaration : {}", today);

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
                    "Periode de declaration ouverte",
                    "La fenetre de declaration mensuelle est ouverte du 1er au 5. "
                            + "Declarez vos revenus pour " + nbBiens
                            + " bien(s) avant le 5 pour eviter la penalite de 300 EUR.",
                    TypeMessage.ANNONCE
            );
        }
        log.info("[Phase 10b] Notif ouverture envoyee a {} proprietaire(s)", parProposeur.size());
    }

    // =========================================================================
    // Job 2 : le 5 du mois a 18h00 -> rappel retardataires
    // =========================================================================
    @Scheduled(cron = "0 0 18 5 * *", zone = "Europe/Paris")
    public void rappelDernierJour() {
        log.info("[Phase 10b] Cron rappel dernier jour (J-0 fenetre)");
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
                        "Derniere chance : declaration avant minuit",
                        "Le bien \"" + s.proprieteNom() + "\" n'a toujours pas ete declare pour "
                                + s.moisADeclarer() + ". Apres minuit, une penalite de 300 EUR sera appliquee.",
                        TypeMessage.AVERTISSEMENT
                );
                retardCount++;
            }
        }
        log.info("[Phase 10b] Rappel dernier jour envoye pour {} declaration(s) en attente", retardCount);
    }

    // =========================================================================
    // Job 3 : le 6 du mois a 09h00 -> notif admin des retardataires
    // =========================================================================
    @Scheduled(cron = "0 0 9 6 * *", zone = "Europe/Paris")
    public void rapportRetardsPourAdmin() {
        log.info("[Phase 10b] Cron rapport retards aux admins (J+1 apres fermeture)");
        List<StatutDeclarationResponse> tous = revenuService.statutsTouteLaPlateforme();
        List<StatutDeclarationResponse> enRetard = tous.stream()
                .filter(s -> s.statut() == StatutDeclarationResponse.Statut.EN_RETARD)
                .toList();

        if (enRetard.isEmpty()) {
            log.info("[Phase 10b] Aucun retard a signaler aux admins.");
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
                        "Retards de declaration mensuelle",
                        resume,
                        TypeMessage.AVERTISSEMENT
                ));
        log.info("[Phase 10b] Rapport retards envoye aux admins ({} retards)", enRetard.size());
    }
}
