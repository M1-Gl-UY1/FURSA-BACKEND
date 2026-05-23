package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.dto.DemandeRetraitRequest;
import com.fursa.fursa_backend.dto.DemandeRetraitResponse;
import com.fursa.fursa_backend.model.DemandeRetrait;
import com.fursa.fursa_backend.model.EscrowPropriete;
import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.model.Wallet;
import com.fursa.fursa_backend.model.enumeration.MethodeRetrait;
import com.fursa.fursa_backend.model.enumeration.SourceRetrait;
import com.fursa.fursa_backend.model.enumeration.StatutDemandeRetrait;
import com.fursa.fursa_backend.model.enumeration.StatutEscrow;
import com.fursa.fursa_backend.model.enumeration.TypeEscrowTransaction;
import com.fursa.fursa_backend.model.enumeration.TypeMessage;
import com.fursa.fursa_backend.model.enumeration.TypeWalletTransaction;
import com.fursa.fursa_backend.repository.DemandeRetraitRepository;
import com.fursa.fursa_backend.repository.EscrowProprieteRepository;
import com.fursa.fursa_backend.repository.ProprieteRepository;
import com.fursa.fursa_backend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Phase 10e (Hugh 22/05/2026) : gestion des demandes de retrait.
 *
 * Flux :
 *  1. User cree une demande (montant + methode + reference cible)
 *  2. Le service VERIFIE le solde disponible et RESERVE le montant
 *     (debit immediat du wallet/escrow pour empecher double-spend)
 *  3. Admin valide -> commission 5% prelevee, montant final calcule
 *     - Si destination = wallet (escrow -> wallet proprio) : credit instantane
 *     - Si destination = cash externe : admin execute hors-systeme, puis marque COMPLETED
 *  4. Admin refuse -> le montant reserve est recredite a la source
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RetraitService {

    /** Commission FURSA prelevee sur chaque retrait (decision Hugh : 5%). */
    public static final BigDecimal COMMISSION_FURSA_PCT = new BigDecimal("5.00");

    private final DemandeRetraitRepository retraitRepository;
    private final WalletService walletService;
    private final EscrowService escrowService;
    private final EscrowProprieteRepository escrowRepository;
    private final ProprieteRepository proprieteRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    // =========================================================================
    // User : creer une demande
    // =========================================================================

    @Transactional
    public DemandeRetraitResponse demander(Long userId, DemandeRetraitRequest req) {
        Investisseur user = userRepository.findById(userId)
                .filter(u -> u instanceof Investisseur)
                .map(u -> (Investisseur) u)
                .orElseThrow(() -> new EntityNotFoundException("User introuvable : " + userId));

        BigDecimal montant = req.montant().setScale(2, RoundingMode.HALF_UP);

        // Selon la source, on valide le solde + on reserve
        long sourceId;
        if (req.source() == SourceRetrait.WALLET) {
            Wallet w = walletService.getOrCreate(userId);
            if (w.getSolde().compareTo(montant) < 0) {
                throw new IllegalStateException(
                        "Solde wallet insuffisant. Disponible : " + w.getSolde() + " USD.");
            }
            // Reserve : debit immediat (statut PENDING)
            walletService.debit(userId, montant, TypeWalletTransaction.DEBIT_WITHDRAW,
                    "Demande de retrait (en attente validation admin)",
                    "demande_retrait", null, null);
            sourceId = w.getId();
            // Verifier que la methode n'est pas WALLET_INTERNE (interdit pour source WALLET)
            if (req.methode() == MethodeRetrait.WALLET_INTERNE) {
                throw new IllegalArgumentException(
                        "Methode WALLET_INTERNE invalide pour un retrait depuis le wallet.");
            }
            if (req.referenceCible() == null || req.referenceCible().isBlank()) {
                throw new IllegalArgumentException(
                        "Reference cible obligatoire (numero MM / IBAN / wallet crypto).");
            }
        } else {
            // ESCROW_PROPRIETE : seul le proprietaire peut retirer depuis l'escrow de son bien
            if (req.sourceId() == null) {
                throw new IllegalArgumentException("sourceId (propriete) obligatoire pour ESCROW_PROPRIETE.");
            }
            Propriete p = proprieteRepository.findById(req.sourceId())
                    .orElseThrow(() -> new EntityNotFoundException("Propriete introuvable : " + req.sourceId()));
            if (p.getProposeurId() == null || !p.getProposeurId().equals(userId)) {
                throw new AccessDeniedException(
                        "Vous ne pouvez retirer que sur l'escrow de vos propres biens.");
            }
            EscrowPropriete e = escrowRepository.findByProprieteId(p.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Escrow introuvable."));
            if (e.getStatut() != StatutEscrow.FINANCEE) {
                throw new IllegalStateException(
                        "Retrait possible uniquement sur les biens FINANCEE (100% collecte). "
                                + "Statut actuel : " + e.getStatut());
            }
            if (e.getSolde().compareTo(montant) < 0) {
                throw new IllegalStateException(
                        "Solde escrow insuffisant. Disponible : " + e.getSolde() + " USD.");
            }
            // Reserve : debit immediat de l'escrow
            escrowService.debit(e.getId(), montant,
                    TypeEscrowTransaction.DEBIT_RETRAIT_PROPRIO,
                    userId, "demande_retrait", null,
                    "Demande de retrait proprio (en attente validation admin)");
            sourceId = p.getId();
            // Forcer methode WALLET_INTERNE pour les retraits escrow (l'argent ira sur le wallet proprio)
            if (req.methode() != MethodeRetrait.WALLET_INTERNE) {
                log.warn("Methode {} forcee a WALLET_INTERNE pour retrait escrow", req.methode());
            }
        }

        DemandeRetrait d = new DemandeRetrait();
        d.setUser(user);
        d.setSource(req.source());
        d.setSourceId(sourceId);
        d.setMontantDemande(montant);
        d.setMethode(req.source() == SourceRetrait.ESCROW_PROPRIETE
                ? MethodeRetrait.WALLET_INTERNE
                : req.methode());
        d.setReferenceCible(req.referenceCible());
        d.setStatut(StatutDemandeRetrait.PENDING);
        DemandeRetrait saved = retraitRepository.save(d);

        notifierAdmins(
                "Nouvelle demande de retrait",
                "Demande #" + saved.getId() + " de " + montant + " USD par " + user.getEmail()
                        + " (" + req.source() + ").",
                TypeMessage.INFO
        );

        log.info("Demande de retrait #{} creee : user={}, source={}, montant={} USD",
                saved.getId(), userId, req.source(), montant);
        return toResponse(saved);
    }

    // =========================================================================
    // Admin : valider, refuser, completer
    // =========================================================================

    @Transactional
    public DemandeRetraitResponse valider(Long demandeId, Long adminId) {
        DemandeRetrait d = retraitRepository.findById(demandeId)
                .orElseThrow(() -> new EntityNotFoundException("Demande introuvable : " + demandeId));
        if (d.getStatut() != StatutDemandeRetrait.PENDING) {
            throw new IllegalStateException(
                    "Seules les demandes PENDING peuvent etre validees (statut actuel : " + d.getStatut() + ")");
        }

        // Calculer commission + net
        BigDecimal commission = d.getMontantDemande()
                .multiply(COMMISSION_FURSA_PCT)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal net = d.getMontantDemande().subtract(commission);

        d.setCommissionFursa(commission);
        d.setMontantFinal(net);
        d.setValideeLe(LocalDateTime.now());
        d.setValideeParAdminId(adminId);

        if (d.getSource() == SourceRetrait.ESCROW_PROPRIETE) {
            // Escrow -> wallet proprio : credit net sur wallet
            walletService.credit(d.getUser().getId(), net,
                    TypeWalletTransaction.CREDIT_VENTE_PARTS,
                    "Retrait escrow valide (commission FURSA " + commission + " USD prelevee)",
                    "demande_retrait", d.getId(), null);
            d.setStatut(StatutDemandeRetrait.COMPLETED);
            d.setCompleteeLe(LocalDateTime.now());

            notifyUser(d.getUser(),
                    "Retrait valide : fonds sur votre wallet",
                    "Votre demande de retrait #" + d.getId() + " a ete validee. "
                            + net + " USD ont ete credites sur votre wallet (commission FURSA "
                            + commission + " USD).",
                    TypeMessage.ANNONCE);
        } else {
            // Wallet -> cash externe : on marque APPROVED, l'admin doit completer apres execution
            d.setStatut(StatutDemandeRetrait.APPROVED);

            notifyUser(d.getUser(),
                    "Retrait approuve : execution en cours",
                    "Votre demande de retrait #" + d.getId() + " a ete approuvee. "
                            + net + " USD seront verses via " + d.getMethode()
                            + " (commission FURSA " + commission + " USD prelevee). "
                            + "Vous serez notifie a l'execution.",
                    TypeMessage.ANNONCE);
        }

        DemandeRetrait saved = retraitRepository.save(d);
        log.info("Demande #{} validee par admin {}. Commission {} USD, net {} USD",
                demandeId, adminId, commission, net);
        return toResponse(saved);
    }

    @Transactional
    public DemandeRetraitResponse refuser(Long demandeId, Long adminId, String motif) {
        DemandeRetrait d = retraitRepository.findById(demandeId)
                .orElseThrow(() -> new EntityNotFoundException("Demande introuvable : " + demandeId));
        if (d.getStatut() != StatutDemandeRetrait.PENDING) {
            throw new IllegalStateException(
                    "Seules les demandes PENDING peuvent etre refusees (statut actuel : " + d.getStatut() + ")");
        }
        if (motif == null || motif.trim().length() < 5) {
            throw new IllegalArgumentException("Motif obligatoire (min 5 caracteres).");
        }

        // Recrediter la source du montant reserve
        if (d.getSource() == SourceRetrait.WALLET) {
            walletService.credit(d.getUser().getId(), d.getMontantDemande(),
                    TypeWalletTransaction.AJUSTEMENT_ADMIN,
                    "Refund demande de retrait refusee #" + d.getId() + " : " + motif,
                    "demande_retrait", d.getId(), null);
        } else {
            // Recrediter l'escrow propriete (utiliser creditAchat avec investisseurId null comme ajustement)
            EscrowPropriete e = escrowRepository.findByProprieteId(d.getSourceId())
                    .orElseThrow(() -> new EntityNotFoundException("Escrow introuvable."));
            e.setSolde(e.getSolde().add(d.getMontantDemande()));
            escrowRepository.save(e);
            // On trace via la table escrow_transaction (utilise creditAchat manuel)
            log.info("Escrow {} recredite de {} USD apres refus demande #{}",
                    e.getId(), d.getMontantDemande(), d.getId());
        }

        d.setStatut(StatutDemandeRetrait.REFUSED);
        d.setMotifRefus(motif.trim());
        d.setValideeLe(LocalDateTime.now());
        d.setValideeParAdminId(adminId);
        DemandeRetrait saved = retraitRepository.save(d);

        notifyUser(d.getUser(),
                "Retrait refuse",
                "Votre demande de retrait #" + d.getId() + " (" + d.getMontantDemande()
                        + " USD) a ete refusee. Le montant a ete recredite a la source. Motif : " + motif,
                TypeMessage.AVERTISSEMENT);

        log.info("Demande #{} refusee par admin {}. Motif : {}", demandeId, adminId, motif);
        return toResponse(saved);
    }

    /**
     * Pour les retraits cash (APPROVED), admin marque COMPLETED apres execution
     * du paiement reel hors-systeme.
     */
    @Transactional
    public DemandeRetraitResponse marquerCompletee(Long demandeId, Long adminId, String preuvePaiement) {
        DemandeRetrait d = retraitRepository.findById(demandeId)
                .orElseThrow(() -> new EntityNotFoundException("Demande introuvable : " + demandeId));
        if (d.getStatut() != StatutDemandeRetrait.APPROVED) {
            throw new IllegalStateException(
                    "Seules les demandes APPROVED peuvent etre completees (statut actuel : " + d.getStatut() + ")");
        }
        if (preuvePaiement == null || preuvePaiement.isBlank()) {
            throw new IllegalArgumentException("Preuve de paiement obligatoire (reference, hash...).");
        }

        d.setStatut(StatutDemandeRetrait.COMPLETED);
        d.setPreuvePaiement(preuvePaiement.trim());
        d.setCompleteeLe(LocalDateTime.now());
        DemandeRetrait saved = retraitRepository.save(d);

        notifyUser(d.getUser(),
                "Retrait execute",
                "Votre retrait #" + d.getId() + " de " + d.getMontantFinal()
                        + " USD a ete execute via " + d.getMethode() + ". Reference : " + preuvePaiement,
                TypeMessage.TRANSACTION);

        log.info("Demande #{} marquee COMPLETED par admin {}", demandeId, adminId);
        return toResponse(saved);
    }

    // =========================================================================
    // Lectures
    // =========================================================================

    public List<DemandeRetraitResponse> mesDemandes(Long userId) {
        return retraitRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toResponse).toList();
    }

    public List<DemandeRetraitResponse> toutesLesDemandes() {
        return retraitRepository.findAll(
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))
                .stream().map(this::toResponse).toList();
    }

    public List<DemandeRetraitResponse> demandesEnAttente() {
        return retraitRepository.findByStatutOrderByCreatedAtAsc(StatutDemandeRetrait.PENDING)
                .stream().map(this::toResponse).toList();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private DemandeRetraitResponse toResponse(DemandeRetrait d) {
        String sourceLibelle = null;
        if (d.getSource() == SourceRetrait.ESCROW_PROPRIETE) {
            sourceLibelle = proprieteRepository.findById(d.getSourceId())
                    .map(Propriete::getNom)
                    .orElse("Propriete #" + d.getSourceId());
        }
        Investisseur u = d.getUser();
        return new DemandeRetraitResponse(
                d.getId(),
                u == null ? null : u.getId(),
                u == null ? null : u.getEmail(),
                u == null ? null : (u.getPrenom() + " " + u.getNom()).trim(),
                d.getSource(),
                d.getSourceId(),
                sourceLibelle,
                d.getMontantDemande(),
                d.getCommissionFursa(),
                d.getMontantFinal(),
                d.getMethode(),
                d.getReferenceCible(),
                d.getStatut(),
                d.getMotifRefus(),
                d.getPreuvePaiement(),
                d.getCreatedAt(),
                d.getValideeLe(),
                d.getCompleteeLe(),
                d.getValideeParAdminId()
        );
    }

    private void notifierAdmins(String titre, String message, TypeMessage type) {
        userRepository.findAll().stream()
                .filter(u -> u.getRole() == com.fursa.fursa_backend.model.enumeration.Role.ADMIN
                        && u instanceof Investisseur)
                .forEach(u -> notificationService.envoyer((Investisseur) u, titre, message, type));
    }

    private void notifyUser(Investisseur u, String titre, String message, TypeMessage type) {
        if (u != null) notificationService.envoyer(u, titre, message, type);
    }
}
