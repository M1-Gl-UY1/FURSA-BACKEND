package com.fursa.fursa_backend.repository;

import com.fursa.fursa_backend.model.ListeAttente;
import com.fursa.fursa_backend.model.enumeration.StatutListeAttente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ListeAttenteRepository extends JpaRepository<ListeAttente, Long> {

    /** Toutes les inscriptions actives (EN_ATTENTE) d'un bien, par ordre FIFO. */
    List<ListeAttente> findByProprieteIdAndStatutOrderByCreatedAtAsc(
            Long proprieteId, StatutListeAttente statut);

    /** Toutes les inscriptions (actives + servies + annulees) d'un bien. */
    List<ListeAttente> findByProprieteIdOrderByCreatedAtDesc(Long proprieteId);

    /** Inscriptions d'un investisseur, du plus recent au plus ancien. */
    List<ListeAttente> findByInvestisseurIdOrderByCreatedAtDesc(Long investisseurId);

    /** Pour empecher la double inscription active. */
    Optional<ListeAttente> findByProprieteIdAndInvestisseurIdAndStatut(
            Long proprieteId, Long investisseurId, StatutListeAttente statut);

    /** Somme des parts en attente sur un bien (utilise pour le bonus_demande). */
    @Query("SELECT COALESCE(SUM(la.nombreParts), 0) FROM ListeAttente la " +
           "WHERE la.propriete.id = :proprieteId AND la.statut = 'EN_ATTENTE'")
    int sumPartsEnAttente(Long proprieteId);
}
