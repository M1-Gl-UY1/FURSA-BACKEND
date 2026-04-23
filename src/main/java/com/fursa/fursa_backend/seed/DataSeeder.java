package com.fursa.fursa_backend.seed;

import com.fursa.fursa_backend.model.Annonce;
import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.model.Possession;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.model.Revenus;
import com.fursa.fursa_backend.model.enumeration.Role;
import com.fursa.fursa_backend.model.enumeration.StatutAnnonce;
import com.fursa.fursa_backend.model.enumeration.StatutPropriete;
import com.fursa.fursa_backend.repository.AnnonceRepository;
import com.fursa.fursa_backend.repository.InvestisseurRepository;
import com.fursa.fursa_backend.repository.PossessionRepository;
import com.fursa.fursa_backend.repository.ProprieteRepository;
import com.fursa.fursa_backend.repository.RevenusRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class DataSeeder implements CommandLineRunner {

    private final ProprieteRepository proprieteRepository;
    private final InvestisseurRepository investisseurRepository;
    private final PossessionRepository possessionRepository;
    private final RevenusRepository revenusRepository;
    private final AnnonceRepository annonceRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(ProprieteRepository proprieteRepository,
                      InvestisseurRepository investisseurRepository,
                      PossessionRepository possessionRepository,
                      RevenusRepository revenusRepository,
                      AnnonceRepository annonceRepository,
                      PasswordEncoder passwordEncoder) {
        this.proprieteRepository = proprieteRepository;
        this.investisseurRepository = investisseurRepository;
        this.possessionRepository = possessionRepository;
        this.revenusRepository = revenusRepository;
        this.annonceRepository = annonceRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (proprieteRepository.count() > 0) {
            return;
        }

        Investisseur jorel = new Investisseur();
        jorel.setEmail("jorel@fursa.com");
        jorel.setPassword(passwordEncoder.encode("password123"));
        jorel.setRole(Role.INVESTISSEUR);
        jorel.setNom("TIOMELA");
        jorel.setPrenom("Jorel");
        jorel.setTelephone("+237656146518");
        jorel.setIsVerified(true);
        jorel.setWallet_address("0xABCDEF1234567890ABCDEF1234567890ABCDEF12");
        investisseurRepository.save(jorel);

        Investisseur alice = new Investisseur();
        alice.setEmail("alice@fursa.com");
        alice.setPassword(passwordEncoder.encode("password123"));
        alice.setRole(Role.INVESTISSEUR);
        alice.setNom("Martin");
        alice.setPrenom("Alice");
        alice.setTelephone("+33698765432");
        alice.setIsVerified(true);
        alice.setWallet_address("0x1234567890ABCDEF1234567890ABCDEF12345678");
        investisseurRepository.save(alice);

        Investisseur bob = new Investisseur();
        bob.setEmail("bob@fursa.com");
        bob.setPassword(passwordEncoder.encode("password123"));
        bob.setRole(Role.INVESTISSEUR);
        bob.setNom("Durand");
        bob.setPrenom("Bob");
        bob.setTelephone("+33612345678");
        bob.setIsVerified(true);
        bob.setWallet_address("0xFEDCBA0987654321FEDCBA0987654321FEDCBA09");
        investisseurRepository.save(bob);

        Propriete prop1 = new Propriete();
        prop1.setNom("Fumba Town Villa");
        prop1.setLocalisation("Zanzibar, Tanzanie");
        prop1.setDescription("Villa de luxe en bord de mer a Fumba Town. 3 chambres, piscine, vue ocean.");
        prop1.setNombreTotalPart(1000);
        prop1.setPartsDisponibles(840);
        prop1.setPrixUnitairePart(new BigDecimal("100.00"));
        prop1.setStatut(StatutPropriete.PUBLIEE);
        prop1.setRentabilitePrevue(8.5);
        prop1.setDateCreation(LocalDate.now());
        proprieteRepository.save(prop1);

        Propriete prop2 = new Propriete();
        prop2.setNom("Paje Squares Apartment");
        prop2.setLocalisation("Paje, Zanzibar");
        prop2.setDescription("Appartement moderne dans le complexe Paje Squares. 2 chambres, proche plage.");
        prop2.setNombreTotalPart(500);
        prop2.setPartsDisponibles(500);
        prop2.setPrixUnitairePart(new BigDecimal("200.00"));
        prop2.setStatut(StatutPropriete.PUBLIEE);
        prop2.setRentabilitePrevue(10.0);
        prop2.setDateCreation(LocalDate.now());
        proprieteRepository.save(prop2);

        Propriete prop3 = new Propriete();
        prop3.setNom("Stone Town Heritage House");
        prop3.setLocalisation("Stone Town, Zanzibar");
        prop3.setDescription("Maison historique renovee au coeur de Stone Town. Ideale pour location touristique.");
        prop3.setNombreTotalPart(300);
        prop3.setPartsDisponibles(300);
        prop3.setPrixUnitairePart(new BigDecimal("150.00"));
        prop3.setStatut(StatutPropriete.PUBLIEE);
        prop3.setRentabilitePrevue(12.0);
        prop3.setDateCreation(LocalDate.now());
        proprieteRepository.save(prop3);

        Possession possAlice = new Possession();
        possAlice.setInvestisseur(alice);
        possAlice.setPropriete(prop1);
        possAlice.setNombreDeParts(100);
        possessionRepository.save(possAlice);

        Possession possJorel = new Possession();
        possJorel.setInvestisseur(jorel);
        possJorel.setPropriete(prop1);
        possJorel.setNombreDeParts(60);
        possessionRepository.save(possJorel);

        Revenus revenuProp1 = new Revenus();
        revenuProp1.setPropriete(prop1);
        revenuProp1.setDate(LocalDate.now());
        revenuProp1.setMontantTotal(new BigDecimal("5000.00"));
        revenusRepository.save(revenuProp1);

        Annonce annonceAlice = new Annonce();
        annonceAlice.setInvestisseur(alice);
        annonceAlice.setPropriete(prop1);
        annonceAlice.setNombreDePartsAVendre(30);
        annonceAlice.setPrixUnitaireDemande(new BigDecimal("120.00"));
        annonceAlice.setStatut(StatutAnnonce.OUVERTE);
        annonceRepository.save(annonceAlice);

        System.out.println("=== SEED : 3 investisseurs, 3 proprietes, 2 possessions, 1 revenu, 1 annonce ===");
    }
}
