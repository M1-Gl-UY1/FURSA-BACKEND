package com.fursa.fursa_backend.dto;

import com.fursa.fursa_backend.model.enumeration.SourceRevenu;
import com.fursa.fursa_backend.model.enumeration.StatutExploitation;
import com.fursa.fursa_backend.model.enumeration.TypeBien;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Soumission d'un bien immobilier par un investisseur.
 *
 * P1 (reunion Hugh 22/05/2026) : refonte profonde du DTO pour correspondre
 * au nouveau wizard en 7 etapes (pays/ville, type bien, equipements, statut
 * exploitation neuf/rentable, finance avec prix de vente + devise locale +
 * fraction a vendre, photos structurees, video visite guidee).
 *
 * Le statut Propriete est force a EN_REVIEW cote serveur.
 * partsDisponibles = nombreTotalPart a la creation.
 */
@Getter
@Setter
public class SubmissionRequest {

    // --- Etape 1 : Localisation & basiques ---

    @NotBlank(message = "Le nom du bien est obligatoire")
    private String nom;

    @NotBlank(message = "Le pays est obligatoire")
    @Pattern(regexp = "^[A-Z]{2}$", message = "Code pays ISO 2 lettres requis (ex: TZ, KE)")
    private String pays;

    @NotBlank(message = "La ville est obligatoire")
    private String ville;

    /** Adresse precise / quartier. Optionnel. */
    private String adressePrecise;

    /**
     * Conservation du champ "localisation" pour compatibilite ascendante.
     * Calcule automatiquement par le service comme "ville, pays".
     */
    private String localisation;

    private String description;

    // --- Etape 2 : Type & equipements ---

    /**
     * V2 G.3 (04/06/2026) : l'enum n'est plus obligatoire. Le frontend peut
     * envoyer soit cet enum (compat) soit typeBienCode (source de verite v2,
     * accepte les codes custom crees par l'admin). La validation "au moins
     * un des deux" est faite cote service.
     */
    private TypeBien typeBien;

    /**
     * V2 G.3 : code du type de bien admin-configurable. Prime sur typeBien
     * si fourni. Ex "VILLA", "LOFT", "MAISON_DE_VILLE".
     */
    private String typeBienCode;

    private Integer nombrePieces;
    private Integer nombreChambres;
    private Integer superficieM2;

    private Boolean hasPiscine = false;
    private Boolean hasClimatisation = false;
    private Boolean hasParking = false;
    private Boolean hasAscenseur = false;
    private Boolean hasJardin = false;
    private Boolean hasVueMer = false;

    /**
     * V2 G.1 (04/06/2026) : codes des equipements admin-configurables choisis
     * dans le wizard. Si non-null, le service synchronise les booleens hasXxx
     * connus + les liens vers la table Equipement.
     * Format : ["PISCINE", "PARKING", "SALLE_DE_SPORT"]
     */
    private List<String> equipementsCodes;

    // --- Etape 3 : Statut d'exploitation (NEUF | DEJA_RENTABLE) + preuves ---

    @NotNull(message = "Indiquez si le bien est en construction, neuf ou deja rentable")
    private StatutExploitation statutExploitation;

    /**
     * P8b (Hugh 25/05/2026) : obligatoire si statutExploitation = EN_CONSTRUCTION.
     * Date a laquelle le bien sera livre / disponible a l'exploitation.
     */
    private java.time.LocalDate dateLivraisonPrevue;

    /** Obligatoire si statutExploitation = DEJA_RENTABLE. Valide cote service. */
    private BigDecimal revenuMensuelActuel;

    /** Obligatoire si statutExploitation = DEJA_RENTABLE. */
    private SourceRevenu sourceRevenu;

    // --- Etape 4 : Finance ---

    /**
     * Prix de vente TOTAL demande par le proprietaire (devise locale).
     * La plateforme calcule a partir de ce prix + nombreTotalPart le prix unitaire.
     */
    @NotNull(message = "Le prix de vente total est obligatoire")
    @DecimalMin(value = "0.01", message = "Le prix de vente doit etre superieur a 0")
    private BigDecimal prixVenteTotal;

    @NotBlank(message = "La devise locale est obligatoire")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Code devise ISO 3 lettres requis (ex: USD, EUR, XAF)")
    private String deviseLocale;

    /**
     * Fraction du bien que le proprio met en vente (1-100%).
     * 50 = il vend la moitie de son bien. 100 = il vend tout.
     */
    @NotNull
    @Min(value = 1, message = "La fraction a vendre doit etre au moins 1%")
    @Max(value = 100, message = "La fraction a vendre ne peut pas exceder 100%")
    private Integer fractionVenduePct = 100;

    @NotNull
    @Min(value = 1, message = "Le nombre de parts doit etre au moins 1")
    private Integer nombreTotalPart;

    /**
     * Prix unitaire d'une part (= prixVenteTotal * fractionVenduePct / nombreTotalPart).
     *
     * V2 BB (08/06/2026) : minimum 100 USD par part (decision PO).
     * Le ticket d'entree investisseur commence a 100 USD : on refuse les
     * proprietes qui creeraient des parts moins cheres (sinon le seuil
     * promis aux investisseurs est viole).
     */
    @NotNull
    @DecimalMin(value = "100.00", message = "Le prix unitaire d'une part doit etre d'au moins 100 USD. Augmente le prix de vente ou reduis le nombre de parts.")
    private BigDecimal prixUnitairePart;

    /**
     * Rentabilite annuelle previsionnelle en % (ex: 8.0 = 8% par an).
     *
     * V2 GG (08/06/2026) : minimum 5% (decision PO). En dessous, l'attractivite
     * du bien pour les investisseurs FURSA est insuffisante et la confiance
     * dans la plateforme se degrade.
     */
    @NotNull
    @DecimalMin(value = "5.0", message = "La rentabilite annuelle previsionnelle doit etre d'au moins 5%.")
    private Double rentabilitePrevue;

    // --- Etape 6 : Video de visite guidee ---

    /**
     * URL relative de la video uploadee (envoyee dans le multipart de la requete).
     * Le service la calcule a partir du fichier "video" recu.
     */
    private String videoUrl;
}
