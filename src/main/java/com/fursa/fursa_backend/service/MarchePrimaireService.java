package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.dto.AchatRequest;
import com.fursa.fursa_backend.dto.AchatResponse;
import com.fursa.fursa_backend.model.*;
import com.fursa.fursa_backend.model.enumeration.StatutPaiement;
import com.fursa.fursa_backend.model.enumeration.StatutPropriete;
import com.fursa.fursa_backend.model.enumeration.StatutTransaction;
import com.fursa.fursa_backend.model.enumeration.TypePaiement;
import com.fursa.fursa_backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class MarchePrimaireService {

    private final PaiementRepository paiementRepository;
    private final TransactionRepository transactionRepository;
    private final PossessionRepository possessionRepository;
    private final ProprieteRepository proprieteRepository;
    private final InvestisseurRepository investisseurRepository;

    public MarchePrimaireService(PaiementRepository paiementRepository,
                                  TransactionRepository transactionRepository,
                                  PossessionRepository possessionRepository,
                                  ProprieteRepository proprieteRepository,
                                  InvestisseurRepository investisseurRepository) {
        this.paiementRepository = paiementRepository;
        this.transactionRepository = transactionRepository;
        this.possessionRepository = possessionRepository;
        this.proprieteRepository = proprieteRepository;
        this.investisseurRepository = investisseurRepository;
    }

    /**
     * Mission 1 + 2 + 3 : Flux complet d'achat de parts
     * 1. Créer l'intention d'achat (Paiement)
     * 2. Créer la Transaction avec un faux hash blockchain
     * 3. Si SUCCESS → mettre à jour la Possession et diminuer les parts disponibles
     */
    @Transactional
    public AchatResponse acheterParts(AchatRequest request) {

        // --- Récupérer l'investisseur ---
        Investisseur investisseur = investisseurRepository.findById(request.getInvestisseurId())
                .orElseThrow(() -> new RuntimeException("Investisseur non trouvé avec l'id : " + request.getInvestisseurId()));

        // --- Récupérer la propriété ---
        Propriete propriete = proprieteRepository.findById(request.getProprieteId())
                .orElseThrow(() -> new RuntimeException("Propriété non trouvée avec l'id : " + request.getProprieteId()));

        // --- Vérifications métier ---
        if (propriete.getStatut() != StatutPropriete.PUBLIEE) {
            throw new RuntimeException("Cette propriété n'est pas disponible à l'achat.");
        }

        if (propriete.getPartsDisponibles() < request.getNombreParts()) {
            throw new RuntimeException("Parts insuffisantes. Disponibles : " + propriete.getPartsDisponibles());
        }

        if (request.getNombreParts() <= 0) {
            throw new RuntimeException("Le nombre de parts doit être supérieur à 0.");
        }

        // --- Calculer le montant total ---
        BigDecimal montantTotal = propriete.getPrixUnitairePart()
                .multiply(BigDecimal.valueOf(request.getNombreParts()));

        // =============================================
        // MISSION 1 : Créer l'intention d'achat (Paiement)
        // =============================================
        Paiement paiement = new Paiement();
        paiement.setInvestisseur(investisseur);
        paiement.setPropriete(propriete);
        paiement.setMontant(montantTotal);
        paiement.setNombre_parts(request.getNombreParts());
        paiement.setType(TypePaiement.CRYPTO);
        paiement.setStatut(StatutPaiement.EN_ATTENTE);
        paiement.setDate(LocalDateTime.now());
        paiement = paiementRepository.save(paiement);

        // =============================================
        // MISSION 2 : Créer la Transaction (faux hash blockchain V1)
        // =============================================
        String fauxHash = "0x" + UUID.randomUUID().toString().replace("-", "");

        Transaction transaction = new Transaction();
        transaction.setPaiement(paiement);
        transaction.setHashTransaction(fauxHash);
        transaction.setTypeOperation("ACHAT");
        transaction.setNombreParts(request.getNombreParts());
        transaction.setMontant(montantTotal);
        transaction.setDateTransaction(LocalDateTime.now());
        transaction.setStatut(StatutTransaction.SUCCES); // V1 : on simule le succès
        transaction = transactionRepository.save(transaction);

        // =============================================
        // MISSION 3 : Logique métier critique (si SUCCESS)
        // =============================================
        if (transaction.getStatut() == StatutTransaction.SUCCES) {

            // Valider le paiement
            paiement.setStatut(StatutPaiement.VALIDE);
            paiementRepository.save(paiement);

            // Mettre à jour ou créer la Possession
            Possession possession = possessionRepository
                    .findByInvestisseurIdAndProprieteId(investisseur.getId(), propriete.getId())
                    .orElse(null);

            if (possession == null) {
                // Première fois que cet investisseur achète des parts de cette propriété
                possession = new Possession();
                possession.setInvestisseur(investisseur);
                possession.setPropriete(propriete);
                possession.setNombreDeParts(request.getNombreParts());
            } else {
                // L'investisseur possède déjà des parts → on ajoute
                possession.setNombreDeParts(
                        possession.getNombreDeParts() + request.getNombreParts()
                );
            }
            possessionRepository.save(possession);

            // Diminuer les parts disponibles de la propriété
            propriete.setPartsDisponibles(
                    propriete.getPartsDisponibles() - request.getNombreParts()
            );
            proprieteRepository.save(propriete);
        }

        // --- Construire la réponse ---
        return new AchatResponse(
                paiement.getId(),
                transaction.getId(),
                transaction.getHashTransaction(),
                transaction.getStatut().name(),
                request.getNombreParts(),
                montantTotal,
                propriete.getNom(),
                transaction.getDateTransaction()
        );
    }
}
