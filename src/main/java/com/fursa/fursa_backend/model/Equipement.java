package com.fursa.fursa_backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Equipement configurable par l'admin (Phase V2, 04/06/2026).
 *
 * <p>Remplace les booleens hardcodes hasPiscine/hasClimatisation/etc. sur
 * Propriete. Permet a l'admin d'ajouter de nouveaux equipements (ex : "Spa",
 * "Garage couvert", "Salle de sport") sans deploy.
 *
 * <p>Migration douce : les 6 equipements existants sont inseres via migration
 * SQL avec leurs codes historiques (PISCINE, CLIMATISATION, PARKING, ASCENSEUR,
 * JARDIN, VUE_MER) et les data legacy (Propriete.hasXxx=true) sont migrees
 * vers la table de liaison propriete_equipement.
 */
@Entity
@Table(name = "equipement")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Equipement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_equipement")
    private Long id;

    /** Code stable utilise en API (ex "PISCINE"). Unique, en majuscules. */
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    /** Libelle affiche dans l'UI (ex "Piscine privee"). */
    @Column(nullable = false, length = 100)
    private String label;

    /** Nom de l'icone lucide-react (ex "Waves"). Permet a l'admin de choisir. */
    @Column(length = 50)
    private String icone;

    /** Ordre d'affichage dans le wizard / les cards (ascendant). */
    @Column(name = "ordre_affichage", nullable = false)
    private Integer ordre = 100;

    /**
     * Actif = visible dans le wizard et les filtres.
     * Inactif = masque (mais les biens qui l'ont garde l'affichage).
     * Utilise au lieu d'une suppression dure pour preserver l'historique.
     */
    @Column(nullable = false)
    private Boolean actif = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
