package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.dto.EscrowProprieteResponse;
import com.fursa.fursa_backend.dto.EscrowTransactionResponse;
import com.fursa.fursa_backend.model.EscrowPropriete;
import com.fursa.fursa_backend.model.EscrowTransaction;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.model.enumeration.StatutEscrow;
import com.fursa.fursa_backend.model.enumeration.TypeEscrowTransaction;
import com.fursa.fursa_backend.repository.EscrowProprieteRepository;
import com.fursa.fursa_backend.repository.EscrowTransactionRepository;
import com.fursa.fursa_backend.repository.ProprieteRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Phase 10c : service central des escrows de propriete.
 *
 * Source de verite du compte sequestre par propriete. Toute mutation passe par
 * credit() ou debit() pour garantir l'append-only sur EscrowTransaction et
 * l'optimistic locking sur EscrowPropriete.
 *
 * Ne s'occupe PAS de la mecanique d'achat ni de la mecanique de retrait :
 * - L'achat (debit wallet + credit escrow + Possession) est dans MarchePrimaireService
 * - Le retrait proprio (debit escrow + credit wallet + commission FURSA) sera Phase 10e
 *
 * S'occupe uniquement de la coherence du solde + journal.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EscrowService {

    private final EscrowProprieteRepository escrowRepository;
    private final EscrowTransactionRepository txRepository;
    private final ProprieteRepository proprieteRepository;

    // =========================================================================
    // Lookup / Creation
    // =========================================================================

    @Transactional
    public EscrowPropriete getOrCreate(Long proprieteId) {
        return escrowRepository.findByProprieteId(proprieteId)
                .orElseGet(() -> createForPropriete(proprieteId));
    }

    @Transactional
    public EscrowPropriete createForPropriete(Long proprieteId) {
        if (escrowRepository.existsByProprieteId(proprieteId)) {
            return escrowRepository.findByProprieteId(proprieteId).orElseThrow();
        }
        Propriete p = proprieteRepository.findById(proprieteId)
                .orElseThrow(() -> new EntityNotFoundException("Propriete introuvable : " + proprieteId));

        EscrowPropriete e = new EscrowPropriete();
        e.setPropriete(p);
        e.setSolde(BigDecimal.ZERO);
        e.setTotalCollecte(BigDecimal.ZERO);
        e.setStatut(StatutEscrow.EN_COLLECTE);
        // Phase 10c ajustement (Hugh 22/05/2026) : 100% (toutes parts vendues) avant deblocage.
        e.setSeuilPct(100);
        EscrowPropriete saved = escrowRepository.save(e);
        log.info("Escrow cree pour propriete {} : id={}", proprieteId, saved.getId());
        return saved;
    }

    // =========================================================================
    // Mouvements
    // =========================================================================

    /**
     * Credite l'escrow (achat investisseur). Le wallet investisseur a deja ete debite
     * par WalletService AVANT cet appel (transaction englobante coté MarchePrimaireService).
     */
    @Transactional
    public EscrowTransaction creditAchat(Long proprieteId, BigDecimal montant,
                                          Long investisseurId,
                                          String refTable, Long refId, String libelle) {
        if (montant == null || montant.signum() <= 0) {
            throw new IllegalArgumentException("Le montant credite doit etre strictement positif.");
        }
        EscrowPropriete e = getOrCreate(proprieteId);
        if (e.getStatut() == StatutEscrow.ANNULEE) {
            throw new IllegalStateException(
                    "Achat impossible : la collecte de cette propriete est annulee.");
        }
        BigDecimal m = montant.setScale(2, RoundingMode.HALF_UP);
        e.setSolde(e.getSolde().add(m));
        e.setTotalCollecte(e.getTotalCollecte().add(m));
        escrowRepository.save(e);

        EscrowTransaction tx = new EscrowTransaction();
        tx.setEscrow(e);
        tx.setType(TypeEscrowTransaction.CREDIT_ACHAT);
        tx.setMontant(m);
        tx.setSoldeApres(e.getSolde());
        tx.setInvestisseurId(investisseurId);
        tx.setRefTable(refTable);
        tx.setRefId(refId);
        tx.setLibelle(libelle);
        EscrowTransaction saved = txRepository.save(tx);

        log.info("Escrow {} (prop {}) : credit achat +{} EUR par inv {}. Solde : {}",
                e.getId(), proprieteId, m, investisseurId, e.getSolde());
        return saved;
    }

    /**
     * Debit l'escrow (retrait proprio, commission, refund, ajustement).
     * Refuse si solde insuffisant.
     */
    @Transactional
    public EscrowTransaction debit(Long escrowId, BigDecimal montant,
                                    TypeEscrowTransaction type,
                                    Long investisseurId, String refTable, Long refId,
                                    String libelle) {
        if (montant == null || montant.signum() <= 0) {
            throw new IllegalArgumentException("Le montant debite doit etre strictement positif.");
        }
        EscrowPropriete e = escrowRepository.findById(escrowId)
                .orElseThrow(() -> new EntityNotFoundException("Escrow introuvable : " + escrowId));
        BigDecimal m = montant.setScale(2, RoundingMode.HALF_UP);
        if (e.getSolde().compareTo(m) < 0) {
            throw new IllegalStateException("Solde escrow insuffisant : " + e.getSolde()
                    + " EUR disponibles, " + m + " EUR demandes.");
        }
        e.setSolde(e.getSolde().subtract(m));
        escrowRepository.save(e);

        EscrowTransaction tx = new EscrowTransaction();
        tx.setEscrow(e);
        tx.setType(type);
        tx.setMontant(m.negate());
        tx.setSoldeApres(e.getSolde());
        tx.setInvestisseurId(investisseurId);
        tx.setRefTable(refTable);
        tx.setRefId(refId);
        tx.setLibelle(libelle);
        return txRepository.save(tx);
    }

    // =========================================================================
    // Statut & lecture
    // =========================================================================

    public EscrowProprieteResponse getStatut(Long proprieteId) {
        EscrowPropriete e = getOrCreate(proprieteId);
        return toResponse(e);
    }

    public List<EscrowTransactionResponse> historique(Long proprieteId) {
        EscrowPropriete e = escrowRepository.findByProprieteId(proprieteId)
                .orElseThrow(() -> new EntityNotFoundException("Escrow introuvable pour prop " + proprieteId));
        return txRepository.findByEscrowIdOrderByCreatedAtDesc(e.getId())
                .stream().map(this::toResponse).toList();
    }

    public List<EscrowProprieteResponse> listerTous() {
        return escrowRepository.findAll().stream().map(this::toResponse).toList();
    }

    // =========================================================================
    // Mapping
    // =========================================================================

    public EscrowProprieteResponse toResponse(EscrowPropriete e) {
        Propriete p = e.getPropriete();
        BigDecimal montantCible = montantCible(p);
        BigDecimal montantSeuil = montantCible
                .multiply(BigDecimal.valueOf(e.getSeuilPct()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        double pct = 0.0;
        if (montantCible.signum() > 0) {
            pct = e.getTotalCollecte()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(montantCible, 2, RoundingMode.HALF_UP)
                    .doubleValue();
        }
        return new EscrowProprieteResponse(
                e.getId(),
                p == null ? null : p.getId(),
                p == null ? null : p.getNom(),
                e.getSolde(),
                e.getTotalCollecte(),
                montantCible,
                montantSeuil,
                pct,
                e.getSeuilPct(),
                e.getStatut(),
                e.getCreatedAt(),
                e.getFinanceeLe(),
                e.getAnnuleeLe(),
                e.getMotifAnnulation()
        );
    }

    private BigDecimal montantCible(Propriete p) {
        if (p == null || p.getNombreTotalPart() == null || p.getPrixUnitairePart() == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(p.getNombreTotalPart())
                .multiply(p.getPrixUnitairePart())
                .setScale(2, RoundingMode.HALF_UP);
    }

    public EscrowTransactionResponse toResponse(EscrowTransaction t) {
        return new EscrowTransactionResponse(
                t.getId(),
                t.getEscrow().getId(),
                t.getType(),
                t.getMontant(),
                t.getSoldeApres(),
                t.getInvestisseurId(),
                t.getRefTable(),
                t.getRefId(),
                t.getLibelle(),
                t.getMetadata(),
                t.getCreatedAt()
        );
    }
}
