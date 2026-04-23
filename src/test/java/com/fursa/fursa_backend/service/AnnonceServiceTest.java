package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.dto.AchatAnnonceRequest;
import com.fursa.fursa_backend.dto.AchatAnnonceResponse;
import com.fursa.fursa_backend.dto.AnnonceRequest;
import com.fursa.fursa_backend.dto.AnnonceResponse;
import com.fursa.fursa_backend.model.Annonce;
import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.model.Paiement;
import com.fursa.fursa_backend.model.Possession;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.model.Transaction;
import com.fursa.fursa_backend.model.enumeration.StatutAnnonce;
import com.fursa.fursa_backend.repository.AnnonceRepository;
import com.fursa.fursa_backend.repository.InvestisseurRepository;
import com.fursa.fursa_backend.repository.PaiementRepository;
import com.fursa.fursa_backend.repository.PossessionRepository;
import com.fursa.fursa_backend.repository.ProprieteRepository;
import com.fursa.fursa_backend.repository.TransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnonceServiceTest {

    @Mock private AnnonceRepository annonceRepository;
    @Mock private InvestisseurRepository investisseurRepository;
    @Mock private ProprieteRepository proprieteRepository;
    @Mock private PossessionRepository possessionRepository;
    @Mock private PaiementRepository paiementRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks private AnnonceService annonceService;

    private Investisseur alice;
    private Investisseur bob;
    private Propriete villa;

    @BeforeEach
    void setUp() {
        alice = new Investisseur();
        alice.setId(1L);
        alice.setNom("Dupont");
        alice.setPrenom("Alice");

        bob = new Investisseur();
        bob.setId(2L);
        bob.setNom("Martin");
        bob.setPrenom("Bob");

        villa = new Propriete();
        villa.setId(100L);
        villa.setNom("Villa Fumba");
        villa.setNombreTotalPart(1000);
    }

    @Test
    void creer_vendeurAvecParts_succes() {
        Possession possAlice = new Possession();
        possAlice.setInvestisseur(alice);
        possAlice.setPropriete(villa);
        possAlice.setNombreDeParts(100);

        when(investisseurRepository.findById(1L)).thenReturn(Optional.of(alice));
        when(proprieteRepository.findById(100L)).thenReturn(Optional.of(villa));
        when(possessionRepository.findByInvestisseurIdAndProprieteId(1L, 100L))
                .thenReturn(Optional.of(possAlice));
        when(annonceRepository.findByProprieteIdAndStatut(100L, StatutAnnonce.OUVERTE))
                .thenReturn(List.of());
        when(annonceRepository.save(any(Annonce.class))).thenAnswer(inv -> {
            Annonce a = inv.getArgument(0);
            a.setId(500L);
            return a;
        });

        AnnonceRequest req = new AnnonceRequest(1L, 100L, 30, new BigDecimal("150.00"));
        AnnonceResponse res = annonceService.creer(req);

        assertEquals(500L, res.id());
        assertEquals(StatutAnnonce.OUVERTE, res.statut());
        assertEquals(30, res.nombreDePartsAVendre());
    }

    @Test
    void creer_vendeurSansPossession_leveIllegalState() {
        when(investisseurRepository.findById(1L)).thenReturn(Optional.of(alice));
        when(proprieteRepository.findById(100L)).thenReturn(Optional.of(villa));
        when(possessionRepository.findByInvestisseurIdAndProprieteId(1L, 100L))
                .thenReturn(Optional.empty());

        AnnonceRequest req = new AnnonceRequest(1L, 100L, 10, new BigDecimal("100.00"));
        assertThrows(IllegalStateException.class, () -> annonceService.creer(req));
    }

    @Test
    void creer_trop_de_parts_compte_les_annonces_existantes() {
        Possession possAlice = new Possession();
        possAlice.setInvestisseur(alice);
        possAlice.setNombreDeParts(100);

        Annonce existante = new Annonce();
        existante.setInvestisseur(alice);
        existante.setNombreDePartsAVendre(80);
        existante.setStatut(StatutAnnonce.OUVERTE);

        when(investisseurRepository.findById(1L)).thenReturn(Optional.of(alice));
        when(proprieteRepository.findById(100L)).thenReturn(Optional.of(villa));
        when(possessionRepository.findByInvestisseurIdAndProprieteId(1L, 100L))
                .thenReturn(Optional.of(possAlice));
        when(annonceRepository.findByProprieteIdAndStatut(100L, StatutAnnonce.OUVERTE))
                .thenReturn(List.of(existante));

        AnnonceRequest req = new AnnonceRequest(1L, 100L, 25, new BigDecimal("100.00"));
        assertThrows(IllegalStateException.class, () -> annonceService.creer(req));
    }

    @Test
    void acheter_nominal_transfereParts_creeTransaction_envoieNotifs() {
        Annonce annonce = new Annonce();
        annonce.setId(500L);
        annonce.setInvestisseur(alice);
        annonce.setPropriete(villa);
        annonce.setNombreDePartsAVendre(50);
        annonce.setPrixUnitaireDemande(new BigDecimal("120.00"));
        annonce.setStatut(StatutAnnonce.OUVERTE);

        Possession possAlice = new Possession();
        possAlice.setInvestisseur(alice);
        possAlice.setPropriete(villa);
        possAlice.setNombreDeParts(60);

        when(annonceRepository.findById(500L)).thenReturn(Optional.of(annonce));
        when(investisseurRepository.findById(2L)).thenReturn(Optional.of(bob));
        when(possessionRepository.findByInvestisseurIdAndProprieteId(1L, 100L))
                .thenReturn(Optional.of(possAlice));
        when(possessionRepository.findByInvestisseurIdAndProprieteId(2L, 100L))
                .thenReturn(Optional.empty());
        when(possessionRepository.save(any(Possession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paiementRepository.save(any(Paiement.class))).thenAnswer(inv -> {
            Paiement p = inv.getArgument(0);
            p.setId(900L);
            return p;
        });
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            t.setId(800L);
            return t;
        });

        AchatAnnonceRequest req = new AchatAnnonceRequest(2L, 20);
        AchatAnnonceResponse res = annonceService.acheter(500L, req);

        assertEquals(20, res.nombreDePartsAchetees());
        assertEquals(0, new BigDecimal("2400.00").compareTo(res.montantTotal()));
        assertEquals(2L, res.acheteurId());
        assertEquals(1L, res.vendeurId());
        assertEquals(30, annonce.getNombreDePartsAVendre());
        assertEquals(StatutAnnonce.OUVERTE, annonce.getStatut());
        assertEquals(40, possAlice.getNombreDeParts());
        verify(notificationService, times(2)).envoyer(any(), any(), any(), any());
    }

    @Test
    void acheter_toutes_les_parts_passe_annonce_en_completee() {
        Annonce annonce = new Annonce();
        annonce.setId(500L);
        annonce.setInvestisseur(alice);
        annonce.setPropriete(villa);
        annonce.setNombreDePartsAVendre(10);
        annonce.setPrixUnitaireDemande(new BigDecimal("100.00"));
        annonce.setStatut(StatutAnnonce.OUVERTE);

        Possession possAlice = new Possession();
        possAlice.setInvestisseur(alice);
        possAlice.setPropriete(villa);
        possAlice.setNombreDeParts(10);

        when(annonceRepository.findById(500L)).thenReturn(Optional.of(annonce));
        when(investisseurRepository.findById(2L)).thenReturn(Optional.of(bob));
        when(possessionRepository.findByInvestisseurIdAndProprieteId(1L, 100L))
                .thenReturn(Optional.of(possAlice));
        when(possessionRepository.findByInvestisseurIdAndProprieteId(2L, 100L))
                .thenReturn(Optional.empty());
        when(possessionRepository.save(any(Possession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paiementRepository.save(any(Paiement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        annonceService.acheter(500L, new AchatAnnonceRequest(2L, 10));

        assertEquals(0, annonce.getNombreDePartsAVendre());
        assertEquals(StatutAnnonce.COMPLETEE, annonce.getStatut());
        verify(possessionRepository).delete(possAlice);
    }

    @Test
    void acheter_annonceCompletee_leveIllegalState() {
        Annonce annonce = new Annonce();
        annonce.setId(500L);
        annonce.setInvestisseur(alice);
        annonce.setStatut(StatutAnnonce.COMPLETEE);

        when(annonceRepository.findById(500L)).thenReturn(Optional.of(annonce));

        assertThrows(IllegalStateException.class,
                () -> annonceService.acheter(500L, new AchatAnnonceRequest(2L, 5)));
    }

    @Test
    void acheter_parSoiMeme_leveIllegalState() {
        Annonce annonce = new Annonce();
        annonce.setId(500L);
        annonce.setInvestisseur(alice);
        annonce.setPropriete(villa);
        annonce.setNombreDePartsAVendre(10);
        annonce.setStatut(StatutAnnonce.OUVERTE);

        when(annonceRepository.findById(500L)).thenReturn(Optional.of(annonce));
        when(investisseurRepository.findById(1L)).thenReturn(Optional.of(alice));

        assertThrows(IllegalStateException.class,
                () -> annonceService.acheter(500L, new AchatAnnonceRequest(1L, 5)));
    }

    @Test
    void acheter_tropDeParts_leveIllegalState() {
        Annonce annonce = new Annonce();
        annonce.setId(500L);
        annonce.setInvestisseur(alice);
        annonce.setPropriete(villa);
        annonce.setNombreDePartsAVendre(10);
        annonce.setStatut(StatutAnnonce.OUVERTE);

        when(annonceRepository.findById(500L)).thenReturn(Optional.of(annonce));
        when(investisseurRepository.findById(2L)).thenReturn(Optional.of(bob));

        assertThrows(IllegalStateException.class,
                () -> annonceService.acheter(500L, new AchatAnnonceRequest(2L, 50)));
    }

    @Test
    void annuler_parVendeur_passeEnAnnulee() {
        Annonce annonce = new Annonce();
        annonce.setId(500L);
        annonce.setInvestisseur(alice);
        annonce.setStatut(StatutAnnonce.OUVERTE);
        annonce.setPropriete(villa);
        annonce.setNombreDePartsAVendre(10);
        annonce.setPrixUnitaireDemande(new BigDecimal("100"));

        when(annonceRepository.findById(500L)).thenReturn(Optional.of(annonce));
        when(annonceRepository.save(any(Annonce.class))).thenAnswer(inv -> inv.getArgument(0));

        AnnonceResponse res = annonceService.annuler(500L, 1L);
        assertEquals(StatutAnnonce.ANNULEE, res.statut());
    }

    @Test
    void annuler_parAutreQueVendeur_leveIllegalState() {
        Annonce annonce = new Annonce();
        annonce.setId(500L);
        annonce.setInvestisseur(alice);
        annonce.setStatut(StatutAnnonce.OUVERTE);

        when(annonceRepository.findById(500L)).thenReturn(Optional.of(annonce));

        assertThrows(IllegalStateException.class, () -> annonceService.annuler(500L, 2L));
    }

    @Test
    void getById_inexistant_leveNotFound() {
        when(annonceRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> annonceService.getById(999L));
    }
}
