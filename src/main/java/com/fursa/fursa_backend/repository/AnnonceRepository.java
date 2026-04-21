// AnnonceRepository.java
package com.fursa.fursa_backend.repository;

import com.fursa.fursa_backend.model.Annonce;
import com.fursa.fursa_backend.model.enumeration.StatutAnnonce;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnnonceRepository extends JpaRepository<Annonce, Long> {

    List<Annonce> findByStatut(StatutAnnonce statut);

    List<Annonce> findByInvestisseurId(Long investisseurId);

    @Query("SELECT a FROM Annonce a WHERE a.statut = 'OUVERTE' AND a.nombreDePartsAVendre > 0")
    List<Annonce> findActiveAnnonces();

    @Query("SELECT a FROM Annonce a WHERE a.propriete.id = :proprieteId AND a.statut = 'OUVERTE'")
    List<Annonce> findActiveByProprieteId(@Param("proprieteId") Long proprieteId);

    Optional<Annonce> findByIdAndStatut(Long id, StatutAnnonce statut);
}