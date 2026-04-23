package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.dto.DashboardAdminResponse;
import com.fursa.fursa_backend.dto.DashboardInvestisseurResponse;
import com.fursa.fursa_backend.model.Dividende;
import com.fursa.fursa_backend.model.Paiement;
import com.fursa.fursa_backend.model.Possession;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.model.enumeration.StatutAnnonce;
import com.fursa.fursa_backend.model.enumeration.StatutPaiement;
import com.fursa.fursa_backend.model.enumeration.StatutPropriete;
import com.fursa.fursa_backend.repository.AnnonceRepository;
import com.fursa.fursa_backend.repository.DividendeRepository;
import com.fursa.fursa_backend.repository.InvestisseurRepository;
import com.fursa.fursa_backend.repository.NotificationRepository;
import com.fursa.fursa_backend.repository.PaiementRepository;
import com.fursa.fursa_backend.repository.PossessionRepository;
import com.fursa.fursa_backend.repository.ProprieteRepository;
import com.fursa.fursa_backend.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final PossessionRepository possessionRepository;
    private final PaiementRepository paiementRepository;
    private final DividendeRepository dividendeRepository;
    private final AnnonceRepository annonceRepository;
    private final NotificationRepository notificationRepository;
    private final ProprieteRepository proprieteRepository;
    private final InvestisseurRepository investisseurRepository;
    private final TransactionRepository transactionRepository;

    public DashboardInvestisseurResponse pourInvestisseur(Long investisseurId) {
        List<Possession> possessions = possessionRepository.findByInvestisseurId(investisseurId);

        int totalParts = possessions.stream().mapToInt(p ->
                p.getNombreDeParts() == null ? 0 : p.getNombreDeParts()).sum();

        BigDecimal valeurPortefeuille = possessions.stream()
                .map(p -> {
                    Propriete prop = p.getPropriete();
                    if (prop == null || prop.getPrixUnitairePart() == null) return BigDecimal.ZERO;
                    return prop.getPrixUnitairePart().multiply(BigDecimal.valueOf(p.getNombreDeParts()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal revenusAnnuelsPrevus = possessions.stream()
                .map(p -> {
                    Propriete prop = p.getPropriete();
                    if (prop == null || prop.getPrixUnitairePart() == null || prop.getRentabilitePrevue() == null) {
                        return BigDecimal.ZERO;
                    }
                    BigDecimal valeurPosition = prop.getPrixUnitairePart().multiply(BigDecimal.valueOf(p.getNombreDeParts()));
                    return valeurPosition.multiply(BigDecimal.valueOf(prop.getRentabilitePrevue()))
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalInvesti = paiementRepository.findByInvestisseurId(investisseurId).stream()
                .filter(p -> p.getStatut() == StatutPaiement.VALIDE)
                .map(Paiement::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDividendes = dividendeRepository.findByInvestisseurId(investisseurId).stream()
                .map(Dividende::getMontantCalcule)
                .filter(m -> m != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int nbAnnoncesOuvertes = (int) annonceRepository.findByInvestisseurId(investisseurId).stream()
                .filter(a -> a.getStatut() == StatutAnnonce.OUVERTE)
                .count();

        int nbNotifsNonLues = notificationRepository
                .findByDestinataireIdAndLuFalseOrderByDateDesc(investisseurId).size();

        return new DashboardInvestisseurResponse(
                investisseurId,
                (int) possessions.stream().map(Possession::getPropriete).distinct().count(),
                totalParts,
                totalInvesti,
                valeurPortefeuille,
                totalDividendes,
                revenusAnnuelsPrevus,
                nbAnnoncesOuvertes,
                nbNotifsNonLues
        );
    }

    public DashboardAdminResponse global() {
        long nbInvestisseurs = investisseurRepository.count();
        long nbProprietes = proprieteRepository.count();
        long nbPubliees = proprieteRepository.findByStatut(StatutPropriete.PUBLIEE).size();

        int totalEmises = proprieteRepository.findAll().stream()
                .mapToInt(p -> p.getNombreTotalPart() == null ? 0 : p.getNombreTotalPart())
                .sum();

        int totalVendues = possessionRepository.findAll().stream()
                .mapToInt(p -> p.getNombreDeParts() == null ? 0 : p.getNombreDeParts())
                .sum();

        BigDecimal volumeTx = transactionRepository.findAll().stream()
                .map(t -> t.getMontant() == null ? BigDecimal.ZERO : t.getMontant())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDiv = dividendeRepository.findAll().stream()
                .map(Dividende::getMontantCalcule)
                .filter(m -> m != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long nbAnnoncesOuvertes = annonceRepository.findByStatut(StatutAnnonce.OUVERTE).size();

        return new DashboardAdminResponse(
                nbInvestisseurs, nbProprietes, nbPubliees,
                totalEmises, totalVendues, volumeTx, totalDiv, nbAnnoncesOuvertes);
    }
}
