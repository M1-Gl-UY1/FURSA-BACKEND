package com.fursa.fursa_backend.dto;

import com.fursa.fursa_backend.model.enumeration.SourceRevenu;
import com.fursa.fursa_backend.model.enumeration.StatutCertification;
import com.fursa.fursa_backend.model.enumeration.StatutExploitation;
import com.fursa.fursa_backend.model.enumeration.StatutPropriete;
import com.fursa.fursa_backend.model.enumeration.TypeBien;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ProprieteResponse {
    private Long id;
    private String nom;
    private String localisation;
    private String description;
    private Integer nombreTotalPart;
    private Integer partsDisponibles;
    private BigDecimal prixUnitairePart;
    private StatutPropriete statut;
    private Double rentabilitePrevue;
    private LocalDate dateCreation;
    private List<DocumentResponse> documents;
    /**
     * URLs des photos uploadees pour ce bien (extraites des documents type IMAGE
     * avec section photo non-null). Premiere photo = cover dans le catalogue.
     * URL renvoyee en chemin relatif "/api/fichiers/xxx" — le frontend prefixe
     * avec l'API base via resolveFileUrl().
     */
    private List<String> photos;

    // --- Phase 7 : workflow soumission propriétaire ---
    private Long proposeurId;
    /** Prenom + initiale du nom du proposeur (ex "Marie D."). Pas exposer le nom complet pour RGPD. */
    private String proposeurNom;
    /** True si le proposeur a complete son KYC. Affiche un badge "verifie" cote frontend. */
    private Boolean proposeurIsVerified;
    private String motifRefus;
    private LocalDateTime soumiseLe;

    // --- Blockchain : tokenisation propriete ---
    private String adresseContrat;
    private String transactionHash;

    // --- P1 (reunion Hugh 22/05/2026) : refonte fiche bien ---
    private String pays;
    private String ville;
    private String adressePrecise;
    private TypeBien typeBien;
    private Integer nombrePieces;
    private Integer nombreChambres;
    private Integer superficieM2;
    private Boolean hasPiscine;
    private Boolean hasClimatisation;
    private Boolean hasParking;
    private Boolean hasAscenseur;
    private Boolean hasJardin;
    private Boolean hasVueMer;
    /**
     * V2 G.1 (04/06/2026) : codes des equipements (admin-configurables).
     * Union des booleens hasXxx et des entites Equipement liees au bien.
     * Source de verite pour les NOUVEAUX clients ; les booleens hasXxx restent
     * exposes pour la retro-compat.
     */
    private List<String> equipementsCodes;
    private StatutExploitation statutExploitation;
    /** P8b (Hugh 25/05/2026) : date prevue de livraison si EN_CONSTRUCTION. */
    private LocalDate dateLivraisonPrevue;
    private BigDecimal revenuMensuelActuel;
    private SourceRevenu sourceRevenu;
    private BigDecimal prixVenteTotal;
    private String deviseLocale;
    /** Equivalent USD calcule a la soumission (P5 Hugh 22/05/2026). */
    private BigDecimal prixVenteTotalUsd;
    private Integer fractionVenduePct;
    private String videoUrl;
    private Boolean certifie;
    private LocalDateTime certifieLe;
    // Phase Certification (Hugh 22/05/2026)
    private StatutCertification statutCertif;
    private LocalDateTime certifSoumiseLe;
    private String certifMotifRefus;

    // --- P1 (Hugh 22/05/2026) : prix dynamique ---
    // Voir PRIX_DYNAMIQUE_FURSA.md a la racine du projet.
    /** Prix unitaire INITIAL a la creation (le prixUnitairePart est le prix COURANT). */
    private BigDecimal prixInitialPart;
    /** Cumul des contributions de rentabilite (fraction : 0.05 = +5%). */
    private BigDecimal bonusRentabiliteTotal;
    /** Bonus instantane de demande (fraction : 0.10 = +10%). */
    private BigDecimal bonusDemande;

    // --- P9 (Hugh 22/05/2026) : gestionnaire locatif assigne ---
    private PartenaireGestionResponse gestionnaire;

    // --- P4 (Hugh 22/05/2026) : modele FURSA acheteur ---
    private Boolean acquisFursa;
}
