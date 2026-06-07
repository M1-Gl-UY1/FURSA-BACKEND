package com.fursa.fursa_backend.repository;

import com.fursa.fursa_backend.model.User;
import com.fursa.fursa_backend.model.enumeration.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    /** V2 Z (07/06/2026) : recupere le 1er user ADMIN (pour resoudre le master wallet FURSA). */
    Optional<User> findFirstByRoleOrderByIdAsc(Role role);
}
