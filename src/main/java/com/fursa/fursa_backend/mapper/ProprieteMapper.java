package com.fursa.fursa_backend.mapper;

import com.fursa.fursa_backend.dto.DocumentResponse;
import com.fursa.fursa_backend.dto.ProprieteRequest;
import com.fursa.fursa_backend.dto.ProprieteResponse;
import com.fursa.fursa_backend.model.Propriete;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class ProprieteMapper {

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
                .url(d.getUrl())
                .type(d.getType())
                .dateUpload(d.getDateUpload())
                .sectionPhoto(d.getSectionPhoto())
                .build()
            ).toList();

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
                // Phase 7
                .proposeurId(p.getProposeurId())
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
                .build();
    }
}
