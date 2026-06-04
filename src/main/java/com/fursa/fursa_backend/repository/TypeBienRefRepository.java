package com.fursa.fursa_backend.repository;

import com.fursa.fursa_backend.model.TypeBienRef;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TypeBienRefRepository extends JpaRepository<TypeBienRef, Long> {
    List<TypeBienRef> findByActifTrueOrderByOrdreAsc();
    List<TypeBienRef> findAllByOrderByOrdreAsc();
    Optional<TypeBienRef> findByCode(String code);
    boolean existsByCode(String code);
}
