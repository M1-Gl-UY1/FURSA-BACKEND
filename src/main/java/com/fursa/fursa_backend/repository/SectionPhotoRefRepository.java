package com.fursa.fursa_backend.repository;

import com.fursa.fursa_backend.model.SectionPhotoRef;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SectionPhotoRefRepository extends JpaRepository<SectionPhotoRef, Long> {
    List<SectionPhotoRef> findByActifTrueOrderByOrdreAsc();
    List<SectionPhotoRef> findAllByOrderByOrdreAsc();
    Optional<SectionPhotoRef> findByCode(String code);
    boolean existsByCode(String code);
}
