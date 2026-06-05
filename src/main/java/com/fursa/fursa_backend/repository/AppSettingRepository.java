package com.fursa.fursa_backend.repository;

import com.fursa.fursa_backend.model.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
    /** Pour la page admin : liste groupee puis triee par ordre. */
    List<AppSetting> findAllByOrderByGroupeAscOrdreAsc();
}
