package com.fursa.fursa_backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Possession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pos")
    private Long id;

    private Integer nombreDeParts;

    @ManyToOne
    @JoinColumn(name = "id_prop")
    private Propriete propriete;

    @ManyToOne
    @JoinColumn(name = "id_inv")
    private Investisseur investisseur;

    @Version
    private Long version;
}
