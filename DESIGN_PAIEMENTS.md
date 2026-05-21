# Design - Module Paiements FURSA

Document de conception du module paiement de FURSA Community. Cible : Yellow Card
(on-ramp fiat -> USDC pour l'Afrique), avec architecture PSP-agnostique permettant
de demarrer en mode `MockProvider` tant que le KYB business Yellow Card n'est pas
valide (FURSA n'a pas encore d'entite juridique au 2026-05-21).

Voir aussi : [`README.md`](README.md), [`DEPLOYMENT.md`](DEPLOYMENT.md),
[`../ROADMAP_FURSA.md`](../ROADMAP_FURSA.md).

## 1. Objectifs

- Remplacer le faux paiement actuel (Paiement.statut force a VALIDE sans debit) par un vrai flow asynchrone webhook-driven.
- Architecture PSP-agnostique : interface commune `PaymentProvider`, implementations `MockPaymentProvider` (dev), `YellowCardProvider` (prod), futur `StripeProvider` / `FlutterwaveProvider`.
- Brancher en meme temps le **chantier 5** : ecriture on-chain reelle a la confirmation du paiement (plus de UUID factice dans `Transaction.hashTransaction`).
- Garantir l'idempotence end-to-end (init + webhook) pour resister aux double-clics, retries reseau, race conditions.

## 2. Vue d'ensemble du flow

```text
[Investisseur clique "Acheter 5 parts"]
    |
    v
[Frontend]  POST /api/paiements/init
            body: { proprieteId, nombreParts, idempotencyKey }
            header: Authorization: Bearer <jwt>
    |
    v
[Backend]   1. Recupere investisseur via JWT
            2. Recupere propriete, verifie partsDisponibles >= nombreParts
            3. Calcule montantFiat = prixUnitairePart * nombreParts (devise = investisseur.deviseFiat ou par defaut "EUR")
            4. Calcule montantUsdc via DeviseRateService.convertirEnUsdc(montantFiat, devise)
            5. Determine le PaymentProvider actif (dev=MOCK, prod=YELLOW_CARD)
            6. provider.createSession(req) -> { externalId, widgetUrl, expiresAt }
            7. Persiste PaymentSession(statut=PENDING, expiresAt=now+30min)
            8. Renvoie au front : { sessionId, widgetUrl, expiresAt }
    |
    v
[Frontend]  Redirige vers widgetUrl (ou affiche widget integre)
    |
    | L'investisseur paie via Mobile Money / virement / CB
    | (En mode MOCK : MockPaymentScheduler auto-confirme apres 5s en simulant un webhook)
    |
    v
[PSP]       POST /api/webhooks/{provider}
            headers: { X-Signature: HMAC-SHA256(body, secret) }
            body: { externalId, status: CONFIRMED, amount, txHashCryptoPSP }
    |
    v
[Backend]   1. Recupere PaymentProvider par {provider} (path variable)
            2. provider.verifyWebhookSignature(rawBody, signature) -> bloque les fakes (401)
            3. event = provider.parseWebhook(rawBody) -> WebhookEvent neutre
            4. Recupere PaymentSession par externalId
            5. IDEMPOTENCE : si statut != PENDING, renvoie 200 OK sans rien faire
            6. Verifie amount recu == montantUsdc attendu (tolerance epsilon)
            7. Appelle MarchePrimaireService.confirmerAchat(session, event)
                |
                v
            [MarchePrimaireService.confirmerAchat]
                    @Transactional
                    a. Lock pessimistic la PaymentSession (PENDING -> CONFIRMED atomic)
                    b. Cree Paiement (statut=VALIDE, type=CRYPTO, montant=session.montantFiat)
                    c. Cree Transaction (statut=EN_ATTENTE, hashTransaction=null temporairement)
                    d. Cree ou ajoute Possession (+= nombreParts)
                    e. Decremente Propriete.partsDisponibles
                    f. CHANTIER 5 : BlockchainService.addInvestor(walletInvestisseur) -> vrai txHash
                       si erreur on-chain: session.statut=FAILED + Notification admin
                    g. Transaction.hashTransaction = vrai txHash (plus de UUID factice)
                    h. Transaction.statut = SUCCES
                    i. PaymentSession.statut = CONFIRMED, confirmedAt = now
                    j. Notification "Achat confirme" pour l'investisseur
    |
    v
[Frontend]  Polling toutes les 10s sur GET /api/paiements/session/{id}
            -> CONFIRMED -> redirige vers page de succes avec lien Etherscan
            -> FAILED   -> page "Probleme, contact support"
            -> EXPIRED  -> page "Session expiree, recommencer"
            (Stop polling apres 5 min ou statut terminal)
```

## 3. Modele de donnees

### 3.1 Table `payment_session`

| Colonne | Type | Contraintes | Role |
|---|---|---|---|
| `id` | BIGSERIAL | PK | id interne |
| `external_id` | VARCHAR(100) | NOT NULL UNIQUE | id retourne par le PSP (Yellow Card session id, Stripe session id...) |
| `id_inv` | BIGINT | NOT NULL FK investisseur(id_user) | qui paie |
| `id_prop` | BIGINT | NOT NULL FK propriete(id_prop) | quel bien |
| `nombre_parts` | INTEGER | NOT NULL CHECK > 0 | combien de parts |
| `montant_fiat` | NUMERIC(19,2) | NOT NULL | ce que paie l'investisseur dans sa devise |
| `devise_fiat` | VARCHAR(3) | NOT NULL | "XAF", "EUR", "USD"... |
| `montant_usdc` | NUMERIC(38,18) | NOT NULL | equivalent stable a arriver wallet FURSA |
| `provider_name` | VARCHAR(50) | NOT NULL | "MOCK", "YELLOW_CARD"... |
| `widget_url` | TEXT | nullable | URL ou rediriger l'investisseur |
| `statut` | VARCHAR(20) | NOT NULL | PENDING / CONFIRMED / EXPIRED / FAILED |
| `created_at` | TIMESTAMP | NOT NULL | |
| `expires_at` | TIMESTAMP | NOT NULL | typiquement created_at + 30 min |
| `confirmed_at` | TIMESTAMP | nullable | |
| `idempotency_key` | VARCHAR(100) | nullable | dedup cote init |
| `paiement_id` | BIGINT | nullable FK | audit trail |
| `transaction_id` | BIGINT | nullable FK | audit trail |
| `possession_id` | BIGINT | nullable FK | audit trail |
| `webhook_raw_payload` | TEXT | nullable | payload brut recu du PSP |
| `error_message` | VARCHAR(500) | nullable | si FAILED, raison |
| `version` | BIGINT | NOT NULL DEFAULT 0 | @Version optimistic locking |

Index unique : `(idempotency_key, id_inv)` (partiel, where idempotency_key is not null).

### 3.2 Enum `StatutPaymentSession`

```text
PENDING    : session creee, paiement en cours cote PSP, en attente webhook
CONFIRMED  : webhook recu, paiement valide, possession creee, parts mintees on-chain
EXPIRED    : expires_at depasse, paiement abandonne (cron passe sur les PENDING vieux)
FAILED     : PSP a renvoye un echec OU on-chain a echoue apres paiement
             (cas critique : argent recu mais parts pas ecrites -> remboursement manuel)
```

### 3.3 Table `devise_rate`

| Colonne | Type | Contraintes |
|---|---|---|
| `code_devise` | VARCHAR(3) | PK |
| `taux_vers_usdc` | NUMERIC(38,18) | NOT NULL |
| `updated_at` | TIMESTAMP | NOT NULL |

Seed initial (a ajuster selon les vrais taux courants) :

| Devise | Taux vers USDC | Note |
|---|---|---|
| XAF | 0.0017 | 1 XAF ~= 0.0017 USDC (~600 XAF = 1 USDC) |
| EUR | 1.10 | 1 EUR ~= 1.10 USDC |
| USD | 1.00 | 1 USD = 1 USDC par definition |
| KES | 0.0078 | 1 KES ~= 0.0078 USDC |
| NGN | 0.00066 | 1 NGN ~= 0.00066 USDC |

Maj manuelle par admin via `PUT /api/admin/devise-rate/{code}` (a coder en Session 2).

## 4. Interface `PaymentProvider`

```java
public interface PaymentProvider {

    /** "MOCK" | "YELLOW_CARD" | "STRIPE" | "FLUTTERWAVE" */
    String getName();

    /** Cree une session de paiement chez le PSP. */
    ProviderSessionResponse createSession(ProviderSessionRequest request);

    /** Verifie le HMAC du webhook. Algo et header varient selon PSP. */
    boolean verifyWebhookSignature(String rawBody, String signature);

    /** Parse le payload brut en evenement metier neutre. */
    WebhookEvent parseWebhook(String rawBody);

    /** Optionnel : fallback pour les sessions PENDING vieilles (cron). */
    default Optional<ProviderSessionStatus> querySessionStatus(String externalId) {
        return Optional.empty();
    }
}
```

### 4.1 DTO

```java
public record ProviderSessionRequest(
    String idempotencyKey,
    String investisseurEmail,
    BigDecimal montantFiat,
    String deviseFiat,
    String successCallbackUrl,
    String cancelCallbackUrl,
    Map<String, String> metadata    // sessionId FURSA, proprieteId, etc.
) {}

public record ProviderSessionResponse(
    String externalId,
    String widgetUrl,
    LocalDateTime expiresAt
) {}

public enum WebhookEventType {
    PAYMENT_CONFIRMED,
    PAYMENT_FAILED,
    PAYMENT_EXPIRED,
    UNKNOWN
}

public record WebhookEvent(
    WebhookEventType type,
    String externalSessionId,
    BigDecimal amountReceived,        // dans la devise crypto/USDC recue
    String currency,
    String providerTxHash,            // tx hash on-chain du PSP (si applicable)
    String errorMessage               // si FAILED
) {}
```

## 5. Implementations

### 5.1 `MockPaymentProvider` (profil dev)

- `createSession()` : genere `externalId = "mock_" + UUID`, `widgetUrl = /mock-payment-widget?session={externalId}`, expire dans 30 min
- `verifyWebhookSignature()` : retourne `true` (pas de signature en mode mock)
- `parseWebhook()` : parse le JSON simule envoye par le scheduler interne

### 5.2 `MockPaymentScheduler` (profil dev uniquement)

Composant `@Component @Profile("dev")` avec `@Scheduled(fixedDelay = 5000)` :
- Parcourt les `PaymentSession` PENDING du provider MOCK creees il y a > 5s
- Pour chacune : appelle directement `POST /api/webhooks/MOCK` en local (ou bypass HTTP et appelle directement le service) avec un payload simule `{ externalId, status: CONFIRMED, amount: session.montantUsdc, txHashCrypto: "mock_tx_<uuid>" }`
- Permet de tester le flow complet sans PSP reel

### 5.3 `YellowCardProvider` (squelette)

Squelette pose au cas ou la doc API serait connue par avance. **A completer** quand FURSA aura les cles sandbox apres KYB.

- Authentification : Bearer token via header `Authorization: Bearer <YELLOW_CARD_API_KEY>`
- `createSession()` : POST `https://api.yellowcard.io/business/payments/sessions` (URL hypothetique, a confirmer)
- `verifyWebhookSignature()` : HMAC-SHA256 du body avec `YELLOW_CARD_WEBHOOK_SECRET`, comparaison constant-time avec le header `X-Yellow-Card-Signature` (hypothetique)
- `parseWebhook()` : mappe le format Yellow Card en `WebhookEvent` neutre

Variables d'env attendues (a ajouter quand le KYB sera fait) :

```env
YELLOW_CARD_API_KEY=<a obtenir apres KYB>
YELLOW_CARD_WEBHOOK_SECRET=<a obtenir apres KYB>
YELLOW_CARD_API_BASE_URL=https://api.yellowcard.io/business  # a confirmer
```

## 6. Migration DB

Fichier : [`scripts/migrations/003_payment_session_and_devise_rate.sql`](scripts/migrations/003_payment_session_and_devise_rate.sql)

- `CREATE TABLE IF NOT EXISTS payment_session (...)`
- `CREATE TABLE IF NOT EXISTS devise_rate (...)`
- `INSERT INTO devise_rate ... ON CONFLICT (code_devise) DO NOTHING` (seed initial)
- Index unique partiel sur `(idempotency_key, id_inv)`
- Index sur `expires_at` (cron qui marque les sessions EXPIRED)
- Index sur `(statut, provider_name)` (queries admin)

Idempotent grace a `IF NOT EXISTS` partout. Appliquee auto par le workflow `deploy.yml` en debut de deploiement.

## 7. Decoupage en sessions

### Session 1 (en cours) - **Fondation** (~10h)

Modele de donnees + interface + Mock. **Pas** de refactor du service ni de controller.

1. Entite `PaymentSession` + `PaymentSessionRepository` + enum `StatutPaymentSession`
2. Entite `DeviseRate` + `DeviseRateRepository` + service `DeviseRateService.convertirEnUsdc(BigDecimal, String)`
3. Interface `PaymentProvider` + DTO records
4. `MockPaymentProvider`
5. `MockPaymentScheduler` (`@Profile("dev")`)
6. `YellowCardProvider` squelette (vide ou avec TODO clairs)
7. Migration DB `003_payment_session_and_devise_rate.sql`
8. Tests unitaires : `MockPaymentProviderTest`, `DeviseRateServiceTest`

A la fin de Session 1 : `mvnw compile` doit passer, les beans `PaymentProvider` doivent etre injectables, mais aucun endpoint REST n'expose encore le flow.

### Session 2 - **Refactor service + controllers** (~12h)

9. Refactor `MarchePrimaireService` :
   - `initierAchat(investisseurId, request, idempotencyKey)` -> `PaymentSession`
   - `confirmerAchat(sessionId, WebhookEvent)` -> declenche la cascade Paiement / Transaction / Possession / on-chain
   - L'ancien `acheterParts(...)` devient deprecated mais reste pour ne pas casser
10. `PaymentController` :
    - `POST /api/paiements/init` (authentifie investisseur) -> initierAchat -> renvoie `{ sessionId, widgetUrl, expiresAt }`
    - `GET /api/paiements/session/{id}` (authentifie investisseur, self uniquement) -> renvoie `{ statut, txHash, etherscanUrl, errorMessage }`
11. `PaymentWebhookController` :
    - `POST /api/webhooks/{provider}` (public, securite par signature HMAC)
    - Idempotence : si session deja CONFIRMED, renvoie 200 sans rien faire
12. **Chantier 5 inclus** : dans `confirmerAchat()`, appel `BlockchainService.addInvestor(wallet)` + recuperation du vrai tx hash. Si erreur on-chain : `session.statut = FAILED` + notification admin.

### Session 3 - **Frontend + cron + tests E2E** (~11h)

13. Frontend `AcheterPartsPage` : appel `/paiements/init`, redirection vers widgetUrl, polling sur `/paiements/session/{id}` toutes les 10s
14. Cron de cleanup : `@Scheduled` qui marque EXPIRED les sessions PENDING > expires_at
15. Endpoint admin : `GET /api/admin/paiements/failed` pour voir les sessions FAILED, `POST /api/admin/paiements/{id}/retry-on-chain` pour retry manuel
16. Endpoint admin : `PUT /api/admin/devise-rate/{code}` pour MAJ des taux
17. Tests d'integration : flow complet end-to-end avec MockPaymentProvider
18. Doc + smoke tests prod

## 8. Securite

- **Webhook** : signature HMAC obligatoire pour `YELLOW_CARD`. Toujours `true` pour `MOCK` (dev only). Comparison constant-time pour eviter les timing attacks.
- **Anti-fraude** : verifier `amountReceived == session.montantUsdc` avant de confirmer.
- **Idempotence** : 3 niveaux
  - Header `Idempotency-Key` sur `POST /paiements/init` (dedup cote create)
  - `external_id UNIQUE` cote DB (dedup cote PSP)
  - Statut PENDING (transition unique vers CONFIRMED/FAILED/EXPIRED)
- **Lock pessimistic** sur la `PaymentSession` lors de la confirmation pour serialiser les webhooks concurrents.
- **Wallet investisseur** : a definir comment FURSA gere les wallets custodial. Option simple V1 : un seul wallet FURSA pour tous les investisseurs, mappage investisseur <-> id custodial off-chain. Option V2 : un wallet derive par investisseur (HD wallet seed).

## 9. Risques connus

| Risque | Mitigation |
|---|---|
| Webhook double / replay attack | Idempotence externe via `external_id` UNIQUE + statut PENDING |
| Montant recu < attendu (frais PSP non prevus) | Tolerance epsilon (0.5%) ou refus si delta > seuil |
| On-chain rate apres paiement OK | Statut FAILED + alerte admin + endpoint retry manuel |
| Wallet custodial FURSA compromis | Cle privee hors-Git, rotation reguliere, alerte sur tx sortantes (V2) |
| PSP down au moment de l'achat | Affichage erreur "reessayer plus tard" cote front |
| Cron EXPIRED pendant qu'un webhook arrive | Lock pessimistic + check statut avant transition |
| Taux fiat -> USDC obsolete | `updated_at` affiche cote admin, alerte si > 7 jours |

## 10. Ce qui est hors-scope (a faire plus tard)

- Vraie integration Yellow Card (attendre les cles sandbox apres KYB)
- Multi-PSP simultane (un investisseur choisit entre Yellow Card et Stripe)
- Off-ramp (USDC -> fiat) pour les retraits investisseurs : workflow inverse, traite en V3
- Reconciliation comptable (rapports admin pour la compta FURSA)
- Webhook resilience (retry exponential, dead-letter queue)
- KYC / AML par investisseur (necessite prestataire externe type Sumsub)
