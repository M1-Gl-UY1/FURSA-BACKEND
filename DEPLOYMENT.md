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

```bash
docker exec -it fursa-db psql -U postgres -d fursa <<'SQL'
DELETE FROM users WHERE email IN (
  'admin@fursa.test',
  'investor1@fursa.test',
  'investor2@fursa.test',
  'investor3@fursa.test'
);
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

```bash
# Health public
curl -s https://api.fursas.duckdns.org/api/health
# => {"status":"UP"}

# Register d'un vrai investisseur
curl -sX POST https://api.fursas.duckdns.org/api/user/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"founder@fursas.com","password":"Changeme2026","nom":"Founder","prenom":"Admin","telephone":"+237600000000"}'

# Login
TOKEN=$(curl -sX POST https://api.fursas.duckdns.org/api/user/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"founder@fursas.com","password":"Changeme2026"}' | jq -r .token)

# Ne PEUT PAS distribuer (role=INVESTISSEUR par defaut) -> 403 attendu
curl -si -X POST https://api.fursas.duckdns.org/api/distribution/1 \
  -H "Authorization: Bearer ${TOKEN}" | head -n 1
# => HTTP/2 403

# Promouvoir manuellement cet utilisateur en ADMIN via DB (une seule fois) :
docker exec -it fursa-db psql -U postgres -d fursa \
  -c "UPDATE users SET role='ADMIN' WHERE email='founder@fursas.com';"
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
