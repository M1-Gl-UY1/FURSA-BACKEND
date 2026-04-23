# FURSA Backend - API REST

Plateforme d'investissement immobilier fractionne en Afrique (Zanzibar, Tanzanie).
Backend Spring Boot couvrant authentification JWT, catalogue immobilier, marche primaire (achat de parts), marche secondaire (revente entre investisseurs), distribution de dividendes et notifications.

---

## Stack technique

| Composant       | Technologie                          |
|-----------------|--------------------------------------|
| Backend         | Java 21 / Spring Boot 4.0.5          |
| Base de donnees | PostgreSQL 16                        |
| ORM             | Hibernate / JPA                      |
| Securite        | Spring Security + JWT (JJWT 0.11.5)  |
| API docs        | SpringDoc OpenAPI 2.8.14 (Swagger UI)|
| Build           | Maven                                |
| Tests           | JUnit 5 + Mockito                    |

---

## Prerequis

- Java 21 (JDK)
- PostgreSQL 16 (ou utiliser `docker compose`)

---

## Lancement local

### Option 1 : Docker (recommande)

```bash
docker compose up -d --build
```

Demarre 2 containers :
- `fursa-db` : PostgreSQL 16 (volume persistant `fursa-db-data`)
- `fursa-backend` : API Spring Boot (port 8081)

Pour repartir d'une DB vierge (re-execution du seed) : `docker compose down -v`.

### Option 2 : Maven

```bash
createdb fursa   # ou via psql
./mvnw spring-boot:run
```

L'API demarre sur **http://localhost:8081**.
Swagger UI : **http://localhost:8081/swagger-ui**.

---

## Production

- API : **https://api.fursas.duckdns.org**
- VPS Contabo Ubuntu 24.04, Docker + Nginx reverse proxy + Let's Encrypt (auto-renew via `certbot.timer`)
- CI/CD : GitHub Actions, push sur `main` -> deploiement automatique (`.github/workflows/deploy.yml`)

---

## Structure du projet

```
src/main/java/com/fursa/fursa_backend/
├── config/                    # SecurityConfig, JwtUtils
├── controller/
│   ├── HealthController             # GET /api/health (public)
│   ├── UserController               # auth (register, login) + CRUD user
│   ├── ProprieteController          # CRUD proprietes│   ├── FileController               # upload documents│   ├── MarchePrimaireController     # achat primaire + consultations│   ├── AnnonceController            # CRUD annonces│   ├── MarcheSecondaireController   # achat d'une annonce│   ├── NotificationController       # consult + mark-read│   └── DistributionController       # distribution dividendes├── dto/                       # records / POJOs de Request/Response
├── exception/GlobalExceptionHandler
├── filter/JwtFilter
├── mapper/ProprieteMapper
├── model/                     # entites JPA (User, Investisseur, Admin, Propriete,
│                              #  Paiement, Transaction, Possession, Annonce,
│                              #  Notification, Revenus, Dividende, Document)
│   └── enumeration/           # StatutXxx, TypeXxx, Role, Devise
├── repository/                # JpaRepository pour chaque entite metier
├── service/
│   ├── CustomUserService              # UserDetailsService pour Spring Security
│   ├── AuthenticatedInvestisseurService  # helper pour recuperer l'investisseur courant
│   ├── FileStorageService
│   ├── ProprieteService
│   ├── MarchePrimaireService
│   ├── AnnonceService
│   ├── NotificationService
│   └── DistributionServiceImpl
├── seed/DataSeeder            # donnees de demarrage
└── FursaBackendApplication.java
```

---

## Authentification

Tous les endpoints sont proteges par JWT, sauf :
- `POST /api/user/auth/register`, `POST /api/user/auth/login`
- `GET /api/health`
- `/swagger-ui/**`, `/v3/api-docs/**`

### 1. S'inscrire ou se connecter

```bash
curl -X POST https://api.fursas.duckdns.org/api/user/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "investor1@fursa.test", "password": "password123"}'
# => { "token": "eyJhbGciOi..." }
```

### 2. Utiliser le token

```bash
curl -H "Authorization: Bearer <token>" https://api.fursas.duckdns.org/api/annonces
```

Le `id` de l'investisseur courant est extrait du JWT par `AuthenticatedInvestisseurService`. Les endpoints `POST /acheter`, `POST /annonces`, `DELETE /annonces/{id}` n'acceptent **plus** d'id de vendeur/acheteur dans le body : ils l'obtiennent du token.

---

## Endpoints

### Auth
| Methode | Chemin                          | Public | Description                      |
|---------|---------------------------------|--------|----------------------------------|
| POST    | `/api/user/auth/register`       | oui    | Creer un compte investisseur     |
| POST    | `/api/user/auth/login`          | oui    | Obtenir un JWT                   |
| GET     | `/api/user/...`                 | non    | Gestion des utilisateurs (admin) |

### Proprietes
| Methode | Chemin                          | Description                       |
|---------|---------------------------------|-----------------------------------|
| POST    | `/api/proprietes`               | Creer une propriete (admin)       |
| GET     | `/api/proprietes`               | Lister les proprietes             |
| GET     | `/api/proprietes/{id}`          | Detail d'une propriete            |
| PUT     | `/api/proprietes/{id}`          | Modifier (admin)                  |
| DELETE  | `/api/proprietes/{id}`          | Supprimer (admin)                 |
| POST    | `/api/files`                    | Upload document (admin)           |

### Marche primaire
| Methode | Chemin                                       | Description                                       |
|---------|----------------------------------------------|---------------------------------------------------|
| POST    | `/api/marche-primaire/acheter`               | Acheter des parts (acheteur = JWT)                |
| GET     | `/api/marche-primaire/me/possessions`        | Mon portefeuille                                  |
| GET     | `/api/marche-primaire/me/transactions`       | Mes transactions                                  |
| GET     | `/api/marche-primaire/me/paiements`          | Mes paiements                                     |
| GET     | `/api/marche-primaire/possessions`           | Toutes les possessions (admin)                    |
| GET     | `/api/marche-primaire/possessions/{invId}`   | Portefeuille d'un investisseur (admin)            |
| GET     | `/api/marche-primaire/transactions`          | Toutes les transactions (admin)                   |
| GET     | `/api/marche-primaire/transactions/{invId}`  | Historique d'un investisseur (admin)              |
| GET     | `/api/marche-primaire/paiements`             | Tous les paiements (admin)                        |
| GET     | `/api/marche-primaire/paiements/{invId}`     | Paiements d'un investisseur (admin)               |

**Body `POST /acheter` :**
```json
{ "proprieteId": 1, "nombreParts": 5 }
```

### Marche secondaire
| Methode | Chemin                                              | Description                                            |
|---------|-----------------------------------------------------|--------------------------------------------------------|
| POST    | `/api/annonces`                                     | Publier une annonce (vendeur = JWT)                    |
| GET     | `/api/annonces`                                     | Lister les annonces OUVERTE                            |
| GET     | `/api/annonces/me`                                  | Mes annonces                                           |
| GET     | `/api/annonces/vendeur/{id}`                        | Annonces d'un investisseur                             |
| GET     | `/api/annonces/{id}`                                | Detail                                                 |
| DELETE  | `/api/annonces/{id}`                                | Annuler (vendeur = JWT)                                |
| POST    | `/api/marche-secondaire/annonces/{id}/acheter`      | Acheter une annonce (acheteur = JWT)                   |

**Body `POST /annonces` :**
```json
{ "proprieteId": 1, "nombreDePartsAVendre": 30, "prixUnitaireDemande": 120.00 }
```

**Body `POST /marche-secondaire/.../acheter` :**
```json
{ "nombreDeParts": 10 }
```

Cote metier : transfert de possession (suppression si 0, creation si nouvelle), creation `Paiement` + `Transaction` avec hash UUID, decrement de l'annonce (`COMPLETEE` quand epuisee), notifications envoyees au vendeur et a l'acheteur.

### Notifications
| Methode | Chemin                                              | Description                              |
|---------|-----------------------------------------------------|------------------------------------------|
| GET     | `/api/notifications/me`                             | Mes notifications (`?nonLuesSeulement=true`) |
| GET     | `/api/notifications/investisseur/{id}`              | Notifications d'un investisseur (admin)   |
| PUT     | `/api/notifications/{id}/lu`                        | Marquer comme lue                        |

### Dividendes
| Methode | Chemin                              | Description                                             |
|---------|-------------------------------------|---------------------------------------------------------|
| POST    | `/api/distribution/{revenuId}`      | Distribuer au prorata des possessions (admin)           |

Chaque dividende est persiste avec `montantCalcule`, `dateDistribution`, `statut = VALIDE`, `hashTransaction = UUID`.

### Documentation interactive

- **Swagger UI** : `https://api.fursas.duckdns.org/swagger-ui`
- **OpenAPI JSON** : `https://api.fursas.duckdns.org/v3/api-docs`

---

## Donnees de test (Seed)

Au premier demarrage sur une DB vierge, `DataSeeder` insere :

### Investisseurs (mot de passe `password123`, BCrypt)

| ID | Email                  | Nom affiche       |
|----|------------------------|-------------------|
| 1  | investor1@fursa.test   | Demo Investor One |
| 2  | investor2@fursa.test   | Demo Investor Two |
| 3  | investor3@fursa.test   | Demo Investor Three |

### Proprietes

| ID | Nom                       | Parts | Prix/Part | Rentabilite |
|----|---------------------------|-------|-----------|-------------|
| 1  | Fumba Town Villa          | 1000 (840 dispo) | 100.00 | 8.5%  |
| 2  | Paje Squares Apartment    | 500   | 200.00    | 10.0%       |
| 3  | Stone Town Heritage House | 300   | 150.00    | 12.0%       |

### Autres

- 2 possessions sur Fumba Villa : investor2 = 100 parts, investor1 = 60 parts
- 1 revenu de 5000 EUR sur Fumba Villa (pret pour `POST /api/distribution/1`)
- 1 annonce ouverte : investor2 vend 30 parts a 120 EUR (pret pour l'achat par investor3)

---

## Tests

```bash
./mvnw test
```

Suite complete : **49 tests** repartis sur tous les modules.

---

## Configuration (`src/main/resources/application.yaml`)

```yaml
server:
  port: 8081

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/fursa
    username: postgres
    password: scorp             # override via env POSTGRES_PASSWORD en Docker
  jpa:
    hibernate.ddl-auto: update
    show-sql: true

app:
  secret-key: ${JWT_SECRET:dev-only-secret-change-me-in-prod-min-32-chars-long-for-hs256}
  expiration-time: 86400000     # 24h

springdoc:
  swagger-ui.path: /swagger-ui
  api-docs.path: /v3/api-docs
```

En prod, `JWT_SECRET` doit etre surcharge via variable d'environnement (container Docker).

---

## Modules du projet

| Module                                          | Branche d'origine                      |
|-------------------------------------------------|----------------------------------------|
| Securite & Utilisateurs (JWT + Spring Security) | `feature/authentication`               |
| Catalogue & Fichiers (CRUD Propriete + upload)  | `feature/crud-propriete`               |
| Marche Primaire & Transactions                  | `feat/module_transactions`             |
| Marche Secondaire & Notifications               | PR #1 (`feature/secondary-market`)     |
| Rendement & Structure globale (dividendes)      | `feature/dividend-calculation`         |

---

## CI/CD

Workflow : `.github/workflows/deploy.yml` - declencheur : push sur `main`.

Etapes :
1. `actions/checkout@v5`
2. SSH vers le VPS avec la cle `koursa_deploy`
3. `git fetch + reset --hard FETCH_HEAD` (auth via `GITHUB_TOKEN`)
4. `docker compose build fursa-backend && docker compose up -d`
5. `docker image prune -f`
6. Verification : `curl https://.../api/health` doit retourner 200

Secrets GitHub requis : `VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY`.
