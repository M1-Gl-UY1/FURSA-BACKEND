package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.dto.KycAdminResponse;
import com.fursa.fursa_backend.dto.KycSubmissionResponse;
import com.fursa.fursa_backend.dto.KycSubmitRequest;
import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.model.KycSubmission;
import com.fursa.fursa_backend.model.enumeration.StatutKyc;
import com.fursa.fursa_backend.repository.InvestisseurRepository;
import com.fursa.fursa_backend.repository.KycSubmissionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Workflow KYC manuel (Phase 1) :
 * - Investisseur soumet ses docs + formulaire -> PENDING
 * - Admin examine, approve ou reject avec motif
 * - A l'APPROVED : investisseur.isVerified = true
 * - A la rejection : possible de re-submit (nombreReSubmissions++)
 *
 * Phase 2 (post-entite-juridique FURSA) : on remplace le wizard front par le widget
 * Smile Identity / Sumsub, et un webhook PSP appelle approve/reject automatiquement
 * pour 95% des cas. Cette classe sera reutilisee pour les cas borderline.
 */
@Service
public class KycService {

    private static final Logger log = LoggerFactory.getLogger(KycService.class);
    private static final String FILE_BASE_PATH = "/api/fichiers/";

    private final KycSubmissionRepository kycRepository;
    private final InvestisseurRepository investisseurRepository;
    private final FileStorageService fileStorageService;

    public KycService(KycSubmissionRepository kycRepository,
                      InvestisseurRepository investisseurRepository,
                      FileStorageService fileStorageService) {
        this.kycRepository = kycRepository;
        this.investisseurRepository = investisseurRepository;
        this.fileStorageService = fileStorageService;
    }

    // =========================================================================
    // Cote investisseur
    // =========================================================================

    /**
     * Soumet un nouveau dossier KYC. Refuse si une soumission PENDING/IN_REVIEW existe deja
     * (un investisseur ne peut pas avoir 2 soumissions actives en parallele).
     */
    @Transactional
    public KycSubmissionResponse submit(Long investisseurId,
                                         KycSubmitRequest req,
                                         MultipartFile documentIdentite,
                                         MultipartFile documentDomicile,
                                         MultipartFile selfie) {
        Investisseur inv = investisseurRepository.findById(investisseurId)
                .orElseThrow(() -> new EntityNotFoundException("Investisseur introuvable : " + investisseurId));

        // Empeche la double-soumission active
        Optional<KycSubmission> existing = kycRepository.findFirstByInvestisseur_IdOrderBySubmittedAtDesc(investisseurId);
        if (existing.isPresent()) {
            StatutKyc s = existing.get().getStatut();
            if (s == StatutKyc.PENDING || s == StatutKyc.IN_REVIEW) {
                throw new IllegalStateException(
                        "Vous avez deja un dossier KYC en cours d'examen. Patientez ou contactez le support.");
            }
            if (s == StatutKyc.APPROVED) {
                throw new IllegalStateException("Votre identite est deja verifiee. Aucune nouvelle soumission necessaire.");
            }
        }

        // Stockage des fichiers (validation extension + MIME deleguee a FileStorageService)
        if (documentIdentite == null || documentIdentite.isEmpty()) {
            throw new IllegalArgumentException("Document d'identite obligatoire");
        }
        if (documentDomicile == null || documentDomicile.isEmpty()) {
            throw new IllegalArgumentException("Justificatif de domicile obligatoire");
        }
        if (selfie == null || selfie.isEmpty()) {
            throw new IllegalArgumentException("Selfie obligatoire");
        }

        String idFile = fileStorageService.save(documentIdentite);
        String domFile = fileStorageService.save(documentDomicile);
        String selfieFile = fileStorageService.save(selfie);

        int previousAttempts = existing
                .map(KycSubmission::getNombreReSubmissions)
                .map(n -> n + 1)
                .orElse(0);

        KycSubmission ks = new KycSubmission();
        ks.setInvestisseur(inv);
        ks.setStatut(StatutKyc.PENDING);
        ks.setNationalite(req.nationalite());
        ks.setDateNaissance(req.dateNaissance());
        ks.setPaysResidence(req.paysResidence());
        ks.setAdresse(req.adresse());
        ks.setDocumentIdentiteUrl(FILE_BASE_PATH + idFile);
        ks.setDocumentDomicileUrl(FILE_BASE_PATH + domFile);
        ks.setSelfieUrl(FILE_BASE_PATH + selfieFile);
        ks.setSourceFonds(req.sourceFonds());
        ks.setIsPep(req.isPep());
        ks.setDeclarationSurHonneur(req.declarationSurHonneur());
        ks.setSubmittedAt(LocalDateTime.now());
        ks.setNombreReSubmissions(previousAttempts);
        ks = kycRepository.save(ks);

        log.info("KYC submit : investisseur={} kycId={} (re-submissions={})",
                investisseurId, ks.getId(), previousAttempts);

        return toInvestisseurResponse(ks);
    }

    /** Statut courant du KYC pour l'investisseur (sa derniere soumission). */
    public Optional<KycSubmissionResponse> findMine(Long investisseurId) {
        return kycRepository.findFirstByInvestisseur_IdOrderBySubmittedAtDesc(investisseurId)
                .map(this::toInvestisseurResponse);
    }

    /** Historique complet (utile en page profil). */
    public List<KycSubmissionResponse> findAllMine(Long investisseurId) {
        return kycRepository.findByInvestisseur_IdOrderBySubmittedAtDesc(investisseurId).stream()
                .map(this::toInvestisseurResponse)
                .toList();
    }

    // =========================================================================
    // Cote admin
    // =========================================================================

    public List<KycAdminResponse> listByStatut(StatutKyc statut) {
        return kycRepository.findByStatutOrderBySubmittedAtDesc(statut).stream()
                .map(this::toAdminResponse)
                .toList();
    }

    public KycAdminResponse findByIdForAdmin(Long id) {
        return kycRepository.findById(id)
                .map(this::toAdminResponse)
                .orElseThrow(() -> new EntityNotFoundException("KYC introuvable : " + id));
    }

    @Transactional
    public KycAdminResponse approve(Long kycId, Long adminId) {
        KycSubmission ks = kycRepository.findById(kycId)
                .orElseThrow(() -> new EntityNotFoundException("KYC introuvable : " + kycId));

        if (ks.getStatut() != StatutKyc.PENDING && ks.getStatut() != StatutKyc.IN_REVIEW) {
            throw new IllegalStateException("Impossible d'approuver : statut actuel = " + ks.getStatut());
        }

        ks.setStatut(StatutKyc.APPROVED);
        ks.setReviewedAt(LocalDateTime.now());
        ks.setReviewedByAdminId(adminId);
        ks.setMotifRefus(null);
        kycRepository.save(ks);

        // Marque l'investisseur comme verifie
        Investisseur inv = ks.getInvestisseur();
        inv.setIsVerified(true);
        investisseurRepository.save(inv);

        log.info("KYC APPROVED : kycId={} investisseur={} par admin={}", kycId, inv.getId(), adminId);
        // TODO Phase 2 : envoyer Notification investisseur "Votre identite est verifiee"

        return toAdminResponse(ks);
    }

    @Transactional
    public KycAdminResponse reject(Long kycId, Long adminId, String motif) {
        KycSubmission ks = kycRepository.findById(kycId)
                .orElseThrow(() -> new EntityNotFoundException("KYC introuvable : " + kycId));

        if (ks.getStatut() != StatutKyc.PENDING && ks.getStatut() != StatutKyc.IN_REVIEW) {
            throw new IllegalStateException("Impossible de rejeter : statut actuel = " + ks.getStatut());
        }
        if (motif == null || motif.isBlank()) {
            throw new IllegalArgumentException("Motif de refus obligatoire");
        }

        ks.setStatut(StatutKyc.REJECTED);
        ks.setReviewedAt(LocalDateTime.now());
        ks.setReviewedByAdminId(adminId);
        ks.setMotifRefus(motif);
        kycRepository.save(ks);

        log.info("KYC REJECTED : kycId={} investisseur={} par admin={} motif={}",
                kycId, ks.getInvestisseur().getId(), adminId, motif);
        // TODO Phase 2 : envoyer Notification investisseur avec motif

        return toAdminResponse(ks);
    }

    @Transactional
    public KycAdminResponse markInReview(Long kycId, Long adminId) {
        KycSubmission ks = kycRepository.findById(kycId)
                .orElseThrow(() -> new EntityNotFoundException("KYC introuvable : " + kycId));

        if (ks.getStatut() != StatutKyc.PENDING) {
            throw new IllegalStateException("Cette soumission n'est pas PENDING (actuel : " + ks.getStatut() + ")");
        }
        ks.setStatut(StatutKyc.IN_REVIEW);
        ks.setReviewedByAdminId(adminId);
        kycRepository.save(ks);
        return toAdminResponse(ks);
    }

    // =========================================================================
    // Mappers
    // =========================================================================

    private KycSubmissionResponse toInvestisseurResponse(KycSubmission ks) {
        return new KycSubmissionResponse(
                ks.getId(),
                ks.getStatut().name(),
                ks.getNationalite(),
                ks.getDateNaissance(),
                ks.getPaysResidence(),
                ks.getAdresse(),
                ks.getDocumentIdentiteUrl(),
                ks.getDocumentDomicileUrl(),
                ks.getSelfieUrl(),
                ks.getSourceFonds() == null ? null : ks.getSourceFonds().name(),
                ks.getIsPep(),
                ks.getSubmittedAt(),
                ks.getReviewedAt(),
                ks.getMotifRefus(),
                ks.getNombreReSubmissions()
        );
    }

    private KycAdminResponse toAdminResponse(KycSubmission ks) {
        Investisseur inv = ks.getInvestisseur();
        return new KycAdminResponse(
                ks.getId(),
                ks.getStatut().name(),
                inv.getId(),
                inv.getEmail(),
                inv.getNom(),
                inv.getPrenom(),
                inv.getTelephone(),
                inv.getIsVerified(),
                ks.getNationalite(),
                ks.getDateNaissance(),
                ks.getPaysResidence(),
                ks.getAdresse(),
                ks.getDocumentIdentiteUrl(),
                ks.getDocumentDomicileUrl(),
                ks.getSelfieUrl(),
                ks.getSourceFonds() == null ? null : ks.getSourceFonds().name(),
                ks.getIsPep(),
                ks.getDeclarationSurHonneur(),
                ks.getSubmittedAt(),
                ks.getReviewedAt(),
                ks.getReviewedByAdminId(),
                ks.getMotifRefus(),
                ks.getNombreReSubmissions()
        );
    }
}
