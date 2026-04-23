package com.fursa.fursa_backend.seed;

import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.model.Propriete;
import com.fursa.fursa_backend.model.enumeration.Role;
import com.fursa.fursa_backend.model.enumeration.StatutPropriete;
import com.fursa.fursa_backend.repository.InvestisseurRepository;
import com.fursa.fursa_backend.repository.ProprieteRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class DataSeeder implements CommandLineRunner {

    private final ProprieteRepository proprieteRepository;
    private final InvestisseurRepository investisseurRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(ProprieteRepository proprieteRepository,
                      InvestisseurRepository investisseurRepository,
                      PasswordEncoder passwordEncoder) {
        this.proprieteRepository = proprieteRepository;
        this.investisseurRepository = investisseurRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // Ne pas insérer si des données existent déjà
        if (proprieteRepository.count() > 0) {
            return;
        }

        // --- Créer des investisseurs de test ---
        Investisseur inv1 = new Investisseur();
        inv1.setEmail("jorel@fursa.com");
        inv1.setPassword(passwordEncoder.encode("password123"));
        inv1.setRole(Role.INVESTISSEUR);
        inv1.setNom("TIOMELA");
        inv1.setPrenom("Jorel");
        inv1.setTelephone("+237656146518");
        inv1.setIsVerified(true);
        inv1.setWallet_address("0xABCDEF1234567890ABCDEF1234567890ABCDEF12");
        investisseurRepository.save(inv1);

        Investisseur inv2 = new Investisseur();
        inv2.setEmail("alice@fursa.com");
        inv2.setPassword(passwordEncoder.encode("password123"));
        inv2.setRole(Role.INVESTISSEUR);
        inv2.setNom("Martin");
        inv2.setPrenom("Alice");
        inv2.setTelephone("+33698765432");
        inv2.setIsVerified(true);
        inv2.setWallet_address("0x1234567890ABCDEF1234567890ABCDEF12345678");
        investisseurRepository.save(inv2);

        // --- Créer des propriétés de test ---
        Propriete prop1 = new Propriete();
        prop1.setNom("Fumba Town Villa");
        prop1.setLocalisation("Zanzibar, Tanzanie");
        prop1.setDescription("Villa de luxe en bord de mer à Fumba Town. 3 chambres, piscine, vue océan.");
        prop1.setNombreTotalPart(1000);
        prop1.setPartsDisponibles(1000);
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
        prop3.setDescription("Maison historique rénovée au coeur de Stone Town. Idéale pour location touristique.");
        prop3.setNombreTotalPart(300);
        prop3.setPartsDisponibles(300);
        prop3.setPrixUnitairePart(new BigDecimal("150.00"));
        prop3.setStatut(StatutPropriete.PUBLIEE);
        prop3.setRentabilitePrevue(12.0);
        prop3.setDateCreation(LocalDate.now());
        proprieteRepository.save(prop3);

        System.out.println("=== SEED : 2 investisseurs et 3 propriétés insérés ===");
    }
}
