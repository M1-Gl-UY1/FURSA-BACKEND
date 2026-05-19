-- Migration idempotente : table idempotency_record + contrainte unique Possession
-- A appliquer en prod avant le deploiement Spring Boot qui exige ces objets DB
-- (ddl-auto=validate refuse de demarrer si table/contrainte manquantes).

CREATE TABLE IF NOT EXISTS idempotency_record (
    id BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(100) NOT NULL,
    user_id BIGINT NOT NULL,
    endpoint VARCHAR(100) NOT NULL,
    response_body TEXT,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_idempotency_key_user_endpoint UNIQUE (idempotency_key, user_id, endpoint)
);

CREATE INDEX IF NOT EXISTS idx_idempotency_created_at ON idempotency_record(created_at);

-- Contrainte unique Possession (Postgres 16 ne supporte pas ADD CONSTRAINT IF NOT EXISTS, donc DO block)
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_possession_investisseur_propriete') THEN
    ALTER TABLE possession ADD CONSTRAINT uk_possession_investisseur_propriete UNIQUE (id_inv, id_prop);
  END IF;
END $$;
