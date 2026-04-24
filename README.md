# FURSA Backend - API REST

Plateforme d'investissement immobilier fractionne en Afrique (Zanzibar, Tanzanie).
Backend Spring Boot couvrant authentification JWT, catalogue immobilier, marche
primaire (achat de parts), marche secondaire (revente entre investisseurs),
distribution de dividendes, notifications, dashboard et administration.

**Production** : `https://api.fursas.duckdns.org`
**Swagger UI** : `https://api.fursas.duckdns.org/swagger-ui`
**Status** : 48 endpoints, 49 tests unitaires verts, 66/66 smoke tests.

---

## Stack technique

| Composant       | Technologie                          |
|-----------------|--------------------------------------|
| Backend         | Java 21 / Spring Boot 4.0.5          |
| Base de donnees | PostgreSQL 16                        |
| ORM             | Hibernate / JPA                      |
| Securite        | Spring Security + JWT (JJWT 0.11.5) + Method Security |
| Rate limiting   | Bucket4j 8.14 (in-memory)            |
| API docs        | SpringDoc OpenAPI 2.8.14 (Swagger UI)|
| Monitoring      | Spring Actuator + Micrometer Prometheus |
| Build           | Maven                                |
| Tests           | JUnit 5 + Mockito                    |
| CI/CD           | GitHub Actions -> SSH -> Docker Compose |

---

## Lancement local

```bash
cp .env.example .env            # puis remplir POSTGRES_PASSWORD et JWT_SECRET
docker compose up -d --build
```

- `fursa-db` : PostgreSQL 16
- `fursa-backend` : API sur port 8081
- Profil Spring : `dev` par defaut (ddl-auto=update, DataSeeder actif, logs DEBUG)

Pour repartir d'une DB vierge : `docker compose down -v`.

### Sans Docker

```bash
createdb fursa
export JWT_SECRET="$(openssl rand -base64 48)"
export POSTGRES_PASSWORD=scorp
./mvnw spring-boot:run
```

L'API : **http://localhost:8081** - Swagger : **http://localhost:8081/swagger-ui**

---

## Production

**Deploiement** : push sur `main` -> GitHub Actions -> SSH VPS -> `docker compose up -d --build`.
Voir `DEPLOYMENT.md` pour la procedure complete de bascule en profil `prod`, la
generation des secrets, la rotation JWT et le backup DB.

---

## Structure du projet

```
src/main/java/com/fursa/fursa_backend/
├── config/
│   ├── SecurityConfig              # chaine de filtres, CORS, method security
│   ├── CorsConfig                  # origines autorisees (env CORS_ALLOWED_ORIGINS)
│   ├── OpenApiConfig               # titre, description, Bearer auth Swagger
│   ├── JwtUtils                    # generation et validation des tokens
│   └── LoginRateLimiter            # 5 tentatives / minute / (email+IP)
├── filter/
│   ├── JwtFilter                   # extrait le Bearer, peuple SecurityContext
│   └── RequestIdFilter             # MDC requestId pour correlation des logs
├── controller/                     # 10 controllers, 48 endpoints
├── dto/                            # records Request/Response
├── exception/GlobalExceptionHandler # codes HTTP coherents (404, 400, 403, 409, 413, 415, 429)
├── mapper/ProprieteMapper
├── model/                          # entites JPA
│   └── enumeration/                # StatutXxx, TypeXxx, Role, TypeOperation
├── repository/                     # JpaRepository par entite
├── service/
│   ├── CustomUserService           # UserDetailsService
│   ├── AuthenticatedInvestisseurService # helper pour l'utilisateur courant
│   ├── ProprieteService, FileStorageService
│   ├── MarchePrimaireService, AnnonceService, NotificationService
│   ├── RevenuService, DistributionServiceImpl, DividendeQueryService
│   └── DashboardService
├── seed/DataSeeder                 # @Profile("!prod")
└── FursaBackendApplication.java
```

---

## Authentification

Tous les endpoints sont proteges par JWT sauf :
- `POST /api/user/auth/register`, `POST /api/user/auth/login`
- `GET /api/health`, `GET /actuator/health`, `/actuator/info`, `/actuator/prometheus`
- `/swagger-ui/**`, `/v3/api-docs/**`

### Obtenir un token

```bash
curl -X POST https://api.fursas.duckdns.org/api/user/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"tiomelajorel@gmail.com","password":"jorel2026"}'
# => { "token": "eyJhbGciOi...", "type": "Bearer" }
```

### Utiliser le token

```bash
curl -H "Authorization: Bearer <token>" https://api.fursas.duckdns.org/api/user/me
```

Sur Swagger UI, cliquer **Authorize** en haut a droite et coller le token
(sans le prefixe `Bearer`).

### Regles de securite

- **Injection impossible** : `RegisterRequest` (record) filtre les champs ; `role` et
  `isVerified` sont forces cote serveur (`INVESTISSEUR` / `false`).
- **Password policy** : min 8 caracteres, au moins une lettre + un chiffre.
- **Rate limit login** : 5 tentatives/minute par `(email, IP)`, 429 au-dela.
- **Self-or-admin** sur `GET/PUT/DELETE /api/user/{id}` via SpEL.
- **`@PreAuthorize("hasRole('ADMIN')")`** sur tous les endpoints admin.
- **JWT secret obligatoire** : fail-fast au demarrage si `JWT_SECRET` manque ou < 32 octets.
- **Upload** : max 10 MB, whitelist MIME (`pdf`, `jpg`, `jpeg`, `png`, `webp`).
- **Optimistic locking** (`@Version`) sur `Propriete`, `Annonce`, `Possession`
  pour prevenir les race conditions d'achat concurrent.
- **CORS** : origines explicites via `CORS_ALLOWED_ORIGINS` (jamais `*`).

---

## Endpoints (48)

### Auth (public)
| Methode | Chemin                      |
|---------|-----------------------------|
| POST    | `/api/user/auth/register`   |
| POST    | `/api/user/auth/login`      |

### Utilisateurs
| Methode | Chemin                          | Acces           |
|---------|---------------------------------|-----------------|
| GET     | `/api/user/me`                  | authentifie     |
| GET     | `/api/user/{id}`                | self ou admin   |
| PUT     | `/api/user/update/{id}`         | self ou admin   |
| DELETE  | `/api/user/delete/{id}`         | self ou admin   |
| GET     | `/api/user`                     | **admin**       |
| POST    | `/api/user/{id}/valider`        | **admin**       |

### Proprietes & Fichiers
| Methode | Chemin                                        | Acces      |
|---------|-----------------------------------------------|------------|
| GET     | `/api/proprietes/public`                      | authentifie|
| GET     | `/api/proprietes/public/{id}`                 | authentifie|
| GET     | `/api/proprietes/public/{id}/progression`     | authentifie|
| POST    | `/api/proprietes/admin` (multipart)           | **admin**  |
| PUT     | `/api/proprietes/admin/{id}` (multipart)      | **admin**  |
| POST    | `/api/proprietes/admin/{id}/publier`          | **admin**  |
| DELETE  | `/api/proprietes/admin/{id}`                  | **admin**  |
| GET     | `/api/fichiers/{fileName}`                    | authentifie|

### Marche primaire
| Methode | Chemin                                        | Acces           |
|---------|-----------------------------------------------|-----------------|
| POST    | `/api/marche-primaire/acheter`                | authentifie     |
| GET     | `/api/marche-primaire/me/{possessions,transactions,paiements}` | authentifie |
| GET     | `/api/marche-primaire/{possessions,transactions,paiements}`    | **admin**   |
| GET     | `/api/marche-primaire/{possessions,transactions,paiements}/{investisseurId}` | **admin** |

### Marche secondaire
| Methode | Chemin                                                 | Acces      |
|---------|--------------------------------------------------------|------------|
| POST    | `/api/annonces`                                        | authentifie|
| GET     | `/api/annonces` (paginee)                              | authentifie|
| GET     | `/api/annonces/me`                                     | authentifie|
| GET     | `/api/annonces/vendeur/{id}`                           | authentifie|
| GET     | `/api/annonces/{id}`                                   | authentifie|
| PUT     | `/api/annonces/{id}`                                   | vendeur    |
| DELETE  | `/api/annonces/{id}`                                   | vendeur    |
| POST    | `/api/marche-secondaire/annonces/{id}/acheter`         | authentifie|

### Notifications
| Methode | Chemin                                 | Acces      |
|---------|----------------------------------------|------------|
| GET     | `/api/notifications/me`                | authentifie|
| PUT     | `/api/notifications/me/lu-tout`        | authentifie|
| PUT     | `/api/notifications/{id}/lu`           | authentifie|
| GET     | `/api/notifications/investisseur/{id}` | **admin**  |

### Revenus
| Methode | Chemin                            | Acces      |
|---------|-----------------------------------|------------|
| POST    | `/api/revenus`                    | **admin**  |
| GET     | `/api/revenus`                    | authentifie|
| GET     | `/api/revenus/{id}`               | authentifie|
| GET     | `/api/revenus/propriete/{id}`     | authentifie|

### Distribution & Dividendes
| Methode | Chemin                                     | Acces      |
|---------|--------------------------------------------|------------|
| POST    | `/api/distribution/{revenuId}`             | **admin**  |
| GET     | `/api/dividendes/me`                       | authentifie|
| GET     | `/api/dividendes/revenu/{id}`              | authentifie|
| GET     | `/api/dividendes/investisseur/{id}`        | **admin**  |
| GET     | `/api/dividendes`                          | **admin**  |

### Dashboard
| Methode | Chemin                                     | Acces      |
|---------|--------------------------------------------|------------|
| GET     | `/api/dashboard/me`                        | authentifie|
| GET     | `/api/dashboard/investisseur/{id}`         | **admin**  |
| GET     | `/api/dashboard/admin`                     | **admin**  |

### Infra & Monitoring
| Methode | Chemin                     | Acces  |
|---------|----------------------------|--------|
| GET     | `/api/health`              | public |
| GET     | `/actuator/health`         | public |
| GET     | `/actuator/info`           | public |
| GET     | `/actuator/metrics`        | authentifie |
| GET     | `/actuator/prometheus`     | public |
| GET     | `/swagger-ui`              | public |
| GET     | `/v3/api-docs`             | public |

---

## Comptes de la plateforme

### Compte admin actif en production

| Champ     | Valeur                      |
|-----------|-----------------------------|
| URL       | https://api.fursas.duckdns.org |
| Email     | `tiomelajorel@gmail.com`    |
| Password  | `jorel2026`                 |
| Role      | ADMIN                       |
| Verifie   | oui                         |

C'est actuellement le **seul compte en production**. Tous les comptes de seed
ont ete purges apres la bascule en profil `prod`. Pour creer un autre admin :
`POST /api/user/auth/register` puis `UPDATE users SET role='ADMIN' WHERE email='...'`
directement en DB (pas d'endpoint self-service de promotion).

> Securite : `jorel2026` passe la password policy mais reste predictible.
> A changer apres stabilisation des workflows (voir DEPLOYMENT.md pour la procedure).

### Comptes de seed (profil dev/non-prod uniquement)

Au premier demarrage sur une DB vierge avec `SPRING_PROFILES_ACTIVE != prod`,
`DataSeeder` insere automatiquement :

| Email                  | Password       | Role         |
|------------------------|----------------|--------------|
| `admin@fursa.test`     | `admin123`     | ADMIN        |
| `investor1@fursa.test` | `password123`  | INVESTISSEUR |
| `investor2@fursa.test` | `password123`  | INVESTISSEUR |
| `investor3@fursa.test` | `password123`  | INVESTISSEUR |

Les mots de passe sont encodes en BCrypt avant persistance par le seeder.
**Aucun seed n'est execute en profil `prod`** (`@Profile("!prod")`).

### Proprietes seedees

| ID | Nom                       | Parts | Prix/Part | Rentabilite |
|----|---------------------------|-------|-----------|-------------|
| 1  | Fumba Town Villa          | 1000  | 100.00    | 8.5%        |
| 2  | Paje Squares Apartment    | 500   | 200.00    | 10.0%       |
| 3  | Stone Town Heritage House | 300   | 150.00    | 12.0%       |

Plus : 2 possessions pre-existantes sur Fumba (`investor2`: 100 parts,
`investor1`: 60), 1 revenu de 5000 EUR sur Fumba, 1 annonce ouverte
(`investor2` vend 30 parts a 120 EUR).

---

## Tests

### Tests unitaires + integration Spring Boot

```bash
./mvnw test
# => 49 tests : AnnonceService (11), ProprieteController (9), ProprieteService (11),
#    FileStorageService (7), DistributionService (5), NotificationService (5),
#    FursaBackendApplicationTests (1)
```

### Smoke tests bout-en-bout contre la prod

```bash
bash scripts/smoke-test.sh
# ou autre environnement/admin :
bash scripts/smoke-test.sh https://staging.example.com admin@x.com password
```

66 tests couvrant 13 modules. Voir `TEST_PLAN.md` pour la matrice complete.

---

## Configuration

### Variables d'environnement (docker-compose `.env`)

```env
SPRING_PROFILES_ACTIVE=prod          # dev | prod
POSTGRES_DB=fursa
POSTGRES_USER=postgres
POSTGRES_PASSWORD=<obligatoire>
JWT_SECRET=<obligatoire, min 32 octets>
JWT_EXPIRATION_MS=86400000
CORS_ALLOWED_ORIGINS=https://app.fursas.duckdns.org,http://localhost:3000
```

### Profils Spring

| Propriete            | dev          | prod                |
|----------------------|--------------|---------------------|
| `ddl-auto`           | update       | **validate**        |
| `show-sql`           | true         | false               |
| `sql.init.mode`      | always       | never               |
| `DataSeeder`         | actif        | **desactive**       |
| Logs `com.fursa`     | DEBUG        | INFO                |
| Logs Security        | DEBUG        | WARN                |

---

## Observabilite

- **Logs correles** : chaque ligne porte `[requestId=<uuid>]` via `RequestIdFilter` + MDC.
  Le `X-Request-Id` fourni par un reverse proxy est reutilise sinon un UUID est genere.
- **Metriques Prometheus** : `/actuator/prometheus` expose `http_server_requests_*`,
  `jvm_memory_*`, `hikaricp_*`, `jdbc_connections_*`.
- **Health probes** : `/actuator/health/liveness`, `/actuator/health/readiness`.

---

## Gestion d'erreurs

`GlobalExceptionHandler` mappe les exceptions en codes HTTP coherents et
un body JSON uniforme :

```json
{
  "timestamp": "2026-04-24T02:47:12.123Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation echouee",
  "fieldErrors": { "email": "doit etre une adresse email valide" }
}
```

| Exception                               | Code |
|-----------------------------------------|------|
| `EntityNotFoundException`               | 404  |
| `IllegalArgumentException`              | 400  |
| `IllegalStateException`                 | 400  |
| `MethodArgumentNotValidException`       | 400 (+ fieldErrors) |
| `AccessDeniedException`                 | 403  |
| `OptimisticLockingFailureException`     | 409  |
| `DataIntegrityViolationException`       | 409  |
| `MaxUploadSizeExceededException`        | 413  |
| `MultipartException`                    | 400  |
| `HttpMediaTypeNotSupportedException`    | 415  |
| `Exception` / `RuntimeException`        | 500 (stacktrace loggee, pas exposee) |

---

## Modules du projet

| Module                                              | Branche d'origine                  |
|-----------------------------------------------------|------------------------------------|
| Securite & Utilisateurs (JWT + Spring Security)     | `feature/authentication`           |
| Catalogue & Fichiers (CRUD Propriete + upload)      | `feature/crud-propriete`           |
| Marche Primaire & Transactions                      | `feat/module_transactions`         |
| Marche Secondaire & Notifications                   | PR #1 (`feature/secondary-market`) |
| Rendement & Structure globale (dividendes + OpenAPI)| `feature/dividend-calculation`     |

---

## CI/CD

Workflow : `.github/workflows/deploy.yml` - declencheur : push sur `main`.

1. `actions/checkout@v5`
2. SSH vers le VPS avec `VPS_SSH_KEY` (secret GitHub)
3. `git fetch + reset --hard` (auth via `GITHUB_TOKEN`)
4. `docker compose build fursa-backend && docker compose up -d`
5. `docker image prune -f`
6. Health check : `curl https://.../api/health` attendu 200

Secrets GitHub requis : `VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY`.

---

## Fichiers de reference

- `DEPLOYMENT.md` - procedure de bascule prod, generation secrets, rotation JWT, backup DB
- `TEST_PLAN.md` - matrice des 66 tests automatises + tests hors scope (charge, UX)
- `scripts/smoke-test.sh` - script bash exécutable, utilisable en CI ou manuel
- `diagrammes/` - MCD, MLD, diagrammes de sequence, UC
