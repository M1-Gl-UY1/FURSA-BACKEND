package com.fursa.fursa_backend.repository;

import com.fursa.fursa_backend.model.Annonce;
import com.fursa.fursa_backend.model.enumeration.StatutAnnonce;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnnonceRepository extends JpaRepository<Annonce, Long> {
    List<Annonce> findByStatut(StatutAnnonce statut);
    Page<Annonce> findByStatut(StatutAnnonce statut, Pageable pageable);
    List<Annonce> findByInvestisseurId(Long investisseurId);
    List<Annonce> findByProprieteIdAndStatut(Long proprieteId, StatutAnnonce statut);
}
