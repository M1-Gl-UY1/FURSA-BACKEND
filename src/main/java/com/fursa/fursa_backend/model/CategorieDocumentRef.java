package com.fursa.fursa_backend.model;

import com.fursa.fursa_backend.model.enumeration.RegleObligationDoc;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * V2 G.2 (04/06/2026) : categories de document admin-configurables.
 *
 * <p>Remplace progressivement l'enum {@code CategorieDocument} (qui reste
 * expose en lecture pour retro-compat). La source de verite est le code en
 * string, stocke dans {@code Document.categorieDocumentCode}.
 *
 * <p>L'admin peut creer des categories custom (ASSURANCE_HABITATION,
 * DIAGNOSTIC_DPE, ACTE_NOTARIE, ATTESTATION_FISCALE...) sans devoir redeployer.
 *
 * <p>La regle d'obligation est stockee dans {@code regleObligation} pour
 * permettre a une refonte ulterieure de remplacer la validation hardcodee
 * du finalisateur. Pour V1, les codes historiques utilisent leur regle
 * d'origine et les codes custom sont OPTIONNEL par defaut.
 *
 * <p>Le nom de classe est CategorieDocumentRef (et non CategorieDocument)
 * pour eviter la collision avec l'enum
 * {@code com.fursa.fursa_backend.model.enumeration.CategorieDocument}.
 */
@Entity
@Table(name = "categorie_document")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class CategorieDocumentRef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_categorie_doc")
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(length = 500)
    private String description;

    @Column(length = 50)
    private String icone;

    @Column(name = "ordre_affichage", nullable = false)
    private Integer ordre = 100;

    @Column(nullable = false)
    private Boolean actif = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "regle_obligation", nullable = false, length = 30)
    private RegleObligationDoc regleObligation = RegleObligationDoc.OPTIONNEL;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
