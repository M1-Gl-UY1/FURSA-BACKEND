# Configuration des emails transactionnels (V2 G.6)

FURSA envoie des emails transactionnels (KYC validé/refusé, propriété acceptée/refusée) via le serveur **Postal** déjà installé sur le VPS.

## Credentials à fournir (GitHub Secrets)

Ajouter ces secrets dans `Settings → Secrets and variables → Actions` du repo `FURSA-BACKEND` :

| Secret | Description | Exemple |
|---|---|---|
| `MAIL_HOST` | Hôte SMTP Postal | `mail.seed-innov.com` |
| `MAIL_PORT` | Port SMTP (587 STARTTLS, 25 si lan) | `587` |
| `MAIL_USERNAME` | User SMTP créé dans Postal | `fursa-app` |
| `MAIL_PASSWORD` | Token SMTP Postal (Settings → SMTP Credentials) | `xxxxxxx` |
| `MAIL_FROM` | Adresse expéditeur (doit appartenir à un domaine validé sur Postal) | `noreply@fursa.seed-innov.com` |
| `MAIL_FROM_NAME` | Nom affiché à côté de l'adresse | `FURSA` |
| `MAIL_ENABLED` | Activer/désactiver les envois (`true`/`false`) | `true` |

## Setup côté Postal

1. Se connecter à l'interface admin Postal (probablement `https://mail.seed-innov.com/`)
2. Créer un **server** dédié à FURSA (ou réutiliser un existant)
3. Dans ce server → onglet **Credentials** → **New SMTP credential** :
   - Type : SMTP
   - Username : `fursa-app` (ou autre)
   - Hold messages : non
   - Permissions : Send messages
4. Copier le token généré et le coller dans le secret GitHub `MAIL_PASSWORD`
5. Vérifier que le domaine d'envoi (`fursa.seed-innov.com` ou autre) est **validé** dans Postal (DKIM + SPF + Return-Path)
6. Test local :
   ```bash
   echo "Test FURSA" | mail -s "Test" -S smtp=smtp://mail.seed-innov.com:587 \
     -S smtp-auth=login -S smtp-auth-user=fursa-app -S smtp-auth-password=TOKEN \
     -S from=noreply@fursa.seed-innov.com vous@example.com
   ```

## Comportement par défaut

- **Si `MAIL_HOST` est vide** : le backend tombe en **log-only**. Les emails ne sont pas envoyés mais loggés (`[Email LOG-ONLY] to=... sujet=...`). Aucun crash.
- **Si Postal est temporairement down** : l'envoi est asynchrone (`@Async`), l'exception est loggée mais ne casse pas le flow métier (la notification in-app reste créée).
- **Désactivation explicite** : mettre `MAIL_ENABLED=false` pour passer en log-only même avec un host configuré (utile pour tester sans envoyer).

## Templates couverts en V1 (G.6)

| Événement | Méthode | Déclenché par |
|---|---|---|
| KYC validé | `envoyerKycValide` | `KycService.approve()` |
| KYC refusé | `envoyerKycRefuse` | `KycService.reject()` |
| Propriété acceptée (legacy) | `envoyerProprieteAcceptee` | `ProprieteService.approuver()` |
| Propriété acceptée + tokenisation | `envoyerProprieteAcceptee` | `ProprieteService.validerEtTokeniser()` |
| Propriété refusée | `envoyerProprieteRefusee` | `ProprieteService.refuser()` |

À ajouter ultérieurement : dividende distribué, retrait validé, confirmation d'achat — les méthodes existent déjà dans `EmailService` mais ne sont pas encore branchées sur les services métier (`DistributionServiceImpl`, `RetraitService`, `MarchePrimaireService`).
