package com.fursa.fursa_backend.service;


import com.fursa.fursa_backend.dto.ProprieteRequest;
import com.fursa.fursa_backend.mapper.ProprieteMapper;
import com.fursa.fursa_backend.model.Document;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.model.enumeration.TypeDocument;
import com.fursa.fursa_backend.repository.DocumentRepository;
import com.fursa.fursa_backend.repository.ProprieteRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;


@Service
@RequiredArgsConstructor
public class ProprieteService {

    private final ProprieteRepository proprieteRepository;
    private final DocumentRepository documentRepository;
    private final FileStorageService fileStorageService;
    private final ProprieteMapper proprieteMapper;

    @Transactional
    public Propriete creerPropriete(ProprieteRequest request, List<MultipartFile> fichiers) {

        Propriete propriete = proprieteMapper.toEntity(request);
        propriete.setDateCreation(LocalDate.now());
        Propriete savedProp = proprieteRepository.save(propriete);

        sauvegarderFichiers(fichiers, savedProp);

        // Recharge avec les documents pour la réponse
        return proprieteRepository.findById(savedProp.getId()).orElseThrow();
    }

    @Transactional
    public Propriete modifierPropriete(Long id, ProprieteRequest request, List<MultipartFile> fichiers) {

        Propriete propriete = proprieteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Propriété introuvable : " + id));

        // Mise à jour des champs
        propriete.setNom(request.getNom());
        propriete.setLocalisation(request.getLocalisation());
        propriete.setDescription(request.getDescription());
        propriete.setNombreTotalPart(request.getNombreTotalPart());
        propriete.setPrixUnitairePart(request.getPrixUnitairePart());
        propriete.setStatut(request.getStatut());
        propriete.setRentabilitePrevue(request.getRentabilitePrevue());

        // Ajout de nouveaux fichiers si fournis (sans supprimer les anciens)
        sauvegarderFichiers(fichiers, propriete);

        return proprieteRepository.save(propriete);
    }

    public List<Propriete> listerTout() {
        return proprieteRepository.findAll();
    }

    public Propriete detail(Long id) {
        return proprieteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Propriété introuvable : " + id));
    }

    @Transactional
public void supprimer(Long id) {
    Propriete propriete = proprieteRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Propriété introuvable : " + id));

    // Supprime les fichiers physiques avant de supprimer en base
    if (propriete.getDocuments() != null) {
        propriete.getDocuments().forEach(doc ->
            fileStorageService.delete(doc.getUrl())
        );
    }

    proprieteRepository.deleteById(id);
}

    //méthode privee
    private void sauvegarderFichiers(List<MultipartFile> fichiers, Propriete propriete) {
        if (fichiers == null || fichiers.isEmpty()) return;

        for (MultipartFile f : fichiers) {
            String nomFichier = fileStorageService.save(f);

            Document doc = new Document();
            doc.setNom(f.getOriginalFilename());
            doc.setUrl(nomFichier);
            doc.setDateUpload(LocalDateTime.now());
            doc.setPropriete(propriete);
            doc.setType(
                f.getContentType() != null && f.getContentType().contains("pdf")
                    ? TypeDocument.PDF
                    : TypeDocument.IMAGE
            );
            documentRepository.save(doc);
        }
    }
}