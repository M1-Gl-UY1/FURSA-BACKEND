package com.fursa.fursa_backend.dto;

import com.fursa.fursa_backend.model.enumeration.SourceRevenu;
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

    // --- Phase 7 : workflow soumission propriétaire ---
    private Long proposeurId;
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
    private StatutExploitation statutExploitation;
    private BigDecimal revenuMensuelActuel;
    private SourceRevenu sourceRevenu;
    private BigDecimal prixVenteTotal;
    private String deviseLocale;
    private Integer fractionVenduePct;
    private String videoUrl;
    private Boolean certifie;
    private LocalDateTime certifieLe;
}
