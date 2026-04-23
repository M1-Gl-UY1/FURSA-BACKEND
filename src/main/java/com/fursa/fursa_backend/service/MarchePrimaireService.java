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

import com.fursa.fursa_backend.dto.PaiementResponse;
import com.fursa.fursa_backend.dto.PossessionResponse;
import com.fursa.fursa_backend.dto.TransactionResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
    public AchatResponse acheterParts(Long investisseurId, AchatRequest request) {

        // --- Récupérer l'investisseur ---
        Investisseur investisseur = investisseurRepository.findById(investisseurId)
                .orElseThrow(() -> new RuntimeException("Investisseur non trouvé avec l'id : " + investisseurId));

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

    /**
     * Récupérer toutes les possessions
     */
    public List<PossessionResponse> getAllPossessions() {
        return possessionRepository.findAll().stream().map(p -> new PossessionResponse(
                p.getId(),
                p.getPropriete().getNom(),
                p.getPropriete().getLocalisation(),
                p.getNombreDeParts(),
                p.getPropriete().getPrixUnitairePart(),
                p.getPropriete().getPrixUnitairePart().multiply(BigDecimal.valueOf(p.getNombreDeParts())),
                p.getPropriete().getRentabilitePrevue()
        )).toList();
    }

    /**
     * Récupérer toutes les transactions
     */
    public List<TransactionResponse> getAllTransactions() {
        return transactionRepository.findAll().stream().map(t -> new TransactionResponse(
                t.getId(),
                t.getHashTransaction(),
                t.getTypeOperation(),
                t.getStatut().name(),
                t.getNombreParts(),
                t.getMontant(),
                t.getPaiement().getPropriete().getNom(),
                t.getDateTransaction()
        )).toList();
    }

    /**
     * Récupérer tous les paiements
     */
    public List<PaiementResponse> getAllPaiements() {
        return paiementRepository.findAll().stream().map(p -> new PaiementResponse(
                p.getId(),
                p.getMontant(),
                p.getType().name(),
                p.getStatut().name(),
                p.getNombre_parts(),
                p.getPropriete().getNom(),
                p.getDate()
        )).toList();
    }

    /**
     * Récupérer le portefeuille (possessions) d'un investisseur
     */
    public List<PossessionResponse> getPortefeuille(Long investisseurId) {
        investisseurRepository.findById(investisseurId)
                .orElseThrow(() -> new RuntimeException("Investisseur non trouvé avec l'id : " + investisseurId));

        List<Possession> possessions = possessionRepository.findByInvestisseurId(investisseurId);

        return possessions.stream().map(p -> new PossessionResponse(
                p.getId(),
                p.getPropriete().getNom(),
                p.getPropriete().getLocalisation(),
                p.getNombreDeParts(),
                p.getPropriete().getPrixUnitairePart(),
                p.getPropriete().getPrixUnitairePart().multiply(BigDecimal.valueOf(p.getNombreDeParts())),
                p.getPropriete().getRentabilitePrevue()
        )).toList();
    }

    /**
     * Récupérer l'historique des transactions d'un investisseur
     */
    public List<TransactionResponse> getTransactions(Long investisseurId) {
        investisseurRepository.findById(investisseurId)
                .orElseThrow(() -> new RuntimeException("Investisseur non trouvé avec l'id : " + investisseurId));

        List<Paiement> paiements = paiementRepository.findByInvestisseurId(investisseurId);

        return paiements.stream()
                .flatMap(p -> p.getTransactions().stream().map(t -> new TransactionResponse(
                        t.getId(),
                        t.getHashTransaction(),
                        t.getTypeOperation(),
                        t.getStatut().name(),
                        t.getNombreParts(),
                        t.getMontant(),
                        p.getPropriete().getNom(),
                        t.getDateTransaction()
                ))).toList();
    }

    /**
     * Récupérer l'historique des paiements d'un investisseur
     */
    public List<PaiementResponse> getPaiements(Long investisseurId) {
        investisseurRepository.findById(investisseurId)
                .orElseThrow(() -> new RuntimeException("Investisseur non trouvé avec l'id : " + investisseurId));

        List<Paiement> paiements = paiementRepository.findByInvestisseurId(investisseurId);

        return paiements.stream().map(p -> new PaiementResponse(
                p.getId(),
                p.getMontant(),
                p.getType().name(),
                p.getStatut().name(),
                p.getNombre_parts(),
                p.getPropriete().getNom(),
                p.getDate()
        )).toList();
    }
}
