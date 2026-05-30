package com.fursa.fursa_backend.mapper;

import com.fursa.fursa_backend.dto.DocumentResponse;
import com.fursa.fursa_backend.dto.PartenaireGestionResponse;
import com.fursa.fursa_backend.dto.ProprieteRequest;
import com.fursa.fursa_backend.dto.ProprieteResponse;
import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.model.PartenaireGestion;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.repository.InvestisseurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ProprieteMapper {

    private final InvestisseurRepository investisseurRepository;

    /**
     * Fix 25/05/2026 : standardise l'URL d'un document.
     * Le backend stocke "abc.jpg" en base (juste le nom), mais le frontend
     * attend "/api/fichiers/abc.jpg" pour pouvoir telecharger. On normalise ici.
     */
    private String toFichierUrl(String url) {
        if (url == null || url.isBlank()) return url;
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        if (url.startsWith("/api/fichiers/")) return url;
        if (url.startsWith("/")) return url;
        return "/api/fichiers/" + url;
    }

    public Propriete toEntity(ProprieteRequest req) {
        Propriete p = new Propriete();
        p.setNom(req.getNom());
        p.setLocalisation(req.getLocalisation());
        p.setDescription(req.getDescription());
        p.setNombreTotalPart(req.getNombreTotalPart());
        // À la création, toutes les parts sont disponibles
        p.setPartsDisponibles(req.getNombreTotalPart());
        p.setPrixUnitairePart(req.getPrixUnitairePart());
        p.setStatut(req.getStatut());
        p.setRentabilitePrevue(req.getRentabilitePrevue());
        
        return p;
    }

    public ProprieteResponse toResponse(Propriete p) {
        List<DocumentResponse> docs = p.getDocuments() == null
            ? Collections.emptyList()
            : p.getDocuments().stream().map(d -> DocumentResponse.builder()
                .id(d.getId())
                .nom(d.getNom())
                .url(toFichierUrl(d.getUrl()))
                .type(d.getType())
                .dateUpload(d.getDateUpload())
                .sectionPhoto(d.getSectionPhoto())
                .categorieDocument(d.getCategorieDocument())
                .build()
            ).toList();

        // Fix 25/05/2026 : extraire la liste des URLs des photos pour les cards.
        // On garde uniquement les documents de type IMAGE avec une sectionPhoto definie
        // (= photos structurees uploadees via le wizard).
        List<String> photos = p.getDocuments() == null
            ? Collections.emptyList()
            : p.getDocuments().stream()
                .filter(d -> d.getType() == com.fursa.fursa_backend.model.enumeration.TypeDocument.IMAGE)
                .filter(d -> d.getUrl() != null && !d.getUrl().isBlank())
                .map(d -> toFichierUrl(d.getUrl()))
                .toList();

        // Resolution proposeur (nom anonymise + statut KYC) pour badge "verifie".
        // N+1 accepte sur les listes (volume bas en MVP, pas de tri/filtre par proposeur).
        String proposeurNomAnon = null;
        Boolean proposeurVerifie = null;
        if (p.getProposeurId() != null) {
            Investisseur prop = investisseurRepository.findById(p.getProposeurId()).orElse(null);
            if (prop != null) {
                String prenom = prop.getPrenom() == null ? "" : prop.getPrenom().trim();
                String nom = prop.getNom() == null ? "" : prop.getNom().trim();
                String initiale = nom.isEmpty() ? "" : (" " + nom.substring(0, 1).toUpperCase() + ".");
                proposeurNomAnon = (prenom + initiale).trim();
                if (proposeurNomAnon.isEmpty()) proposeurNomAnon = null;
                proposeurVerifie = prop.getIsVerified();
            }
        }

        return ProprieteResponse.builder()
                .id(p.getId())
                .nom(p.getNom())
                .localisation(p.getLocalisation())
                .description(p.getDescription())
                .nombreTotalPart(p.getNombreTotalPart())
                .partsDisponibles(p.getPartsDisponibles())
                .prixUnitairePart(p.getPrixUnitairePart())
                .statut(p.getStatut())
                .rentabilitePrevue(p.getRentabilitePrevue())
                .dateCreation(p.getDateCreation())
                .documents(docs)
                .photos(photos)
                // Phase 7
                .proposeurId(p.getProposeurId())
                .proposeurNom(proposeurNomAnon)
                .proposeurIsVerified(proposeurVerifie)
                .motifRefus(p.getMotifRefus())
                .soumiseLe(p.getSoumiseLe())
                // Blockchain
                .adresseContrat(p.getAdresseContrat())
                .transactionHash(p.getTransactionHash())
                // P1 (Hugh 22/05/2026)
                .pays(p.getPays())
                .ville(p.getVille())
                .adressePrecise(p.getAdressePrecise())
                .typeBien(p.getTypeBien())
                .nombrePieces(p.getNombrePieces())
                .nombreChambres(p.getNombreChambres())
                .superficieM2(p.getSuperficieM2())
                .hasPiscine(p.getHasPiscine())
                .hasClimatisation(p.getHasClimatisation())
                .hasParking(p.getHasParking())
                .hasAscenseur(p.getHasAscenseur())
                .hasJardin(p.getHasJardin())
                .hasVueMer(p.getHasVueMer())
                .statutExploitation(p.getStatutExploitation())
                .dateLivraisonPrevue(p.getDateLivraisonPrevue())
                .revenuMensuelActuel(p.getRevenuMensuelActuel())
                .sourceRevenu(p.getSourceRevenu())
                .prixVenteTotal(p.getPrixVenteTotal())
                .deviseLocale(p.getDeviseLocale())
                .prixVenteTotalUsd(p.getPrixVenteTotalUsd())
                .fractionVenduePct(p.getFractionVenduePct())
                .videoUrl(p.getVideoUrl())
                .certifie(p.getCertifie())
                .certifieLe(p.getCertifieLe())
                // Phase Certification (Hugh 22/05/2026)
                .statutCertif(p.getStatutCertif())
                .certifSoumiseLe(p.getCertifSoumiseLe())
                .certifMotifRefus(p.getCertifMotifRefus())
                // P1 (Hugh 22/05/2026) : prix dynamique
                .prixInitialPart(p.getPrixInitialPart())
                .bonusRentabiliteTotal(p.getBonusRentabiliteTotal())
                .bonusDemande(p.getBonusDemande())
                // P9 (Hugh 22/05/2026) : gestionnaire locatif
                .gestionnaire(toPartenaireResponse(p.getGestionnaire()))
                // P4 (Hugh 22/05/2026) : modele FURSA acheteur
                .acquisFursa(p.getAcquisFursa())
                .build();
    }

    private PartenaireGestionResponse toPartenaireResponse(PartenaireGestion g) {
        if (g == null) return null;
        return new PartenaireGestionResponse(
                g.getId(),
                g.getNom(),
                g.getTypePartenaire(),
                g.getDescription(),
                g.getSiteWeb(),
                g.getContactEmail(),
                g.getLogoUrl(),
                g.getActif()
        );
    }
}
