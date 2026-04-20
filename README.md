# FURSA Backend - API REST

Plateforme d'investissement immobilier fractionne en Afrique (Zanzibar, Tanzanie).
Backend Spring Boot pour la gestion des transactions, paiements et possessions.

---

## Stack technique

| Composant       | Technologie                |
|-----------------|----------------------------|
| Backend         | Java 21 / Spring Boot 4.0.5 |
| Base de donnees | PostgreSQL                 |
| ORM             | Hibernate / JPA            |
| Securite        | Spring Security (JWT prevu)|
| Build           | Maven                      |

---

## Prerequisites

- Java 21 (JDK)
- PostgreSQL installe et demarre
- Base de donnees `fursa` creee

```sql
CREATE DATABASE fursa;
```

---

## Installation et lancement

```bash
# Cloner le projet
git clone <url-du-repo>
cd FURSA-BACKEND

# Configurer la base de donnees dans src/main/resources/application.yaml
# Modifier username et password selon votre configuration PostgreSQL

# Lancer l'application
./mvnw clean spring-boot:run
```

L'API demarre sur **http://localhost:8081**

---

## Structure du projet

```
src/main/java/com/fursa/fursa_backend/
├── config/                  # Configuration (Security, CORS)
├── controller/              # Controllers REST
│   └── MarchePrimaireController.java
├── dto/                     # Objets de transfert de donnees
│   ├── AchatRequest.java
│   ├── AchatResponse.java
│   ├── PossessionResponse.java
│   ├── TransactionResponse.java
│   └── PaiementResponse.java
├── model/                   # Entites JPA
│   ├── User.java
│   ├── Admin.java
│   ├── Investisseur.java
│   ├── Propriete.java
│   ├── Paiement.java
│   ├── Transaction.java
│   ├── Possession.java
│   ├── Annonce.java
│   ├── Document.java
│   ├── Notification.java
│   ├── Revenus.java
│   ├── Dividende.java
│   └── enumeration/
│       ├── StatutPaiement.java
│       ├── StatutTransaction.java
│       ├── StatutPropriete.java
│       ├── StatutAnnonce.java
│       ├── TypePaiement.java
│       ├── TypeDocument.java
│       ├── TypeMessage.java
│       └── Devise.java
├── repository/              # Repositories JPA
│   ├── PaiementRepository.java
│   ├── TransactionRepository.java
│   ├── PossessionRepository.java
│   ├── ProprieteRepository.java
│   └── InvestisseurRepository.java
├── service/                 # Logique metier
│   └── MarchePrimaireService.java
├── seed/                    # Donnees de test
│   └── DataSeeder.java
└── FursaBackendApplication.java
```

---

## Module : Marche Primaire & Transactions (Jorel)

### Description

Ce module gere le flux complet d'achat de parts d'une propriete sur le marche primaire :

1. **Creation de l'intention d'achat** : un `Paiement` est cree avec le statut `EN_ATTENTE`
2. **Creation de la Transaction** : une `Transaction` est generee avec un hash blockchain simule (V1)
3. **Logique metier critique** : si la transaction est `SUCCES`, les parts sont attribuees a l'investisseur dans la table `Possession` et les parts disponibles de la propriete sont diminuees

### Endpoints

#### `POST /api/marche-primaire/acheter`

Acheter des parts d'une propriete.

**Request body :**

```json
{
  "investisseurId": 1,
  "proprieteId": 1,
  "nombreParts": 5
}
```

**Response (200 OK) :**

```json
{
  "paiementId": 1,
  "transactionId": 1,
  "hashTransaction": "0x4a3b2c1d5e6f7890abcdef1234567890",
  "statut": "SUCCES",
  "nombreParts": 5,
  "montantTotal": 500.00,
  "proprieteNom": "Fumba Town Villa",
  "dateTransaction": "2026-04-18T16:30:00"
}
```

---

#### `GET /api/marche-primaire/possessions`

Recuperer toutes les possessions (tous les investisseurs).

**Response (200 OK) :**

```json
[
  {
    "possessionId": 1,
    "proprieteNom": "Fumba Town Villa",
    "proprieteLocalisation": "Zanzibar, Tanzanie",
    "nombreParts": 5,
    "prixUnitairePart": 100.00,
    "valeurTotale": 500.00,
    "rentabilitePrevue": 8.5
  }
]
```

---

#### `GET /api/marche-primaire/transactions`

Recuperer toutes les transactions.

**Response (200 OK) :**

```json
[
  {
    "transactionId": 1,
    "hashTransaction": "0x4a3b2c1d5e6f7890abcdef1234567890",
    "typeOperation": "ACHAT",
    "statut": "SUCCES",
    "nombreParts": 5,
    "montant": 500.00,
    "proprieteNom": "Fumba Town Villa",
    "dateTransaction": "2026-04-18T16:30:00"
  }
]
```

---

#### `GET /api/marche-primaire/paiements`

Recuperer tous les paiements.

**Response (200 OK) :**

```json
[
  {
    "paiementId": 1,
    "montant": 500.00,
    "typePaiement": "CRYPTO",
    "statut": "VALIDE",
    "nombreParts": 5,
    "proprieteNom": "Fumba Town Villa",
    "date": "2026-04-18T16:30:00"
  }
]
```

---

#### `GET /api/marche-primaire/possessions/{investisseurId}`

Consulter le portefeuille d'un investisseur specifique.

**Response (200 OK) :** meme format que `/possessions` mais filtre par investisseur.

---

#### `GET /api/marche-primaire/transactions/{investisseurId}`

Historique des transactions d'un investisseur specifique.

**Response (200 OK) :** meme format que `/transactions` mais filtre par investisseur.

---

#### `GET /api/marche-primaire/paiements/{investisseurId}`

Historique des paiements d'un investisseur specifique.

**Response (200 OK) :** meme format que `/paiements` mais filtre par investisseur.

---

**Erreurs possibles :**

| Cas                              | Message                                           |
|----------------------------------|----------------------------------------------------|
| Investisseur introuvable         | Investisseur non trouve avec l'id : X              |
| Propriete introuvable            | Propriete non trouvee avec l'id : X                |
| Propriete non publiee            | Cette propriete n'est pas disponible a l'achat     |
| Parts insuffisantes              | Parts insuffisantes. Disponibles : X               |
| Nombre de parts invalide         | Le nombre de parts doit etre superieur a 0         |

### Flux detaille

```
Investisseur                    Backend                         Base de donnees
     |                              |                                |
     |-- POST /acheter ------------>|                                |
     |   {invId, propId, nbParts}   |                                |
     |                              |-- Verifier investisseur ------>|
     |                              |-- Verifier propriete --------->|
     |                              |-- Verifier disponibilite       |
     |                              |-- Calculer montant             |
     |                              |                                |
     |                              |-- Creer Paiement (EN_ATTENTE)->|
     |                              |-- Generer faux hash blockchain |
     |                              |-- Creer Transaction (SUCCES)-->|
     |                              |                                |
     |                              |-- Valider Paiement (VALIDE)--->|
     |                              |-- Creer/MAJ Possession ------->|
     |                              |-- Diminuer parts disponibles ->|
     |                              |                                |
     |<-- 200 OK + AchatResponse ---|                                |
```

---

## Donnees de test (Seed)

Au premier demarrage, le `DataSeeder` insere automatiquement :

### Investisseurs

| ID | Nom      | Prenom | Email            |
|----|----------|--------|------------------|
| 1  | TIOMELA  | Jorel  | jorel@fursa.com  |
| 2  | Martin   | Alice  | alice@fursa.com  |

### Proprietes

| ID | Nom                      | Localisation      | Parts | Prix/Part | Rentabilite |
|----|--------------------------|-------------------|-------|-----------|-------------|
| 1  | Fumba Town Villa         | Zanzibar          | 1000  | 100.00    | 8.5%        |
| 2  | Paje Squares Apartment   | Paje, Zanzibar    | 500   | 200.00    | 10.0%       |
| 3  | Stone Town Heritage House| Stone Town        | 300   | 150.00    | 12.0%       |

---

## Repartition des modules par membre

| Membre  | Module                            |
|---------|-----------------------------------|
| Emile   | Securite & Utilisateurs (JWT)     |
| Imelda  | Catalogue & Fichiers (CRUD Propriete) |
| Jorel   | Marche Primaire & Transactions    |
| Mimche  | Marche Secondaire & Notifications |
| Idriss  | Rendement & Structure globale     |

---

## Deploiement

### Docker local

```bash
docker compose up -d --build
```

Demarre 2 containers :
- `fursa-db` : PostgreSQL 16 (volume persistant `fursa-db-data`)
- `fursa-backend` : API Spring Boot (port 8081)

### Deploiement VPS (production)

API en production : **https://api.fursas.duckdns.org**

- VPS Contabo (Ubuntu 24.04, IP 84.247.183.206)
- Repo deploye dans `~/Fursa/FURSA-BACKEND/`
- Orchestration Docker (meme `docker-compose.yml` qu'en local)
- Nginx reverse proxy + SSL Let's Encrypt (renouvellement automatique via `certbot.timer`)

### CI/CD (GitHub Actions)

Workflow : `.github/workflows/deploy.yml`

Tout push sur `main` declenche automatiquement :
1. Connexion SSH au VPS (cle `koursa_deploy`)
2. `git pull origin main` (auth via `GITHUB_TOKEN` ephemere)
3. `docker compose build fursa-backend`
4. `docker compose up -d` (redemarrage sans downtime sur la DB)
5. `docker image prune -f`
6. Verification de sante de l'API

**Secrets GitHub requis** (deja configures) :
- `VPS_HOST` : `api.fursas.duckdns.org`
- `VPS_USER` : `softengine`
- `VPS_SSH_KEY` : cle privee SSH du VPS

Deploiement manuel possible via onglet *Actions* → *Deploy FURSA Backend to VPS* → *Run workflow*.

---

## Configuration

Le fichier `src/main/resources/application.yaml` contient :

```yaml
server:
  port: 8081

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/fursa
    username: postgres
    password: <votre-mot-de-passe>
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
```
