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

        Investisseur investor1 = new Investisseur();
        investor1.setEmail("investor1@fursa.test");
        investor1.setPassword(passwordEncoder.encode("password123"));
        investor1.setRole(Role.INVESTISSEUR);
        investor1.setNom("Demo");
        investor1.setPrenom("Investor One");
        investor1.setTelephone("+237600000001");
        investor1.setIsVerified(true);
        investor1.setWallet_address("0xABCDEF1234567890ABCDEF1234567890ABCDEF12");
        investisseurRepository.save(investor1);

        Investisseur investor2 = new Investisseur();
        investor2.setEmail("investor2@fursa.test");
        investor2.setPassword(passwordEncoder.encode("password123"));
        investor2.setRole(Role.INVESTISSEUR);
        investor2.setNom("Demo");
        investor2.setPrenom("Investor Two");
        investor2.setTelephone("+237600000002");
        investor2.setIsVerified(true);
        investor2.setWallet_address("0x1234567890ABCDEF1234567890ABCDEF12345678");
        investisseurRepository.save(investor2);

        Investisseur investor3 = new Investisseur();
        investor3.setEmail("investor3@fursa.test");
        investor3.setPassword(passwordEncoder.encode("password123"));
        investor3.setRole(Role.INVESTISSEUR);
        investor3.setNom("Demo");
        investor3.setPrenom("Investor Three");
        investor3.setTelephone("+237600000003");
        investor3.setIsVerified(true);
        investor3.setWallet_address("0xFEDCBA0987654321FEDCBA0987654321FEDCBA09");
        investisseurRepository.save(investor3);

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

        Possession poss2 = new Possession();
        poss2.setInvestisseur(investor2);
        poss2.setPropriete(prop1);
        poss2.setNombreDeParts(100);
        possessionRepository.save(poss2);

        Possession poss1 = new Possession();
        poss1.setInvestisseur(investor1);
        poss1.setPropriete(prop1);
        poss1.setNombreDeParts(60);
        possessionRepository.save(poss1);

        Revenus revenuProp1 = new Revenus();
        revenuProp1.setPropriete(prop1);
        revenuProp1.setDate(LocalDate.now());
        revenuProp1.setMontantTotal(new BigDecimal("5000.00"));
        revenusRepository.save(revenuProp1);

        Annonce annonceDemo = new Annonce();
        annonceDemo.setInvestisseur(investor2);
        annonceDemo.setPropriete(prop1);
        annonceDemo.setNombreDePartsAVendre(30);
        annonceDemo.setPrixUnitaireDemande(new BigDecimal("120.00"));
        annonceDemo.setStatut(StatutAnnonce.OUVERTE);
        annonceRepository.save(annonceDemo);

        System.out.println("=== SEED : 3 investisseurs, 3 proprietes, 2 possessions, 1 revenu, 1 annonce ===");
    }
}
