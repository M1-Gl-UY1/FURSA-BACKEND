package com.fursa.fursa_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.fursa.fursa_backend.model.Propriete;


@Repository
public interface ProprieteRepository extends JpaRepository<Propriete, Long> {

}
