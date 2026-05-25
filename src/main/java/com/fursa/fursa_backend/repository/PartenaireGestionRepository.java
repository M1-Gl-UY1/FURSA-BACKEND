package com.fursa.fursa_backend.repository;

import com.fursa.fursa_backend.model.PartenaireGestion;
import com.fursa.fursa_backend.model.enumeration.TypePartenaire;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PartenaireGestionRepository extends JpaRepository<PartenaireGestion, Long> {

    List<PartenaireGestion> findByActifTrueOrderByNomAsc();

    List<PartenaireGestion> findByActifTrueAndTypePartenaireOrderByNomAsc(TypePartenaire type);
}
