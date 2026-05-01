package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.dto.RevenuRequest;
import com.fursa.fursa_backend.dto.RevenuResponse;
import com.fursa.fursa_backend.dto.SubmissionRevenuRequest;
import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.model.Revenus;
import com.fursa.fursa_backend.model.enumeration.Role;
import com.fursa.fursa_backend.model.enumeration.StatutRevenu;
import com.fursa.fursa_backend.model.enumeration.TypeMessage;
import com.fursa.fursa_backend.repository.ProprieteRepository;
import com.fursa.fursa_backend.repository.RevenusRepository;
import com.fursa.fursa_backend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RevenuService {

    private final RevenusRepository revenusRepository;
    private final ProprieteRepository proprieteRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    // =========================================================================
    // Création directe par admin (workflow historique)
    // =========================================================================

    @Transactional
    public RevenuResponse creer(RevenuRequest request) {
        Propriete propriete = proprieteRepository.findById(request.proprieteId())
                .orElseThrow(() -> new EntityNotFoundException("Propriete non trouvee: id=" + request.proprieteId()));

        Revenus revenu = new Revenus();
        revenu.setPropriete(propriete);
        revenu.setMontantTotal(request.montantTotal());
        revenu.setDate(request.date() != null ? request.date() : LocalDate.now());
        // Création directe par admin → directement VALIDE (peut être distribué tout de suite)
        revenu.setStatut(StatutRevenu.VALIDE);
        return toResponse(revenusRepository.save(revenu));
    }

    public List<RevenuResponse> lister() {
        return revenusRepository.findAll().stream().map(this::toResponse).toList();
    }

    public List<RevenuResponse> listerParPropriete(Long proprieteId) {
        return revenusRepository.findByProprieteId(proprieteId).stream().map(this::toResponse).toList();
    }

    public RevenuResponse getById(Long id) {
        return toResponse(revenusRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Revenu non trouve: id=" + id)));
    }

    // =========================================================================
    // PHASE 8 : workflow déclaration propriétaire
    // =========================================================================

    @Transactional
    public RevenuResponse soumettre(Long proposeurId, SubmissionRevenuRequest req) {
        Propriete propriete = proprieteRepository.findById(req.proprieteId())
                .orElseThrow(() -> new EntityNotFoundException("Propriete non trouvee: id=" + req.proprieteId()));

        if (propriete.getProposeurId() == null || !propriete.getProposeurId().equals(proposeurId)) {
            throw new AccessDeniedException("Vous ne pouvez déclarer un revenu que pour vos propres biens.");
        }

        Revenus revenu = new Revenus();
        revenu.setPropriete(propriete);
        revenu.setMontantTotal(req.montantTotal());
        revenu.setDate(LocalDate.now());
        revenu.setProposeurId(proposeurId);
        revenu.setStatut(StatutRevenu.EN_REVIEW);
        revenu.setPeriodeDebut(req.periodeDebut());
        revenu.setPeriodeFin(req.periodeFin());

        Revenus saved = revenusRepository.save(revenu);

        notifierAdmins(
                "Nouvelle déclaration de revenu",
                "Le bien \"" + propriete.getNom() + "\" a une nouvelle déclaration de revenu en attente.",
                TypeMessage.INFO
        );

        return toResponse(saved);
    }

    public List<RevenuResponse> listerMesRevenus(Long proposeurId) {
        return revenusRepository.findByProposeurIdOrderByIdDesc(proposeurId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public RevenuResponse approuver(Long id) {
        Revenus r = revenusRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Revenu non trouve: id=" + id));
        if (r.getStatut() != StatutRevenu.EN_REVIEW) {
            throw new IllegalStateException("Seuls les revenus EN_REVIEW peuvent être approuvés (statut actuel : " + r.getStatut() + ")");
        }
        r.setStatut(StatutRevenu.VALIDE);
        r.setMotifRefus(null);
        Revenus saved = revenusRepository.save(r);

        notifierProposeur(saved,
                "Revenu validé",
                "Votre déclaration de revenu pour \"" + saved.getPropriete().getNom() + "\" a été validée. La distribution aux investisseurs sera prochainement effectuée.",
                TypeMessage.ANNONCE
        );

        return toResponse(saved);
    }

    @Transactional
    public RevenuResponse refuser(Long id, String motif) {
        Revenus r = revenusRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Revenu non trouve: id=" + id));
        if (r.getStatut() != StatutRevenu.EN_REVIEW) {
            throw new IllegalStateException("Seuls les revenus EN_REVIEW peuvent être refusés (statut actuel : " + r.getStatut() + ")");
        }
        r.setStatut(StatutRevenu.REFUSE);
        r.setMotifRefus(motif);
        Revenus saved = revenusRepository.save(r);

        notifierProposeur(saved,
                "Revenu refusé",
                "Votre déclaration pour \"" + saved.getPropriete().getNom() + "\" a été refusée. Motif : " + motif,
                TypeMessage.AVERTISSEMENT
        );

        return toResponse(saved);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void notifierAdmins(String titre, String message, TypeMessage type) {
        userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN && u instanceof Investisseur)
                .forEach(u -> notificationService.envoyer((Investisseur) u, titre, message, type));
    }

    private void notifierProposeur(Revenus r, String titre, String message, TypeMessage type) {
        if (r.getProposeurId() == null) return;
        userRepository.findById(r.getProposeurId()).ifPresent(u -> {
            if (u instanceof Investisseur inv) {
                notificationService.envoyer(inv, titre, message, type);
            }
        });
    }

    private RevenuResponse toResponse(Revenus r) {
        Propriete p = r.getPropriete();
        return new RevenuResponse(
                r.getId(),
                p == null ? null : p.getId(),
                p == null ? null : p.getNom(),
                r.getDate(),
                r.getMontantTotal(),
                r.getProposeurId(),
                r.getStatut(),
                r.getMotifRefus(),
                r.getPeriodeDebut(),
                r.getPeriodeFin()
        );
    }
}
