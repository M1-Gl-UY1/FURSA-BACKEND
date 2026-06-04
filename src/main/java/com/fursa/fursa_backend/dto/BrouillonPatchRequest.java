package com.fursa.fursa_backend.dto;

import com.fursa.fursa_backend.model.enumeration.SourceRevenu;
import com.fursa.fursa_backend.model.enumeration.StatutExploitation;
import com.fursa.fursa_backend.model.enumeration.TypeBien;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Update partiel d'un brouillon de propriete (wizard auto-save).
 *
 * <p>TOUS les champs sont optionnels : le service applique uniquement ceux non-null.
 * Aucune validation @NotBlank/@NotNull ici — la validation stricte n'a lieu qu'a la
 * finalisation (cf ProprieteBrouillonService.finaliser).
 *
 * <p>Cas d'usage : l'investisseur clique "Continuer" sur l'etape Localisation,
 * le front envoie un PATCH avec uniquement {nom, pays, ville, adressePrecise,
 * description}. Le serveur met a jour la propriete sans rien casser des autres
 * champs deja remplis (etape 2 ou 3 si l'user revient sur l'etape 1 et corrige).
 */
@Getter
@Setter
public class BrouillonPatchRequest {

    // Etape 1 : localisation
    private String nom;
    private String pays;
    private String ville;
    private String adressePrecise;
    private String description;

    // Etape 2 : type & equipements
    private TypeBien typeBien;
    /** V2 G.3 (04/06/2026) : code admin-configurable. Prime sur typeBien si fourni. */
    private String typeBienCode;
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
     * V2 G.1 (04/06/2026) : codes des equipements admin-configurables.
     * Optionnel. Si fourni (meme vide), remplace la selection courante du brouillon.
     */
    private List<String> equipementsCodes;

    // Etape 3 : exploitation
    private StatutExploitation statutExploitation;
    private LocalDate dateLivraisonPrevue;
    private BigDecimal revenuMensuelActuel;
    private SourceRevenu sourceRevenu;

    // Etape 4 : finance
    private BigDecimal prixVenteTotal;
    private String deviseLocale;
    private Integer fractionVenduePct;
    private Integer nombreTotalPart;
    private BigDecimal prixUnitairePart;
    private Double rentabilitePrevue;
}
