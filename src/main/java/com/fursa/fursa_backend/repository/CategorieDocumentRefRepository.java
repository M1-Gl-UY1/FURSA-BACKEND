package com.fursa.fursa_backend.repository;

import com.fursa.fursa_backend.model.CategorieDocumentRef;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategorieDocumentRefRepository extends JpaRepository<CategorieDocumentRef, Long> {
    List<CategorieDocumentRef> findByActifTrueOrderByOrdreAsc();
    List<CategorieDocumentRef> findAllByOrderByOrdreAsc();
    Optional<CategorieDocumentRef> findByCode(String code);
    boolean existsByCode(String code);
}
