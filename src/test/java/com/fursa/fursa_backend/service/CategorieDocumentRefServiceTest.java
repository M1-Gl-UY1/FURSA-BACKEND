package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.dto.CategorieDocumentResponse;
import com.fursa.fursa_backend.model.CategorieDocumentRef;
import com.fursa.fursa_backend.model.Document;
import com.fursa.fursa_backend.model.enumeration.CategorieDocument;
import com.fursa.fursa_backend.model.enumeration.RegleObligationDoc;
import com.fursa.fursa_backend.model.enumeration.StatutExploitation;
import com.fursa.fursa_backend.repository.CategorieDocumentRefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * V2 H.6 (06/06/2026) : tests unitaires du service categories de document
 * (helper applyToDocument + validation dynamique des obligations).
 */
class CategorieDocumentRefServiceTest {

    private CategorieDocumentRefRepository repository;
    private CategorieDocumentRefService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(CategorieDocumentRefRepository.class);
        service = new CategorieDocumentRefService(repository);
    }

    private static CategorieDocumentRef cat(String code, String label, RegleObligationDoc regle) {
        CategorieDocumentRef c = new CategorieDocumentRef();
        c.setCode(code);
        c.setLabel(label);
        c.setActif(true);
        c.setRegleObligation(regle);
        c.setOrdre(100);
        return c;
    }

    @Test
    void applyToDocument_codeConnu_sync2cotes() {
        Document d = new Document();
        service.applyToDocument(d, "TITRE_FONCIER");

        assertThat(d.getCategorieDocumentCode()).isEqualTo("TITRE_FONCIER");
        assertThat(d.getCategorieDocument()).isEqualTo(CategorieDocument.TITRE_FONCIER);
    }

    @Test
    void applyToDocument_codeCustom_enumFallbackAutre() {
        Document d = new Document();
        service.applyToDocument(d, "ASSURANCE_HABITATION");

        assertThat(d.getCategorieDocumentCode()).isEqualTo("ASSURANCE_HABITATION");
        assertThat(d.getCategorieDocument()).isEqualTo(CategorieDocument.AUTRE);
    }

    @Test
    void applyToDocument_null_defautAutre() {
        Document d = new Document();
        service.applyToDocument(d, null);

        assertThat(d.getCategorieDocumentCode()).isEqualTo("AUTRE");
        assertThat(d.getCategorieDocument()).isEqualTo(CategorieDocument.AUTRE);
    }

    @Test
    void validerObligations_neufSansTitreNiPermis_2manquants() {
        when(repository.findAll()).thenReturn(List.of(
                cat("TITRE_FONCIER", "Titre foncier", RegleObligationDoc.TOUJOURS),
                cat("PERMIS_CONSTRUIRE", "Permis", RegleObligationDoc.SI_NEUF_OU_CONSTRUCTION),
                cat("AUTRE", "Autre", RegleObligationDoc.OPTIONNEL)));

        List<String> manquants = service.validerObligations(StatutExploitation.NEUF, Set.of());

        assertThat(manquants).containsExactlyInAnyOrder("Titre foncier", "Permis");
    }

    @Test
    void validerObligations_dejaRentableAvecTitre_demandeContrat() {
        when(repository.findAll()).thenReturn(List.of(
                cat("TITRE_FONCIER", "Titre foncier", RegleObligationDoc.TOUJOURS),
                cat("CONTRAT_GESTION", "Contrat gestion", RegleObligationDoc.SI_DEJA_RENTABLE),
                cat("CONTRAT_BAIL", "Contrat bail", RegleObligationDoc.SI_DEJA_RENTABLE)));

        List<String> manquants = service.validerObligations(
                StatutExploitation.DEJA_RENTABLE,
                Set.of("TITRE_FONCIER"));

        assertThat(manquants).hasSize(1);
        assertThat(manquants.get(0)).contains("Contrat gestion").contains("Contrat bail").contains("OU");
    }

    @Test
    void validerObligations_dejaRentableAvecContratBail_ok() {
        when(repository.findAll()).thenReturn(List.of(
                cat("TITRE_FONCIER", "Titre foncier", RegleObligationDoc.TOUJOURS),
                cat("CONTRAT_GESTION", "Contrat gestion", RegleObligationDoc.SI_DEJA_RENTABLE),
                cat("CONTRAT_BAIL", "Contrat bail", RegleObligationDoc.SI_DEJA_RENTABLE)));

        List<String> manquants = service.validerObligations(
                StatutExploitation.DEJA_RENTABLE,
                Set.of("TITRE_FONCIER", "CONTRAT_BAIL"));

        assertThat(manquants).isEmpty();
    }

    @Test
    void validerObligations_categorieInactiveIgnoree() {
        CategorieDocumentRef titreInactif = cat("TITRE_FONCIER", "Titre foncier", RegleObligationDoc.TOUJOURS);
        titreInactif.setActif(false);
        when(repository.findAll()).thenReturn(List.of(titreInactif));

        List<String> manquants = service.validerObligations(StatutExploitation.NEUF, Set.of());

        // La categorie inactive n'est plus bloquante
        assertThat(manquants).isEmpty();
    }

    @Test
    void resoudreLabel_codeConnu_retourneLabel() {
        when(repository.findByCode("TITRE_FONCIER"))
                .thenReturn(java.util.Optional.of(cat("TITRE_FONCIER", "Titre foncier", RegleObligationDoc.TOUJOURS)));

        assertThat(service.resoudreLabel("TITRE_FONCIER")).isEqualTo("Titre foncier");
    }

    @Test
    void listerActifs_returnsResponseList() {
        when(repository.findByActifTrueOrderByOrdreAsc()).thenReturn(List.of(
                cat("TITRE_FONCIER", "Titre", RegleObligationDoc.TOUJOURS)));

        List<CategorieDocumentResponse> list = service.listerActifs();

        assertThat(list).hasSize(1);
        assertThat(list.get(0).code()).isEqualTo("TITRE_FONCIER");
    }
}
