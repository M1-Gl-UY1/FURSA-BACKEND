# Plan de test - FURSA Backend Production

> Environnement cible : `https://api.fursas.duckdns.org` (profil Spring `prod`)
> Derniere verification : 2026-04-24

## Execution rapide

```bash
bash scripts/smoke-test.sh
```

Le script teste 40+ endpoints dans un ordre qui respecte les dependances metier
(register -> login -> acheter -> possession -> annonce -> achat secondaire -> notifs
-> distribution) et verifie les codes HTTP attendus. Sortie : **X passed / Y failed**.

---

## Comptes de test

### Admin seed (a remplacer par un vrai compte apres validation)
- Email : `admin@fursa.test`
- Password : `admin123`

### Investisseurs
Crees dynamiquement par le script `smoke-test.sh` (pour eviter la pollution de la
prod avec des comptes residuels, le script les supprime a la fin).

---

## Matrice des tests par module

### 1. Health & Infra

| Test | Endpoint | Attendu |
|---|---|---|
| Health public | `GET /api/health` | 200 `{"status":"UP"}` |
| Actuator health | `GET /actuator/health` | 200 |
| Actuator info | `GET /actuator/info` | 200 |
| Prometheus metrics | `GET /actuator/prometheus` | 200 text/plain |
| Swagger UI | `GET /swagger-ui` | 200 HTML |
| OpenAPI spec | `GET /v3/api-docs` | 200 JSON |

### 2. Securite / Authentification

| Test | Scenario | Attendu |
|---|---|---|
| Endpoint protege sans token | `GET /api/proprietes/public` sans header | 401 ou 403 |
| Register valide | `POST /auth/register` avec DTO complet | 201 + profil sans password |
| Register - mot de passe trop court | password=`abc123` | 400 fieldErrors |
| Register - mot de passe sans chiffre | password=`longpasswordonly` | 400 fieldErrors |
| Register - email invalide | email=`pas_un_email` | 400 fieldErrors |
| Register - injection role=ADMIN | payload contient `role:ADMIN` | 201 mais role=INVESTISSEUR cote serveur |
| Register - email deja utilise | meme email 2 fois | 400 message |
| Login valide | credentials corrects | 200 + token Bearer |
| Login mauvais password | credentials faux | 401 |
| Rate limit login | 6 requetes rapides | 5x 401, puis 429 `Retry-After: 60` |
| JWT expire ou invalide | token malforme | 401/403 |

### 3. Utilisateurs

| Test | Endpoint | Role | Attendu |
|---|---|---|---|
| Profil courant | `GET /api/user/me` | ANY auth | 200 profil complet |
| Lire son propre user | `GET /api/user/{myId}` | self | 200 |
| Lire user autrui | `GET /api/user/{otherId}` | pas admin | 403 |
| Lire autrui | `GET /api/user/{otherId}` | admin | 200 |
| Lister tous | `GET /api/user` | pas admin | 403 |
| Lister tous | `GET /api/user` | admin | 200 array |
| Valider compte | `POST /api/user/{id}/valider` | pas admin | 403 |
| Valider compte | `POST /api/user/{id}/valider` | admin | 200 isVerified=true |
| Modifier son profil | `PUT /api/user/update/{myId}` | self | 200 |
| Modifier autrui | `PUT /api/user/update/{otherId}` | pas admin | 403 |
| Supprimer son compte | `DELETE /api/user/delete/{myId}` | self | 200 |

### 4. Proprietes & Fichiers

| Test | Endpoint | Role | Attendu |
|---|---|---|---|
| Lister catalogue | `GET /api/proprietes/public` | ANY auth | 200 array |
| Detail propriete | `GET /api/proprietes/public/{id}` | ANY auth | 200 |
| Propriete inconnue | `GET /api/proprietes/public/99999` | ANY auth | 404 |
| Progression | `GET /api/proprietes/public/{id}/progression` | ANY auth | 200 `{totalParts, partsVendues, partsDisponibles, pourcentage}` |
| Creer (non admin) | `POST /api/proprietes/admin` | investisseur | 403 |
| Creer (admin) | `POST /api/proprietes/admin` multipart | admin | 201 |
| Publier (admin) | `POST /api/proprietes/admin/{id}/publier` | admin | 200 statut=PUBLIEE |
| Supprimer (admin) | `DELETE /api/proprietes/admin/{id}` | admin | 204 |
| Upload > 10 MB | fichier 11 MB | admin | 413 Payload Too Large |
| Upload mauvais type | fichier .exe | admin | 400 type non autorise |

### 5. Marche primaire (achat initial)

| Test | Endpoint | Role | Attendu |
|---|---|---|---|
| Acheter parts | `POST /api/marche-primaire/acheter` `{proprieteId:1,nombreParts:5}` | investor | 200 hash + status=SUCCES |
| Acheter propriete NON publiee | meme avec statut BROUILLON | investor | 400 |
| Acheter plus que disponible | nombreParts > partsDisponibles | investor | 400 |
| Acheter 0 parts | nombreParts=0 | investor | 400 |
| Mes possessions | `GET /me/possessions` | investor | 200 array |
| Mes transactions | `GET /me/transactions` | investor | 200 array |
| Mes paiements | `GET /me/paiements` | investor | 200 array |
| Toutes possessions | `GET /possessions` | pas admin | 403 |
| Toutes possessions | `GET /possessions` | admin | 200 |
| Portefeuille autre | `GET /possessions/{autreId}` | pas admin | 403 |

### 6. Marche secondaire

| Test | Endpoint | Role | Attendu |
|---|---|---|---|
| Creer annonce | `POST /api/annonces` `{proprieteId, nbParts, prix}` | investor avec parts | 201 statut=OUVERTE |
| Annonce > possession | nbParts > parts possedees | investor | 400 |
| Annonce sans possession | propriete jamais achetee | investor | 400 |
| Lister ouvertes | `GET /api/annonces` paginee | ANY auth | 200 Page<Annonce> |
| Mes annonces | `GET /api/annonces/me` | investor | 200 |
| Detail | `GET /api/annonces/{id}` | ANY auth | 200 |
| Modifier | `PUT /api/annonces/{id}` | vendeur | 200 |
| Modifier autre | `PUT /api/annonces/{id}` | pas vendeur | 400 "Seul le vendeur..." |
| Acheter | `POST /marche-secondaire/annonces/{id}/acheter` `{nombreDeParts}` | acheteur != vendeur | 200 transaction creee |
| Acheter sa propre annonce | meme id vendeur | vendeur | 400 |
| Acheter trop de parts | nbParts > annonce.dispo | acheteur | 400 |
| Annuler | `DELETE /api/annonces/{id}` | vendeur | 200 statut=ANNULEE |
| Annuler autre | `DELETE /api/annonces/{id}` | pas vendeur | 400 |

### 7. Notifications

| Test | Endpoint | Role | Attendu |
|---|---|---|---|
| Notifs apres achat | `GET /api/notifications/me` apres un achat secondaire | acheteur et vendeur | 200 avec notif type=TRANSACTION |
| Filtrer non lues | `GET /api/notifications/me?nonLuesSeulement=true` | any | 200 |
| Marquer lue | `PUT /api/notifications/{id}/lu` | destinataire | 200 lu=true |
| Marquer tout lu | `PUT /api/notifications/me/lu-tout` | any | 200 `{marquees: N}` |
| Notifs autrui (admin) | `GET /api/notifications/investisseur/{id}` | admin | 200 |
| Notifs autrui (non admin) | `GET /api/notifications/investisseur/{id}` | pas admin | 403 |

### 8. Revenus

| Test | Endpoint | Role | Attendu |
|---|---|---|---|
| Creer (non admin) | `POST /api/revenus` | investor | 403 |
| Creer (admin) | `POST /api/revenus` `{proprieteId, montantTotal}` | admin | 201 |
| Lister | `GET /api/revenus` | ANY auth | 200 |
| Detail | `GET /api/revenus/{id}` | ANY auth | 200 |
| Par propriete | `GET /api/revenus/propriete/{id}` | ANY auth | 200 |
| Inconnue | `GET /api/revenus/99999` | ANY auth | 404 |

### 9. Distribution / Dividendes

| Test | Endpoint | Role | Attendu |
|---|---|---|---|
| Distribuer (non admin) | `POST /api/distribution/{revenuId}` | investor | 403 |
| Distribuer (admin) | `POST /api/distribution/{revenuId}` | admin | 200 array Dividende |
| Distribuer revenu inconnu | `POST /api/distribution/99999` | admin | 404 |
| Distribuer sans possessions | `POST /api/distribution/{id}` propriete vide | admin | 400 |
| Mes dividendes | `GET /api/dividendes/me` | investor | 200 apres distribution |
| Dividendes d'un revenu | `GET /api/dividendes/revenu/{id}` | ANY auth | 200 |
| Tous (non admin) | `GET /api/dividendes` | investor | 403 |
| Tous (admin) | `GET /api/dividendes` | admin | 200 |

### 10. Dashboard

| Test | Endpoint | Role | Attendu |
|---|---|---|---|
| Mon dashboard | `GET /api/dashboard/me` | investor | 200 `{nombreProprietes, totalParts, totalInvesti, valeurPortefeuille, totalDividendesRecus, revenusAnnuelsPrevus, ...}` |
| Dashboard autre (non admin) | `GET /api/dashboard/investisseur/{id}` | investor | 403 |
| Dashboard autre (admin) | `GET /api/dashboard/investisseur/{id}` | admin | 200 |
| Dashboard admin (non admin) | `GET /api/dashboard/admin` | investor | 403 |
| Dashboard admin | `GET /api/dashboard/admin` | admin | 200 `{nombreInvestisseurs, volumeTransactions, ...}` |

### 11. Documentation OpenAPI

| Test | Endpoint | Role | Attendu |
|---|---|---|---|
| Swagger UI | `GET /swagger-ui` | public | 200 HTML redirection vers /swagger-ui/index.html |
| OpenAPI 3 spec | `GET /v3/api-docs` | public | 200 JSON valide |
| Tags presents | recherche "Marche primaire" dans le JSON | public | String present |

---

## Tests hors scope automatise

### Performance / Charge

A faire avec JMeter ou k6 separement :
- 100 logins simultanes (verifier que rate limit IP-based fonctionne)
- 500 achats concurrents sur la meme propriete (verifier `@Version` previent
  les parts negatives)
- Timings P95 sur les listings (Dashboard admin doit tenir sous 200 ms)

### UX / Front

- CORS : depuis `https://app.fursas.duckdns.org` (ou localhost:3000) un
  `OPTIONS /api/proprietes/public` doit retourner 204 avec les bons
  headers `Access-Control-Allow-*`

### Monitoring

- `/actuator/prometheus` expose `http_server_requests_seconds_*`
- Erreurs 5xx augmentent le compteur `http_server_requests_seconds_count{status="500"}`
- Health check DB : `/actuator/health/db` → 200 avec `{"status":"UP","details":{...}}`

---

## Rollback si un test critique echoue en prod

```bash
ssh -i ~/.ssh/koursa_deploy softengine@api.fursas.duckdns.org
cd ~/Fursa/FURSA-BACKEND
git log --oneline -5       # identifie le commit precedent stable
git reset --hard <sha>
docker compose up -d --build
curl https://api.fursas.duckdns.org/api/health
```
