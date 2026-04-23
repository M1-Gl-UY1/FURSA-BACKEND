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
                .build()
            ).toList();

        return ProprieteResponse.builder()
                .id(p.getId())
                .nom(p.getNom())
                .localisation(p.getLocalisation())
                .description(p.getDescription())
                .nombreTotalPart(p.getNombreTotalPart())
                .prixUnitairePart(p.getPrixUnitairePart())
                .statut(p.getStatut())
                .rentabilitePrevue(p.getRentabilitePrevue())
                .dateCreation(p.getDateCreation())
                .documents(docs)
                .build();
    }
}