// TransactionDTO.java
package com.fursa.fursa_backend.dto;

import com.fursa.fursa_backend.model.Transaction;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TransactionDTO {
    private Long id;
    private String hashTransaction;
    private String typeOperation;
    private Integer nombreParts;
    private Double montant;
    private LocalDateTime dateTransaction;
    private Long vendeurId;
    private String vendeurNom;
    private Long proprieteId;
    private String proprieteNom;

    public static TransactionDTO from(Transaction transaction) {
        TransactionDTO dto = new TransactionDTO();
        dto.setId(transaction.getId());
        dto.setHashTransaction(transaction.getHashTransaction());
        dto.setTypeOperation(transaction.getTypeOperation());
        dto.setNombreParts(transaction.getNombreParts());
        dto.setMontant(transaction.getMontant());
        dto.setDateTransaction(transaction.getDateTransaction());

        if (transaction.getVendeur() != null) {
            dto.setVendeurId(transaction.getVendeur().getId());
            dto.setVendeurNom(transaction.getVendeur().getNom() + " " + transaction.getVendeur().getPrenom());
        }

        if (transaction.getPropriete() != null) {
            dto.setProprieteId(transaction.getPropriete().getId());
            dto.setProprieteNom(transaction.getPropriete().getNom());
        }

        return dto;
    }
}