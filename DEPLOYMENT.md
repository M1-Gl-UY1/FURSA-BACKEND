# Deploiement en production

## Bascule du VPS vers le profil `prod` (procedure unique)

A faire **une fois** sur le VPS avant de pusher le code qui contient `docker-compose.yml` avec les variables obligatoires.

### 1. Se connecter au VPS

```bash
ssh -i ~/.ssh/koursa_deploy softengine@api.fursas.duckdns.org
cd ~/Fursa/FURSA-BACKEND
```

### 2. Generer les secrets

```bash
# JWT : 48 octets aleatoires base64 = ~64 caracteres, largement > 32.
JWT_SECRET=$(openssl rand -base64 48)

# Mot de passe DB : 32 octets aleatoires, url-safe.
DB_PASSWORD=$(openssl rand -hex 24)

# Les afficher une seule fois et les copier dans un coffre-fort (1Password, Vault).
echo "JWT_SECRET=${JWT_SECRET}"
echo "POSTGRES_PASSWORD=${DB_PASSWORD}"
```

### 3. Ecrire le `.env` de production

```bash
cat > .env <<EOF
SPRING_PROFILES_ACTIVE=prod

POSTGRES_DB=fursa
POSTGRES_USER=postgres
POSTGRES_PASSWORD=${DB_PASSWORD}

JWT_SECRET=${JWT_SECRET}
JWT_EXPIRATION_MS=86400000

CORS_ALLOWED_ORIGINS=https://app.fursas.duckdns.org
EOF

chmod 600 .env
```

### 4. Migrer le mot de passe Postgres existant

Le volume `fursa-db-data` contient deja une DB avec `POSTGRES_PASSWORD=scorp`.
Changer le mot de passe **dans la DB** (sinon l'app ne pourra plus se connecter) :

```bash
docker exec -it fursa-db psql -U postgres -d fursa \
  -c "ALTER USER postgres WITH PASSWORD '${DB_PASSWORD}';"
```

### 5. Purger les comptes demo residuels (si la DB a deja ete seedee)

Les 4 comptes seedes par `DataSeeder` en profil dev n'ont **rien a faire en prod**
(credentials publiquement documentes, vecteur d'attaque trivial) :

| Email                  | Password seed | Role         |
|------------------------|---------------|--------------|
| `admin@fursa.test`     | `admin123`    | ADMIN        |
| `investor1@fursa.test` | `password123` | INVESTISSEUR |
| `investor2@fursa.test` | `password123` | INVESTISSEUR |
| `investor3@fursa.test` | `password123` | INVESTISSEUR |

```bash
docker exec -it fursa-db psql -U postgres -d fursa <<'SQL'
-- Cascade dans l'ordre pour eviter les FK violations
DELETE FROM transaction WHERE id_paie IN (SELECT id_paie FROM paiement WHERE id_inv IN (SELECT id_user FROM users WHERE email LIKE '%@fursa.test'));
DELETE FROM paiement    WHERE id_inv IN (SELECT id_user FROM users WHERE email LIKE '%@fursa.test');
DELETE FROM notification WHERE id_inv IN (SELECT id_user FROM users WHERE email LIKE '%@fursa.test');
DELETE FROM dividende    WHERE id_inv IN (SELECT id_user FROM users WHERE email LIKE '%@fursa.test');
DELETE FROM possession   WHERE id_inv IN (SELECT id_user FROM users WHERE email LIKE '%@fursa.test');
DELETE FROM annonce      WHERE id_inv IN (SELECT id_user FROM users WHERE email LIKE '%@fursa.test');
DELETE FROM investisseur WHERE id_user IN (SELECT id_user FROM users WHERE email LIKE '%@fursa.test');
DELETE FROM admin        WHERE id_user IN (SELECT id_user FROM users WHERE email LIKE '%@fursa.test');
DELETE FROM users        WHERE email LIKE '%@fursa.test';
SQL
```

### 6. Redemarrer avec le profil prod

```bash
docker compose down
docker compose up -d --build
docker logs -f fursa-backend
```

Verifier que l'application demarre sans erreur, que `JwtUtils.validateSecret()`
ne leve pas d'exception, et que `/api/health` retourne 200.

### 7. Smoke tests

Script automatise :

```bash
bash scripts/smoke-test.sh https://api.fursas.duckdns.org tiomelajorel@gmail.com jorel2026
# => 66/66 tests passants
```

Verification manuelle rapide :

```bash
# Health public
curl -s https://api.fursas.duckdns.org/api/health
# => {"status":"UP"}

# Login du compte admin actuel de la prod
TOKEN=$(curl -sX POST https://api.fursas.duckdns.org/api/user/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"tiomelajorel@gmail.com","password":"jorel2026"}' | jq -r .token)

# Dashboard admin (confirme le role ADMIN)
curl -s -H "Authorization: Bearer ${TOKEN}" https://api.fursas.duckdns.org/api/dashboard/admin
```

### Creer un autre admin (facultatif)

Spring ne permet pas l'auto-promotion en ADMIN par API (securite). Procedure :

```bash
# 1. L'utilisateur s'inscrit normalement (role=INVESTISSEUR par defaut)
curl -sX POST https://api.fursas.duckdns.org/api/user/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"newadmin@fursas.com","password":"ChooseStrong2026","nom":"Admin","prenom":"Two","telephone":"+237600000001"}'

# 2. Promotion manuelle par un admin existant via DB (one-shot)
docker exec -it fursa-db psql -U postgres -d fursa \
  -c "UPDATE users SET role='ADMIN' WHERE email='newadmin@fursas.com';"
docker exec -it fursa-db psql -U postgres -d fursa \
  -c "UPDATE investisseur SET is_verified=true WHERE id_user=(SELECT id_user FROM users WHERE email='newadmin@fursas.com');"

# 3. L'utilisateur doit se relogger pour avoir un nouveau token avec le role ADMIN.
```

---

## Proprietes Spring en prod

Au runtime, les valeurs effectives sont lues dans cet ordre :

1. Variables d'environnement (Docker compose `.env`)
2. `application-prod.yaml` (charge si `SPRING_PROFILES_ACTIVE=prod`)
3. `application.yaml`

En prod, `application-prod.yaml` impose :
- `ddl-auto=validate` : refuse de demarrer si le schema divergent du code
- `show-sql=false` : pas de fuite de requetes dans les logs
- `sql.init.mode=never` : pas de scripts SQL auto-executes
- `logging.level.com.fursa=INFO`

---

## Rotation du JWT_SECRET

Quand rotater : suspicion de fuite, depart d'un administrateur, changement majeur.

Consequence : **tous les tokens emis sont invalides** -> tous les users doivent
se re-logger. C'est normal et attendu.

```bash
ssh softengine@api.fursas.duckdns.org
cd ~/Fursa/FURSA-BACKEND

NEW_SECRET=$(openssl rand -base64 48)
sed -i "s|^JWT_SECRET=.*|JWT_SECRET=${NEW_SECRET}|" .env
docker compose restart fursa-backend
```

---

## Checklist pre-deploiement (pour chaque release)

- [ ] `./mvnw clean verify` passe localement
- [ ] Branche mergee dans main (CI verte)
- [ ] Pas de changement d'entite JPA non retro-compatible (ddl-auto=validate refuserait)
  - Si renommage de colonne : prevoir une migration SQL manuelle avant le deploy
  - Si suppression de colonne : idem
- [ ] Verifier l'impact sur les tokens JWT existants si on touche a `JwtFilter` ou `CustomUserService`
- [ ] Preparer un rollback : note du commit precedent pour `git reset --hard <sha>` cote VPS si besoin

## Backup DB (a mettre en place)

```bash
# Cron quotidien 3h du matin, retention 7 jours
0 3 * * * docker exec fursa-db pg_dump -U postgres fursa | gzip > /var/backups/fursa/$(date +\%Y\%m\%d).sql.gz && find /var/backups/fursa -mtime +7 -delete
```
