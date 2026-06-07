package com.fursa.fursa_backend.model;

import com.fursa.fursa_backend.model.enumeration.SourceRevenu;
import com.fursa.fursa_backend.model.enumeration.StatutCertification;
import com.fursa.fursa_backend.model.enumeration.StatutExploitation;
import com.fursa.fursa_backend.model.enumeration.StatutPropriete;
import com.fursa.fursa_backend.model.enumeration.TypeBien;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Propriete {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_prop")
    private Long id;

    private String nom;
    private String localisation;

    @Column(columnDefinition = "TEXT")
    private String description;
    private Integer nombreTotalPart;
    private Integer partsDisponibles;
    private BigDecimal prixUnitairePart;

    @Enumerated(EnumType.STRING)
    private StatutPropriete statut;

    private Double rentabilitePrevue;
    private String images;
    private LocalDate dateCreation;

    // --- Phase 7 : workflow soumission propriétaire ---

    /** ID de l'investisseur qui a proposé ce bien. Null = créé directement par admin. */
    private Long proposeurId;

    /** Motif du refus si statut = REFUSEE. */
    @Column(columnDefinition = "TEXT")
    private String motifRefus;

    /** Date de soumission par un investisseur (null si créé directement par admin). */
    private LocalDateTime soumiseLe;

    @Version
    private Long version;

    @OneToMany(mappedBy = "propriete", cascade = CascadeType.ALL)
    private List<Document> documents;
    
    private String adresseContrat;
    private String transactionHash;

    /**
     * V2 O (07/06/2026) : version du contrat deploye on-chain.
     *   V1 = ProprieteToken (legacy, prix immuable)
     *   V2 = ProprieteTokenV2 (prix mutable + bonus + statut sync via BlockchainSyncService)
     * Null tant que le bien n'a pas ete tokenise.
     */
    @Column(name = "contrat_version", length = 8)
    private String contratVersion;

    @OneToMany(mappedBy = "propriete")
    private List<Revenus> revenus;

    @OneToMany(mappedBy = "propriete")
    private List<Possession> possessions;

    // ========================================================================
    // P1 (reunion Hugh 22/05/2026) : refonte fiche bien
    // ========================================================================

    /** Code ISO 2 lettres du pays (TZ, KE, CI, CM, SN, NG, GH, RW, UG, EG). */
    @Column(name = "pays", length = 2)
    private String pays;

    /** Ville (selection dans la liste des villes principales du pays). */
    @Column(name = "ville", length = 100)
    private String ville;

    /** Adresse precise complementaire (rue, quartier). */
    @Column(name = "adresse_precise", length = 300)
    private String adressePrecise;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_bien", length = 20)
    private TypeBien typeBien;

    /**
     * V2 G.3 (04/06/2026) : type de bien admin-configurable (source de verite).
     * Pour les 7 codes historiques, contient la meme valeur que {@code typeBien}.
     * Pour les codes custom crees par l'admin (LOFT, MAISON_DE_VILLE, ...),
     * {@code typeBien} reste null et seul ce champ porte la valeur.
     */
    @Column(name = "type_bien_code", length = 50)
    private String typeBienCode;

    @Column(name = "nombre_pieces")
    private Integer nombrePieces;

    @Column(name = "nombre_chambres")
    private Integer nombreChambres;

    @Column(name = "superficie_m2")
    private Integer superficieM2;

    @Column(name = "has_piscine", nullable = false)
    private Boolean hasPiscine = false;

    @Column(name = "has_climatisation", nullable = false)
    private Boolean hasClimatisation = false;

    @Column(name = "has_parking", nullable = false)
    private Boolean hasParking = false;

    @Column(name = "has_ascenseur", nullable = false)
    private Boolean hasAscenseur = false;

    @Column(name = "has_jardin", nullable = false)
    private Boolean hasJardin = false;

    @Column(name = "has_vue_mer", nullable = false)
    private Boolean hasVueMer = false;

    /**
     * V2 G.1 (04/06/2026) : equipements admin-configurables.
     * Source de verite pour les NOUVEAUX biens (le wizard ecrit ici).
     * Pour les biens anciens, la migration 025 a backfille les booleens has_xxx
     * vers cette table. Le mapper expose une union des deux sources via
     * equipementsCodes pour la lecture (zero regression frontend).
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "propriete_equipement",
            joinColumns = @JoinColumn(name = "id_prop"),
            inverseJoinColumns = @JoinColumn(name = "id_equipement")
    )
    private Set<Equipement> equipements = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_exploitation", length = 20, nullable = false)
    private StatutExploitation statutExploitation = StatutExploitation.NEUF;

    /**
     * P8b (Hugh 25/05/2026) : date de livraison prevue, utile pour les biens
     * EN_CONSTRUCTION (ex : Paje Square livraison Q4 2028).
     * Null si bien deja livre (NEUF ou DEJA_RENTABLE).
     */
    @Column(name = "date_livraison_prevue")
    private LocalDate dateLivraisonPrevue;

    /** Si DEJA_RENTABLE : revenu mensuel approximatif declare par le proprio. */
    @Column(name = "revenu_mensuel_actuel", precision = 15, scale = 2)
    private BigDecimal revenuMensuelActuel;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_revenu", length = 20)
    private SourceRevenu sourceRevenu;

    /**
     * Prix total demande par le proprio pour son bien (devise locale).
     * La logique de parts (nombreTotalPart, prixUnitairePart) est calculee
     * par la plateforme a partir de ce prix.
     */
    @Column(name = "prix_vente_total", precision = 15, scale = 2)
    private BigDecimal prixVenteTotal;

    /** Devise locale du proprio (XAF, EUR, USD, TZS, etc.). Conversion auto USD pour affichage. */
    @Column(name = "devise_locale", length = 3)
    private String deviseLocale;

    /**
     * Equivalent USD de prix_vente_total, calcule a la soumission via DeviseRateService.
     * Sert de prix de reference partout sur la plateforme (decision Hugh 22/05/2026).
     */
    @Column(name = "prix_vente_total_usd", precision = 15, scale = 2)
    private BigDecimal prixVenteTotalUsd;

    /**
     * Fraction du bien que le proprio met en vente (1-100%).
     * 100 = il vend tout le bien, 50 = il garde la moitie pour lui.
     */
    @Column(name = "fraction_vendue_pct", nullable = false)
    private Integer fractionVenduePct = 100;

    /** URL de la video de visite guidee (obligatoire pour validation prealable). */
    @Column(name = "video_url", length = 500)
    private String videoUrl;

    /** True une fois que tous les documents legaux ont ete uploades + valides par l'admin. */
    @Column(name = "certifie", nullable = false)
    private Boolean certifie = false;

    @Column(name = "certifie_le")
    private LocalDateTime certifieLe;

    // ========================================================================
    // Phase Certification (Hugh 22/05/2026) : etape post-creation separee
    // ========================================================================

    /**
     * Etat de certification du bien (independant du statut de publication).
     * NON_CERTIFIE par defaut. Si CERTIFIE, le bien est achetable.
     * Si autre, le bien est visible mais non achetable.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "statut_certif", nullable = false, length = 20)
    private StatutCertification statutCertif = StatutCertification.NON_CERTIFIE;

    /** Date a laquelle le proprio a soumis sa demande de certification. */
    @Column(name = "certif_soumise_le")
    private LocalDateTime certifSoumiseLe;

    /** Motif du refus si statut_certif = REFUSEE. */
    @Column(name = "certif_motif_refus", length = 500)
    private String certifMotifRefus;

    // ========================================================================
    // P1 (Hugh 22/05/2026) : prix dynamique des parts
    // ========================================================================

    /**
     * Prix unitaire INITIAL (a la creation). Le prix courant reste dans
     * prixUnitairePart et fluctue selon la rentabilite reelle et la demande.
     */
    @Column(name = "prix_initial_part", precision = 15, scale = 2)
    private BigDecimal prixInitialPart;

    /**
     * Bonus cumule de rentabilite (fraction : 0.05 = +5%).
     * Mis a jour a chaque revenu valide ou cron trimestriel.
     */
    @Column(name = "bonus_rentabilite_total", precision = 8, scale = 6, nullable = false)
    private BigDecimal bonusRentabiliteTotal = BigDecimal.ZERO;

    /**
     * Bonus instantane de demande (fraction : 0.10 = +10%).
     * Recalcule a chaque inscription/desinscription liste d'attente.
     */
    @Column(name = "bonus_demande", precision = 8, scale = 6, nullable = false)
    private BigDecimal bonusDemande = BigDecimal.ZERO;

    // ========================================================================
    // P9 (Hugh 22/05/2026) : partenaire de gestion locative assigne
    // ========================================================================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gestionnaire_id")
    private PartenaireGestion gestionnaire;

    // ========================================================================
    // P4 (Hugh 22/05/2026) : modele "FURSA acheteur"
    // ========================================================================

    /**
     * True si FURSA a achete le bien one-time aupres du promoteur (workflow
     * Paje Square) puis le remet en vente fractionnee. Affiche un badge
     * "Acquis FURSA" cote investisseur (gage de fiabilite : le bien est
     * deja sous la responsabilite de la plateforme).
     */
    @Column(name = "acquis_fursa", nullable = false)
    private Boolean acquisFursa = false;
}
