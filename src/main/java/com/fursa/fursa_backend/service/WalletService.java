package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.dto.WalletResponse;
import com.fursa.fursa_backend.dto.WalletTransactionResponse;
import com.fursa.fursa_backend.exception.InsufficientFundsException;
import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.model.Wallet;
import com.fursa.fursa_backend.model.WalletTransaction;
import com.fursa.fursa_backend.model.enumeration.TypeWalletTransaction;
import com.fursa.fursa_backend.repository.UserRepository;
import com.fursa.fursa_backend.repository.WalletRepository;
import com.fursa.fursa_backend.repository.WalletTransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Phase 10a : service central des wallets utilisateurs.
 *
 * Garanties :
 * - Toute mutation de solde passe par credit() ou debit() (jamais d'acces direct au champ).
 * - Optimistic locking via @Version sur Wallet : conflit -> 409 (GlobalExceptionHandler).
 * - Append-only : WalletTransaction est immuable une fois cree.
 * - Le solde ne peut jamais devenir negatif (verifie avant debit + CHECK constraint DB).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final UserRepository userRepository;

    // =========================================================================
    // Lookup / Creation
    // =========================================================================

    /**
     * Recupere le wallet d'un user, le cree si absent. Idempotent.
     * Utilise par l'inscription et par tous les endpoints "/me".
     */
    @Transactional
    public Wallet getOrCreate(Long userId) {
        return walletRepository.findByUserId(userId).orElseGet(() -> createForUser(userId));
    }

    @Transactional
    public Wallet createForUser(Long userId) {
        if (walletRepository.existsByUserId(userId)) {
            return walletRepository.findByUserId(userId).orElseThrow();
        }
        Investisseur user = userRepository.findById(userId)
                .filter(u -> u instanceof Investisseur)
                .map(u -> (Investisseur) u)
                .orElseThrow(() -> new EntityNotFoundException("User introuvable : " + userId));

        Wallet w = new Wallet();
        w.setUser(user);
        w.setSolde(BigDecimal.ZERO);
        w.setDevise("USD");
        Wallet saved = walletRepository.save(w);
        log.info("Wallet cree pour user {} : id={}", userId, saved.getId());
        return saved;
    }

    // =========================================================================
    // Lecture
    // =========================================================================

    public WalletResponse getResponseByUserId(Long userId) {
        Wallet w = getOrCreate(userId);
        return toResponse(w);
    }

    public List<WalletTransactionResponse> listTransactions(Long userId) {
        Wallet w = getOrCreate(userId);
        return walletTransactionRepository
                .findByWalletIdOrderByCreatedAtDesc(w.getId())
                .stream().map(this::toResponse).toList();
    }

    public List<WalletTransactionResponse> listTransactionsFiltered(
            Long userId,
            TypeWalletTransaction type,
            LocalDateTime from,
            LocalDateTime to) {
        Wallet w = getOrCreate(userId);
        return walletTransactionRepository
                .findFiltered(w.getId(), type, from, to)
                .stream().map(this::toResponse).toList();
    }

    /**
     * Stats agregees pour KPIs : total credite, total debite, dernier mouvement.
     */
    public Map<String, Object> stats(Long userId) {
        Wallet w = getOrCreate(userId);
        List<WalletTransaction> all = walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(w.getId());

        BigDecimal totalCredite = all.stream()
                .map(WalletTransaction::getMontant)
                .filter(m -> m.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDebite = all.stream()
                .map(WalletTransaction::getMontant)
                .filter(m -> m.signum() < 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .abs();

        LocalDateTime dernierMouvement = all.isEmpty() ? null : all.get(0).getCreatedAt();

        return Map.of(
                "solde", w.getSolde(),
                "devise", w.getDevise(),
                "totalCredite", totalCredite,
                "totalDebite", totalDebite,
                "nbMouvements", all.size(),
                "dernierMouvement", dernierMouvement == null ? "" : dernierMouvement.toString()
        );
    }

    // =========================================================================
    // Mouvements (credit / debit)
    // =========================================================================

    /**
     * Credite le wallet (montant > 0). Cree une WalletTransaction et MAJ le solde.
     * A appeler depuis les services metiers (distribution dividende, vente parts, refund, ...).
     */
    @Transactional
    public WalletTransaction credit(Long userId, BigDecimal montant, TypeWalletTransaction type,
                                     String libelle, String refTable, Long refId, String metadata) {
        if (montant == null || montant.signum() <= 0) {
            throw new IllegalArgumentException("Le montant credite doit etre strictement positif.");
        }
        Wallet w = getOrCreate(userId);
        BigDecimal montantNormalise = montant.setScale(2, RoundingMode.HALF_UP);
        BigDecimal nouveauSolde = w.getSolde().add(montantNormalise);
        w.setSolde(nouveauSolde);
        walletRepository.save(w);

        WalletTransaction tx = new WalletTransaction();
        tx.setWallet(w);
        tx.setType(type);
        tx.setMontant(montantNormalise);
        tx.setSoldeApres(nouveauSolde);
        tx.setLibelle(libelle);
        tx.setRefTable(refTable);
        tx.setRefId(refId);
        tx.setMetadata(metadata);
        WalletTransaction saved = walletTransactionRepository.save(tx);

        log.info("Wallet {} : credit +{} USD ({}). Nouveau solde : {} USD",
                w.getId(), montantNormalise, type, nouveauSolde);
        return saved;
    }

    /**
     * Debite le wallet (montant > 0, sera enregistre en negatif). Refuse si solde insuffisant.
     * A appeler depuis les services metiers (achat parts, withdraw, etc.).
     */
    @Transactional
    public WalletTransaction debit(Long userId, BigDecimal montant, TypeWalletTransaction type,
                                    String libelle, String refTable, Long refId, String metadata) {
        if (montant == null || montant.signum() <= 0) {
            throw new IllegalArgumentException("Le montant debite doit etre strictement positif.");
        }
        Wallet w = getOrCreate(userId);
        BigDecimal montantNormalise = montant.setScale(2, RoundingMode.HALF_UP);

        if (w.getSolde().compareTo(montantNormalise) < 0) {
            throw new InsufficientFundsException(w.getSolde(), montantNormalise);
        }

        BigDecimal nouveauSolde = w.getSolde().subtract(montantNormalise);
        w.setSolde(nouveauSolde);
        walletRepository.save(w);

        WalletTransaction tx = new WalletTransaction();
        tx.setWallet(w);
        tx.setType(type);
        tx.setMontant(montantNormalise.negate());
        tx.setSoldeApres(nouveauSolde);
        tx.setLibelle(libelle);
        tx.setRefTable(refTable);
        tx.setRefId(refId);
        tx.setMetadata(metadata);
        WalletTransaction saved = walletTransactionRepository.save(tx);

        log.info("Wallet {} : debit -{} USD ({}). Nouveau solde : {} USD",
                w.getId(), montantNormalise, type, nouveauSolde);
        return saved;
    }

    /**
     * Recharge mock (mode demo) : credite directement le wallet sans PSP reel.
     * A remplacer par une vraie integration (Yellow Card / Mobile Money) avant la prod.
     * Le plafond par recharge est valide en amont par RechargeRequest (@DecimalMax).
     */
    @Transactional
    public WalletTransaction rechargerMock(Long userId, BigDecimal montant, String methode) {
        String suffixe = (methode == null || methode.isBlank()) ? "" : " via " + methode.trim();
        return credit(userId, montant, TypeWalletTransaction.TOPUP,
                "Recharge (demo)" + suffixe, null, null, null);
    }

    /**
     * Ajustement admin (peut etre positif ou negatif). Trace le motif obligatoire.
     */
    @Transactional
    public WalletTransaction ajustementAdmin(Long userId, BigDecimal montantSigne, String motif) {
        if (montantSigne == null || montantSigne.signum() == 0) {
            throw new IllegalArgumentException("Le montant d'ajustement ne peut etre nul.");
        }
        if (motif == null || motif.isBlank()) {
            throw new IllegalArgumentException("Le motif est obligatoire pour un ajustement admin.");
        }
        String motifClean = motif.trim();
        if (montantSigne.signum() > 0) {
            return credit(userId, montantSigne, TypeWalletTransaction.AJUSTEMENT_ADMIN,
                    "Ajustement admin : " + motifClean, null, null, null);
        } else {
            return debit(userId, montantSigne.abs(), TypeWalletTransaction.AJUSTEMENT_ADMIN,
                    "Ajustement admin : " + motifClean, null, null, null);
        }
    }

    // =========================================================================
    // Admin
    // =========================================================================

    public List<WalletResponse> listAllWallets() {
        return walletRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public Optional<Wallet> findByUserId(Long userId) {
        return walletRepository.findByUserId(userId);
    }

    public List<WalletTransactionResponse> listTransactionsForAdmin(Long userId) {
        return walletRepository.findByUserId(userId)
                .map(w -> walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(w.getId())
                        .stream().map(this::toResponse).toList())
                .orElse(List.of());
    }

    // =========================================================================
    // Mapping DTOs
    // =========================================================================

    public WalletResponse toResponse(Wallet w) {
        Investisseur u = w.getUser();
        return new WalletResponse(
                w.getId(),
                u == null ? null : u.getId(),
                u == null ? null : u.getEmail(),
                u == null ? null : u.getNom(),
                u == null ? null : u.getPrenom(),
                w.getSolde(),
                w.getDevise(),
                w.getCreatedAt(),
                w.getUpdatedAt()
        );
    }

    public WalletTransactionResponse toResponse(WalletTransaction t) {
        return new WalletTransactionResponse(
                t.getId(),
                t.getWallet().getId(),
                t.getType(),
                t.getMontant(),
                t.getSoldeApres(),
                t.getLibelle(),
                t.getRefTable(),
                t.getRefId(),
                t.getMetadata(),
                t.getCreatedAt()
        );
    }
}
