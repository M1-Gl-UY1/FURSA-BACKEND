package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.model.enumeration.Role;
import com.fursa.fursa_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * V2 Z (07/06/2026) : wallet master FURSA (escrow virtuel des revenus en attente).
 *
 *   À la déclaration d'un revenu : le wallet du propriétaire est débité et le
 *   wallet master FURSA est crédité du même montant. Comptablement, l'argent
 *   "réservé" est tracé sur le wallet master.
 *
 *   À la distribution : le wallet master est débité de la somme totale et
 *   chaque investisseur est crédité au prorata de ses parts.
 *
 *   Si un revenu est refusé par l'admin : remboursement intégral du wallet
 *   propriétaire depuis le wallet master.
 *
 *   IMPLEMENTATION : le master est le wallet du PREMIER user ADMIN en base.
 *   Choix volontairement minimaliste : pas de migration BDD, pas de user
 *   technique séparé. Si la plateforme a plusieurs admins, on prend le plus
 *   ancien (id le plus bas). Une property dédiée pourrait être ajoutée plus
 *   tard si besoin d'un override explicite.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FursaMasterWalletService {

    private final UserRepository userRepository;
    private final WalletService walletService;

    private Long cachedMasterUserId;

    /**
     * Retourne l'id du user "master FURSA" (premier ADMIN). Cache en mémoire
     * après la première résolution. Lève IllegalStateException si aucun admin
     * n'existe en base — situation anormale qui doit faire planter explicitement
     * plutôt que d'enchainer des bugs silencieux.
     */
    public Long getMasterUserId() {
        if (cachedMasterUserId != null) return cachedMasterUserId;

        Long id = userRepository.findFirstByRoleOrderByIdAsc(Role.ADMIN)
                .map(u -> u.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Aucun user ADMIN trouve. Impossible de resoudre le wallet master FURSA. "
                                + "Cree au moins un admin via /api/users."));

        // S'assure que le wallet master existe (création paresseuse).
        walletService.getOrCreate(id);

        log.info("[MasterWallet] Master FURSA resolu : userId={}", id);
        cachedMasterUserId = id;
        return id;
    }

    /** Invalide le cache (test, ou changement de master en runtime). */
    public void resetCache() {
        cachedMasterUserId = null;
    }
}
