package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.dto.ListeAttenteRequest;
import com.fursa.fursa_backend.dto.ListeAttenteResponse;
import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.model.ListeAttente;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.model.enumeration.StatutListeAttente;
import com.fursa.fursa_backend.model.enumeration.TypeMessage;
import com.fursa.fursa_backend.repository.ListeAttenteRepository;
import com.fursa.fursa_backend.repository.ProprieteRepository;
import com.fursa.fursa_backend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * P2 (Hugh 22/05/2026) : gestion de la liste d'attente sur les biens finances.
 *
 * Chaque modification (inscription / desinscription / service) declenche un
 * recalcul du bonus_demande via PrixPartService.
 *
 * Voir PRIX_DYNAMIQUE_FURSA.md §4 pour la formule du bonus_demande.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ListeAttenteService {

    private final ListeAttenteRepository repository;
    private final ProprieteRepository proprieteRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final PrixPartService prixPartService;

    /**
     * Inscrit un investisseur en liste d'attente pour un bien.
     *
     * Regles :
     * - Le bien doit etre PUBLIEE
     * - Le bien doit etre entierement vendu (partsDisponibles == 0)
     * - L'investisseur ne peut avoir qu'une seule inscription EN_ATTENTE par bien
     */
    @Transactional
    public ListeAttenteResponse inscrire(Long investisseurId, ListeAttenteRequest req) {
        Propriete p = proprieteRepository.findById(req.proprieteId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Propriete non trouvee: id=" + req.proprieteId()));

        if (p.getPartsDisponibles() != null && p.getPartsDisponibles() > 0) {
            throw new IllegalStateException(
                    "Inscription en liste d'attente impossible : il reste " +
                            p.getPartsDisponibles() + " parts disponibles a l'achat normal.");
        }

        Optional<ListeAttente> existante = repository
                .findByProprieteIdAndInvestisseurIdAndStatut(
                        req.proprieteId(), investisseurId, StatutListeAttente.EN_ATTENTE);
        if (existante.isPresent()) {
            throw new IllegalStateException(
                    "Vous etes deja inscrit en liste d'attente pour ce bien (id="
                            + existante.get().getId() + ").");
        }

        ListeAttente la = new ListeAttente();
        la.setPropriete(p);
        la.setInvestisseurId(investisseurId);
        la.setNombreParts(req.nombreParts());
        la.setStatut(StatutListeAttente.EN_ATTENTE);
        ListeAttente saved = repository.save(la);

        log.info("[ListeAttente] Inscription inv={} prop={} parts={} -> id={}",
                investisseurId, req.proprieteId(), req.nombreParts(), saved.getId());

        // Hook prix dynamique
        int total = repository.sumPartsEnAttente(req.proprieteId());
        prixPartService.appliquerChangementListeAttente(req.proprieteId(), total, saved.getId());

        return toResponse(saved, calculerPosition(saved));
    }

    /**
     * Desinscription d'une inscription EN_ATTENTE. Seul le titulaire peut le faire.
     */
    @Transactional
    public ListeAttenteResponse desinscrire(Long inscriptionId, Long investisseurId) {
        ListeAttente la = repository.findById(inscriptionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Inscription non trouvee: id=" + inscriptionId));

        if (!la.getInvestisseurId().equals(investisseurId)) {
            throw new AccessDeniedException(
                    "Vous ne pouvez annuler que vos propres inscriptions.");
        }
        if (la.getStatut() != StatutListeAttente.EN_ATTENTE) {
            throw new IllegalStateException(
                    "Seules les inscriptions EN_ATTENTE peuvent etre annulees (actuel : "
                            + la.getStatut() + ")");
        }

        la.setStatut(StatutListeAttente.ANNULE);
        ListeAttente saved = repository.save(la);

        log.info("[ListeAttente] Desinscription id={} inv={} prop={}",
                inscriptionId, investisseurId, saved.getPropriete().getId());

        int total = repository.sumPartsEnAttente(saved.getPropriete().getId());
        prixPartService.appliquerChangementListeAttente(
                saved.getPropriete().getId(), total, saved.getId());

        return toResponse(saved, null);
    }

    /**
     * Mes inscriptions (tous statuts confondus). Pour le dashboard investisseur.
     */
    public List<ListeAttenteResponse> mesInscriptions(Long investisseurId) {
        return repository.findByInvestisseurIdOrderByCreatedAtDesc(investisseurId).stream()
                .map(la -> toResponse(la, calculerPosition(la)))
                .toList();
    }

    /**
     * File complete d'un bien (admin ou public). Permet aux investisseurs de
     * voir la pression sur le bien avant de s'inscrire.
     */
    public List<ListeAttenteResponse> filePropriete(Long proprieteId) {
        List<ListeAttente> tous = repository
                .findByProprieteIdAndStatutOrderByCreatedAtAsc(
                        proprieteId, StatutListeAttente.EN_ATTENTE);
        return tous.stream()
                .map(la -> {
                    int pos = tous.indexOf(la) + 1;
                    return toResponse(la, pos);
                })
                .toList();
    }

    /**
     * Hook appele depuis le marche secondaire quand une part redevient
     * disponible. Notifie le premier de la file.
     * (V1 : juste notif, pas de reservation automatique avec deadline.)
     */
    @Transactional
    public void notifierPremier(Long proprieteId) {
        List<ListeAttente> file = repository
                .findByProprieteIdAndStatutOrderByCreatedAtAsc(
                        proprieteId, StatutListeAttente.EN_ATTENTE);
        if (file.isEmpty()) return;

        ListeAttente premier = file.get(0);
        userRepository.findById(premier.getInvestisseurId())
                .filter(u -> u instanceof Investisseur)
                .map(u -> (Investisseur) u)
                .ifPresent(inv -> {
                    Propriete p = premier.getPropriete();
                    notificationService.envoyer(
                            inv,
                            "Une part s'est liberee !",
                            "Une part du bien \"" + p.getNom()
                                    + "\" vient de se liberer sur le marche secondaire. "
                                    + "Connectez-vous rapidement pour saisir l'opportunite.",
                            TypeMessage.ANNONCE
                    );
                    premier.setNotifieLe(LocalDateTime.now());
                    premier.setStatut(StatutListeAttente.NOTIFIE);
                    repository.save(premier);
                    log.info("[ListeAttente] Notif envoyee a inv={} pour prop={}",
                            premier.getInvestisseurId(), proprieteId);
                });
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private Integer calculerPosition(ListeAttente la) {
        if (la.getStatut() != StatutListeAttente.EN_ATTENTE) return null;
        List<ListeAttente> file = repository
                .findByProprieteIdAndStatutOrderByCreatedAtAsc(
                        la.getPropriete().getId(), StatutListeAttente.EN_ATTENTE);
        int idx = -1;
        for (int i = 0; i < file.size(); i++) {
            if (file.get(i).getId().equals(la.getId())) {
                idx = i;
                break;
            }
        }
        return idx < 0 ? null : idx + 1;
    }

    private ListeAttenteResponse toResponse(ListeAttente la, Integer position) {
        Propriete p = la.getPropriete();
        return new ListeAttenteResponse(
                la.getId(),
                p == null ? null : p.getId(),
                p == null ? null : p.getNom(),
                p == null ? null : p.getLocalisation(),
                la.getInvestisseurId(),
                la.getNombreParts(),
                la.getStatut(),
                position,
                la.getCreatedAt(),
                la.getNotifieLe(),
                la.getServiLe()
        );
    }
}
