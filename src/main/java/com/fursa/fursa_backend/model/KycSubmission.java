package com.fursa.fursa_backend.model;

import com.fursa.fursa_backend.model.enumeration.SourceFonds;
import com.fursa.fursa_backend.model.enumeration.StatutKyc;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Soumission KYC d'un investisseur.
 *
 * Workflow type :
 *   1. Investisseur upload ses docs + remplit le formulaire -> statut PENDING
 *   2. Admin ouvre le detail dans le backoffice -> statut IN_REVIEW (optionnel)
 *   3. Admin clique Approve -> statut APPROVED + investisseur.isVerified=true
 *      OU Admin clique Reject (motif) -> statut REJECTED, possibilite de re-submit
 *   4. Apres 12-24 mois, expiration automatique -> statut EXPIRED, re-submission requise
 */
@Entity
@Table(
        name = "kyc_submission",
        indexes = {
                @Index(name = "idx_kyc_id_inv", columnList = "id_inv"),
                @Index(name = "idx_kyc_statut", columnList = "statut"),
                @Index(name = "idx_kyc_submitted_at", columnList = "submitted_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class KycSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_inv", nullable = false)
    private Investisseur investisseur;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutKyc statut;

    // === Identite ===
    @Column(length = 100)
    private String nationalite;

    @Column(name = "date_naissance")
    private LocalDate dateNaissance;

    @Column(length = 100, name = "pays_residence")
    private String paysResidence;

    @Column(columnDefinition = "TEXT")
    private String adresse;

    // === Documents (path/url servi par FileController) ===
    @Column(name = "document_identite_url", length = 500)
    private String documentIdentiteUrl;

    @Column(name = "document_domicile_url", length = 500)
    private String documentDomicileUrl;

    @Column(name = "selfie_url", length = 500)
    private String selfieUrl;

    // === AML ===
    @Enumerated(EnumType.STRING)
    @Column(name = "source_fonds", length = 50)
    private SourceFonds sourceFonds;

    @Column(name = "is_pep")
    private Boolean isPep;

    @Column(name = "declaration_sur_honneur")
    private Boolean declarationSurHonneur;

    // === Audit ===
    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewed_by_admin_id")
    private Long reviewedByAdminId;

    @Column(name = "motif_refus", columnDefinition = "TEXT")
    private String motifRefus;

    @Column(name = "nombre_re_submissions", nullable = false)
    private Integer nombreReSubmissions = 0;

    @Version
    private Long version;
}
