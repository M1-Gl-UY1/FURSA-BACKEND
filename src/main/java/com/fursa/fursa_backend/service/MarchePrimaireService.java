package com.fursa.fursa_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fursa.fursa_backend.dto.AchatRequest;
import com.fursa.fursa_backend.dto.AchatResponse;
import com.fursa.fursa_backend.model.*;
import com.fursa.fursa_backend.model.enumeration.StatutPaiement;
import com.fursa.fursa_backend.model.enumeration.StatutPropriete;
import com.fursa.fursa_backend.model.enumeration.StatutTransaction;
import com.fursa.fursa_backend.model.enumeration.TypePaiement;
import com.fursa.fursa_backend.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fursa.fursa_backend.dto.InvestisseurPossessionResponse;
import com.fursa.fursa_backend.dto.PaiementResponse;
import com.fursa.fursa_backend.dto.PossessionResponse;
import com.fursa.fursa_backend.dto.TransactionResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MarchePrimaireService {

    private static final String ENDPOINT_ACHETER = "POST /api/marche-primaire/acheter";

    private final PaiementRepository paiementRepository;
    private final TransactionRepository transactionRepository;
    private final PossessionRepository possessionRepository;
    private final ProprieteRepository proprieteRepository;
    private final InvestisseurRepository investisseurRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    public MarchePrimaireService(PaiementRepository paiementRepository,
                                  TransactionRepository transactionRepository,
                                  PossessionRepository possessionRepository,
                                  ProprieteRepository proprieteRepository,
                                  InvestisseurRepository investisseurRepository,
                                  IdempotencyRecordRepository idempotencyRepository,
                                  ObjectMapper objectMapper) {
        this.paiementRepository = paiementRepository;
        this.transactionRepository = transactionRepository;
        this.possessionRepository = possessionRepository;
        this.proprieteRepository = proprieteRepository;
        this.investisseurRepository = investisseurRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AchatResponse acheterParts(Long investisseurId, AchatRequest request, String idempotencyKey) {

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<IdempotencyRecord> existing = idempotencyRepository
                    .findByIdempotencyKeyAndUserIdAndEndpoint(idempotencyKey, investisseurId, ENDPOINT_ACHETER);
            if (existing.isPresent() && existing.get().getResponseBody() != null) {
                return deserialize(existing.get().getResponseBody());
            }
        }

        Investisseur investisseur = investisseurRepository.findById(investisseurId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Investisseur non trouve avec l'id : " + investisseurId));

        Propriete propriete = proprieteRepository.findById(request.getProprieteId())
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Propriete non trouvee avec l'id : " + request.getProprieteId()));

        if (propriete.getStatut() != StatutPropriete.PUBLIEE) {
            throw new IllegalStateException("Cette propriete n'est pas disponible a l'achat.");
        }

        if (propriete.getPartsDisponibles() < request.getNombreParts()) {
            throw new IllegalStateException("Parts insuffisantes. Disponibles : " + propriete.getPartsDisponibles());
        }

        BigDecimal montantTotal = propriete.getPrixUnitairePart()
                .multiply(BigDecimal.valueOf(request.getNombreParts()));

        // V1.5 : pas encore de vrai paiement crypto. On marque FIAT par defaut.
        // TODO chantier blockchain : determiner le type depuis le token utilise (USDC/USDT/EURC).
        // TODO V2 : verifier la balance/solvabilite de l'investisseur avant de creer le Paiement.
        Paiement paiement = new Paiement();
        paiement.setInvestisseur(investisseur);
        paiement.setPropriete(propriete);
        paiement.setMontant(montantTotal);
        paiement.setNombre_parts(request.getNombreParts());
        paiement.setType(TypePaiement.FIAT);
        paiement.setStatut(StatutPaiement.VALIDE);
        paiement.setDate(LocalDateTime.now());
        paiement = paiementRepository.save(paiement);

        // V1.5 : hash factice tant que l'integration on-chain (Sepolia) n'est pas branchee.
        // TODO chantier blockchain : remplacer par le vrai tx hash retourne par BlockchainService.
        String hashTransaction = "0x" + UUID.randomUUID().toString().replace("-", "");

        Transaction transaction = new Transaction();
        transaction.setPaiement(paiement);
        transaction.setHashTransaction(hashTransaction);
        transaction.setTypeOperation(com.fursa.fursa_backend.model.enumeration.TypeOperation.ACHAT);
        transaction.setNombreParts(request.getNombreParts());
        transaction.setMontant(montantTotal);
        transaction.setDateTransaction(LocalDateTime.now());
        transaction.setStatut(StatutTransaction.SUCCES);
        transaction = transactionRepository.save(transaction);

        Possession possession = possessionRepository
                .findByInvestisseurIdAndProprieteId(investisseur.getId(), propriete.getId())
                .orElseGet(() -> {
                    Possession p = new Possession();
                    p.setInvestisseur(investisseur);
                    p.setPropriete(propriete);
                    p.setNombreDeParts(0);
                    return p;
                });
        possession.setNombreDeParts(possession.getNombreDeParts() + request.getNombreParts());
        possessionRepository.save(possession);

        propriete.setPartsDisponibles(propriete.getPartsDisponibles() - request.getNombreParts());
        proprieteRepository.save(propriete);

        AchatResponse response = new AchatResponse(
                paiement.getId(),
                transaction.getId(),
                transaction.getHashTransaction(),
                transaction.getStatut().name(),
                request.getNombreParts(),
                montantTotal,
                propriete.getNom(),
                transaction.getDateTransaction()
        );

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            persistIdempotency(idempotencyKey, investisseurId, response);
        }

        return response;
    }

    private void persistIdempotency(String key, Long userId, AchatResponse response) {
        try {
            IdempotencyRecord record = new IdempotencyRecord();
            record.setIdempotencyKey(key);
            record.setUserId(userId);
            record.setEndpoint(ENDPOINT_ACHETER);
            record.setResponseBody(objectMapper.writeValueAsString(response));
            record.setCreatedAt(LocalDateTime.now());
            idempotencyRepository.save(record);
        } catch (JsonProcessingException e) {
            // L'achat metier a reussi : on ne fait pas echouer le call si seule la sauvegarde de la cle echoue.
            throw new IllegalStateException("Echec serialisation reponse idempotente", e);
        } catch (DataIntegrityViolationException e) {
            // Course condition : deux requetes simultanees avec la meme cle.
            // La 2e tombe ici ; on rejoue l'enregistrement gagnant.
        }
    }

    private AchatResponse deserialize(String json) {
        try {
            return objectMapper.readValue(json, AchatResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cle d'idempotence corrompue", e);
        }
    }

    /**
     * Récupérer toutes les possessions
     */
    public List<PossessionResponse> getAllPossessions() {
        return possessionRepository.findAll().stream().map(p -> new PossessionResponse(
                p.getId(),
                p.getPropriete().getNom(),
                p.getPropriete().getLocalisation(),
                p.getNombreDeParts(),
                p.getPropriete().getPrixUnitairePart(),
                p.getPropriete().getPrixUnitairePart().multiply(BigDecimal.valueOf(p.getNombreDeParts())),
                p.getPropriete().getRentabilitePrevue()
        )).toList();
    }

    /**
     * Récupérer toutes les transactions
     */
    public List<TransactionResponse> getAllTransactions() {
        return transactionRepository.findAll().stream().map(t -> new TransactionResponse(
                t.getId(),
                t.getHashTransaction(),
                t.getTypeOperation() == null ? null : t.getTypeOperation().name(),
                t.getStatut().name(),
                t.getNombreParts(),
                t.getMontant(),
                t.getPaiement().getPropriete().getNom(),
                t.getDateTransaction()
        )).toList();
    }

    /**
     * Récupérer tous les paiements
     */
    public List<PaiementResponse> getAllPaiements() {
        return paiementRepository.findAll().stream().map(p -> new PaiementResponse(
                p.getId(),
                p.getMontant(),
                p.getType().name(),
                p.getStatut().name(),
                p.getNombre_parts(),
                p.getPropriete().getNom(),
                p.getDate()
        )).toList();
    }

    /**
     * Liste les investisseurs d'une propriete avec leur nombre de parts et le pourcentage detenu.
     * Utilise par l'admin et par le proprietaire qui a propose le bien.
     */
    public List<InvestisseurPossessionResponse> getInvestisseursParPropriete(Long proprieteId) {
        Propriete propriete = proprieteRepository.findById(proprieteId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Propriete non trouvee avec l'id : " + proprieteId));

        int totalParts = propriete.getNombreTotalPart() == null ? 0 : propriete.getNombreTotalPart();

        return possessionRepository.findByProprieteId(proprieteId).stream()
                .map(p -> {
                    Investisseur inv = p.getInvestisseur();
                    int parts = p.getNombreDeParts() == null ? 0 : p.getNombreDeParts();
                    double pourcentage = totalParts > 0 ? (parts * 100.0) / totalParts : 0.0;
                    return new InvestisseurPossessionResponse(
                            inv.getId(),
                            inv.getEmail(),
                            inv.getNom(),
                            inv.getPrenom(),
                            parts,
                            pourcentage
                    );
                })
                .toList();
    }

    /**
     * Récupérer le portefeuille (possessions) d'un investisseur
     */
    public List<PossessionResponse> getPortefeuille(Long investisseurId) {
        investisseurRepository.findById(investisseurId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Investisseur non trouve avec l'id : " + investisseurId));

        List<Possession> possessions = possessionRepository.findByInvestisseurId(investisseurId);

        return possessions.stream().map(p -> new PossessionResponse(
                p.getId(),
                p.getPropriete().getNom(),
                p.getPropriete().getLocalisation(),
                p.getNombreDeParts(),
                p.getPropriete().getPrixUnitairePart(),
                p.getPropriete().getPrixUnitairePart().multiply(BigDecimal.valueOf(p.getNombreDeParts())),
                p.getPropriete().getRentabilitePrevue()
        )).toList();
    }

    /**
     * Récupérer l'historique des transactions d'un investisseur
     */
    public List<TransactionResponse> getTransactions(Long investisseurId) {
        investisseurRepository.findById(investisseurId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Investisseur non trouve avec l'id : " + investisseurId));

        List<Paiement> paiements = paiementRepository.findByInvestisseurId(investisseurId);

        return paiements.stream()
                .flatMap(p -> p.getTransactions().stream().map(t -> new TransactionResponse(
                        t.getId(),
                        t.getHashTransaction(),
                        t.getTypeOperation() == null ? null : t.getTypeOperation().name(),
                        t.getStatut().name(),
                        t.getNombreParts(),
                        t.getMontant(),
                        p.getPropriete().getNom(),
                        t.getDateTransaction()
                ))).toList();
    }

    /**
     * Récupérer l'historique des paiements d'un investisseur
     */
    public List<PaiementResponse> getPaiements(Long investisseurId) {
        investisseurRepository.findById(investisseurId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Investisseur non trouve avec l'id : " + investisseurId));

        List<Paiement> paiements = paiementRepository.findByInvestisseurId(investisseurId);

        return paiements.stream().map(p -> new PaiementResponse(
                p.getId(),
                p.getMontant(),
                p.getType().name(),
                p.getStatut().name(),
                p.getNombre_parts(),
                p.getPropriete().getNom(),
                p.getDate()
        )).toList();
    }
}
