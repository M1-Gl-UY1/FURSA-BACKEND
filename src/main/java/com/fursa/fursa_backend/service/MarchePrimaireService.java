package com.fursa.fursa_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fursa.fursa_backend.blockchain.service.BlockchainService;
import com.fursa.fursa_backend.dto.AchatRequest;
import com.fursa.fursa_backend.dto.AchatResponse;
import com.fursa.fursa_backend.dto.PaymentInitResponse;
import com.fursa.fursa_backend.model.*;
import com.fursa.fursa_backend.model.enumeration.StatutPaiement;
import com.fursa.fursa_backend.model.enumeration.StatutPaymentSession;
import com.fursa.fursa_backend.model.enumeration.StatutPropriete;
import com.fursa.fursa_backend.model.enumeration.StatutTransaction;
import com.fursa.fursa_backend.model.enumeration.TypePaiement;
import com.fursa.fursa_backend.payment.PaymentProvider;
import com.fursa.fursa_backend.payment.PaymentProviderRegistry;
import com.fursa.fursa_backend.payment.ProviderSessionRequest;
import com.fursa.fursa_backend.payment.ProviderSessionResponse;
import com.fursa.fursa_backend.payment.WebhookEvent;
import com.fursa.fursa_backend.payment.WebhookEventType;
import com.fursa.fursa_backend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fursa.fursa_backend.dto.InvestisseurPossessionResponse;
import com.fursa.fursa_backend.dto.PaiementResponse;
import com.fursa.fursa_backend.dto.PossessionResponse;
import com.fursa.fursa_backend.dto.TransactionResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class MarchePrimaireService {

    private static final Logger log = LoggerFactory.getLogger(MarchePrimaireService.class);
    private static final String ENDPOINT_ACHETER = "POST /api/marche-primaire/acheter";
    private static final BigDecimal AMOUNT_TOLERANCE_PCT = new BigDecimal("0.005"); // 0.5% tolerance anti-frais PSP

    private final PaiementRepository paiementRepository;
    private final TransactionRepository transactionRepository;
    private final PossessionRepository possessionRepository;
    private final ProprieteRepository proprieteRepository;
    private final InvestisseurRepository investisseurRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final PaymentSessionRepository paymentSessionRepository;
    private final DeviseRateService deviseRateService;
    private final PaymentProviderRegistry providerRegistry;
    private final BlockchainService blockchainService;
    private final ObjectMapper objectMapper;

    public MarchePrimaireService(PaiementRepository paiementRepository,
                                  TransactionRepository transactionRepository,
                                  PossessionRepository possessionRepository,
                                  ProprieteRepository proprieteRepository,
                                  InvestisseurRepository investisseurRepository,
                                  IdempotencyRecordRepository idempotencyRepository,
                                  PaymentSessionRepository paymentSessionRepository,
                                  DeviseRateService deviseRateService,
                                  PaymentProviderRegistry providerRegistry,
                                  BlockchainService blockchainService,
                                  ObjectMapper objectMapper) {
        this.paiementRepository = paiementRepository;
        this.transactionRepository = transactionRepository;
        this.possessionRepository = possessionRepository;
        this.proprieteRepository = proprieteRepository;
        this.investisseurRepository = investisseurRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.paymentSessionRepository = paymentSessionRepository;
        this.deviseRateService = deviseRateService;
        this.providerRegistry = providerRegistry;
        this.blockchainService = blockchainService;
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

    // =========================================================================
    // V2 - Paiements asynchrones via PSP (Yellow Card / Mock)
    // Voir DESIGN_PAIEMENTS.md pour le flow complet.
    // =========================================================================

    /**
     * Cree une session de paiement chez le PSP actif et persiste une PaymentSession PENDING.
     * Renvoie l'URL du widget vers laquelle rediriger l'investisseur.
     *
     * Idempotent : si idempotencyKey deja vue pour cet utilisateur, renvoie la session existante.
     */
    @Transactional
    public PaymentInitResponse initierAchat(Long investisseurId, AchatRequest request, String idempotencyKey) {

        // Idempotence : meme cle deja utilisee -> on renvoie la session existante (PENDING ou terminale)
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<PaymentSession> existing = paymentSessionRepository
                    .findByIdempotencyKeyAndInvestisseur_Id(idempotencyKey, investisseurId);
            if (existing.isPresent()) {
                return toInitResponse(existing.get());
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

        // Calcul des montants. V1.5 : on suppose une devise unique au niveau plateforme (EUR par defaut).
        // V2 : on prendra investisseur.preferredCurrency ou la devise envoyee par le front.
        BigDecimal montantFiat = propriete.getPrixUnitairePart()
                .multiply(BigDecimal.valueOf(request.getNombreParts()))
                .setScale(2, RoundingMode.HALF_UP);
        String deviseFiat = "EUR";
        BigDecimal montantUsdc = deviseRateService.convertirEnUsdc(montantFiat, deviseFiat);

        PaymentProvider provider = providerRegistry.getActive();

        // Metadata transmise au PSP (utile pour les webhooks et le support)
        Map<String, String> metadata = new HashMap<>();
        metadata.put("investisseurId", String.valueOf(investisseurId));
        metadata.put("proprieteId", String.valueOf(propriete.getId()));
        metadata.put("nombreParts", String.valueOf(request.getNombreParts()));

        ProviderSessionRequest providerReq = new ProviderSessionRequest(
                idempotencyKey,
                investisseur.getEmail(),
                montantFiat,
                deviseFiat,
                null,  // successCallbackUrl : a definir cote front quand l'integration sera completee
                null,  // cancelCallbackUrl
                metadata
        );
        ProviderSessionResponse providerResp = provider.createSession(providerReq);

        PaymentSession session = new PaymentSession();
        session.setExternalId(providerResp.externalId());
        session.setInvestisseur(investisseur);
        session.setPropriete(propriete);
        session.setNombreParts(request.getNombreParts());
        session.setMontantFiat(montantFiat);
        session.setDeviseFiat(deviseFiat);
        session.setMontantUsdc(montantUsdc);
        session.setProviderName(provider.getName());
        session.setWidgetUrl(providerResp.widgetUrl());
        session.setStatut(StatutPaymentSession.PENDING);
        session.setCreatedAt(LocalDateTime.now());
        session.setExpiresAt(providerResp.expiresAt());
        session.setIdempotencyKey(idempotencyKey);
        session = paymentSessionRepository.save(session);

        log.info("PaymentSession creee : id={} externalId={} provider={} montant={} {} ({} USDC)",
                session.getId(), session.getExternalId(), provider.getName(),
                montantFiat, deviseFiat, montantUsdc);

        return toInitResponse(session);
    }

    /**
     * Confirme une PaymentSession suite a la reception du webhook PSP.
     * Cree Paiement + Transaction + Possession et inscrit l'investisseur on-chain (chantier 5).
     *
     * Idempotent : si la session est deja CONFIRMED, on no-op.
     */
    @Transactional
    public void confirmerAchat(String externalId, WebhookEvent event) {
        PaymentSession session = paymentSessionRepository.findByExternalIdForUpdate(externalId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "PaymentSession introuvable pour externalId=" + externalId));

        if (session.getStatut() == StatutPaymentSession.CONFIRMED) {
            log.info("PaymentSession {} deja CONFIRMED -> no-op idempotent", externalId);
            return;
        }
        if (session.getStatut() != StatutPaymentSession.PENDING) {
            throw new IllegalStateException(
                    "PaymentSession " + externalId + " dans un etat non confirmable : " + session.getStatut());
        }

        if (event.type() == WebhookEventType.PAYMENT_FAILED || event.type() == WebhookEventType.PAYMENT_EXPIRED) {
            session.setStatut(event.type() == WebhookEventType.PAYMENT_EXPIRED
                    ? StatutPaymentSession.EXPIRED : StatutPaymentSession.FAILED);
            session.setErrorMessage(event.errorMessage());
            paymentSessionRepository.save(session);
            log.warn("PaymentSession {} -> {} : {}", externalId, session.getStatut(), event.errorMessage());
            return;
        }

        if (event.type() != WebhookEventType.PAYMENT_CONFIRMED) {
            throw new IllegalStateException("WebhookEvent non confirmable : " + event.type());
        }

        // Verif montant : tolerance epsilon pour absorber les arrondis et frais PSP eventuels.
        BigDecimal recu = event.amountReceived() == null ? BigDecimal.ZERO : event.amountReceived();
        BigDecimal attendu = session.getMontantUsdc();
        BigDecimal toleranceAbs = attendu.multiply(AMOUNT_TOLERANCE_PCT);
        if (recu.subtract(attendu).abs().compareTo(toleranceAbs) > 0) {
            session.setStatut(StatutPaymentSession.FAILED);
            session.setErrorMessage("Montant recu " + recu + " hors tolerance vs attendu " + attendu);
            paymentSessionRepository.save(session);
            log.error("PaymentSession {} FAILED : montant incoherent (recu={}, attendu={})", externalId, recu, attendu);
            return;
        }

        Propriete propriete = session.getPropriete();
        Investisseur investisseur = session.getInvestisseur();

        // Re-verification des parts disponibles (peuvent avoir diminue depuis l'init)
        if (propriete.getPartsDisponibles() < session.getNombreParts()) {
            session.setStatut(StatutPaymentSession.FAILED);
            session.setErrorMessage("Parts plus disponibles a la confirmation (race condition entre 2 paiements)");
            paymentSessionRepository.save(session);
            log.error("PaymentSession {} FAILED : parts insuffisantes a la confirmation", externalId);
            return;
        }

        // Paiement (montant en devise fiat, type CRYPTO car passe par on-ramp)
        Paiement paiement = new Paiement();
        paiement.setInvestisseur(investisseur);
        paiement.setPropriete(propriete);
        paiement.setMontant(session.getMontantFiat());
        paiement.setNombre_parts(session.getNombreParts());
        paiement.setType(TypePaiement.CRYPTO);
        paiement.setStatut(StatutPaiement.VALIDE);
        paiement.setDate(LocalDateTime.now());
        paiement = paiementRepository.save(paiement);

        // Transaction : on stocke d'abord le PSP tx hash (ETH/USDC reception), on tentera ensuite l'on-chain mint
        Transaction transaction = new Transaction();
        transaction.setPaiement(paiement);
        transaction.setHashTransaction(event.providerTxHash() != null ? event.providerTxHash() : "psp_" + UUID.randomUUID());
        transaction.setTypeOperation(com.fursa.fursa_backend.model.enumeration.TypeOperation.ACHAT);
        transaction.setNombreParts(session.getNombreParts());
        transaction.setMontant(session.getMontantFiat());
        transaction.setDateTransaction(LocalDateTime.now());
        transaction.setStatut(StatutTransaction.SUCCES);
        transaction = transactionRepository.save(transaction);

        // Possession : creation ou increment
        Possession possession = possessionRepository
                .findByInvestisseurIdAndProprieteId(investisseur.getId(), propriete.getId())
                .orElseGet(() -> {
                    Possession p = new Possession();
                    p.setInvestisseur(investisseur);
                    p.setPropriete(propriete);
                    p.setNombreDeParts(0);
                    return p;
                });
        possession.setNombreDeParts(possession.getNombreDeParts() + session.getNombreParts());
        possession = possessionRepository.save(possession);

        propriete.setPartsDisponibles(propriete.getPartsDisponibles() - session.getNombreParts());
        proprieteRepository.save(propriete);

        // CHANTIER 5 : ecriture on-chain
        // Si l'investisseur n'a pas encore d'adresse wallet, on log un warning mais on confirme quand meme la session
        // (le mint on-chain pourra etre rejoue plus tard via endpoint admin /retry-on-chain).
        String walletAddress = investisseur.getWallet_address();
        if (walletAddress == null || walletAddress.isBlank()) {
            log.warn("Investisseur {} sans wallet_address : on-chain skip pour session {}",
                    investisseur.getId(), externalId);
        } else {
            try {
                String onChainTxHash = blockchainService.addInvestor(walletAddress);
                transaction.setHashTransaction(onChainTxHash);
                transactionRepository.save(transaction);
                log.info("On-chain addInvestor OK : wallet={} tx={}", walletAddress, onChainTxHash);
            } catch (Exception e) {
                // Le paiement est valide cote DB mais le on-chain a echoue.
                // On marque la session FAILED pour visibilite admin, mais on NE rollback PAS les entites
                // (l'investisseur a paye, ses parts sont a lui). Admin pourra retry via /retry-on-chain.
                // TODO : envoyer Notification ADMIN, intégrer Sentry.
                session.setErrorMessage("On-chain echoue : " + e.getMessage());
                log.error("On-chain ECHEC pour session {} : {}", externalId, e.getMessage(), e);
                // On laisse statut CONFIRMED quand meme (paiement valide off-chain). Le champ errorMessage signale le souci.
            }
        }

        // Finalisation session
        session.setStatut(StatutPaymentSession.CONFIRMED);
        session.setConfirmedAt(LocalDateTime.now());
        session.setPaiementId(paiement.getId());
        session.setTransactionId(transaction.getId());
        session.setPossessionId(possession.getId());
        try {
            session.setWebhookRawPayload(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException ignore) { /* non critique */ }
        paymentSessionRepository.save(session);

        log.info("PaymentSession {} CONFIRMED : paiement={} transaction={} possession={}",
                externalId, paiement.getId(), transaction.getId(), possession.getId());
    }

    private PaymentInitResponse toInitResponse(PaymentSession session) {
        return new PaymentInitResponse(
                session.getId(),
                session.getExternalId(),
                session.getWidgetUrl(),
                session.getExpiresAt(),
                session.getMontantFiat(),
                session.getDeviseFiat(),
                session.getMontantUsdc(),
                session.getProviderName(),
                session.getStatut().name()
        );
    }
}
