package com.fursa.fursa_backend.dividende.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.NonNull;

import com.fursa.fursa_backend.model.Dividende;
import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.model.Revenus;
import com.fursa.fursa_backend.model.enumeration.StatutPaiement;

import jakarta.annotation.Nonnull;

@Component
@AllArgsConstructor
@Service
public class DividendeFactory {
    public Dividende create(
        BigDecimal montant,
        Investisseur investisseur,
        Revenus revenu

    ){
        Dividende dividende = new Dividende();
        dividende.setMontantCalcule(montant);
        dividende.setInvestisseur(investisseur);
        dividende.setRevenus(revenu);
        dividende.setStatut(StatutPaiement.VALIDE);

        return dividende;

    }
}
