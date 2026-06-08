#!/usr/bin/env bash
#
# V2 Z (07/06/2026) : garantit que le .env de prod contient les variables
# blockchain audit on-chain (Phases P + T).
#
# A executer apres tout `git pull` qui modifierait .env, ou apres restauration
# de backup. Idempotent : ne fait rien si tout est deja present.
#
# Usage : ./scripts/ensure-onchain-env.sh [.env-path]
#

set -euo pipefail

ENV_FILE="${1:-.env}"
if [ ! -f "$ENV_FILE" ]; then
    echo "[ensure-onchain-env] $ENV_FILE introuvable. Abandon."
    exit 1
fi

# Valeurs canoniques deployees sur Sepolia (08/06/2026 - V2 CC reset prod).
# Anciens singletons (07/06) abandonnes apres purge complete BDD/uploads.
# Si Polygon mainnet : changer ici puis re-executer.
LEDGER_ADDR="0xf5c515A73Bb453759179e7d6cA13929ca93d3Dc6"
KYC_ADDR="0xAEC8A668a3bB52D9f6Ca2440FEC187d827d4fE2f"

# Backup avant modification.
TS=$(date +%Y%m%d-%H%M%S)
cp "$ENV_FILE" "${ENV_FILE}.backup-${TS}"

added=0

ensure_var() {
    local key="$1"
    local val="$2"
    local placeholder_msg="$3"
    if grep -q "^${key}=" "$ENV_FILE"; then
        local current
        current=$(grep "^${key}=" "$ENV_FILE" | head -1 | cut -d= -f2-)
        if [ -z "$current" ]; then
            # cle presente mais vide -> on remplit
            sed -i "s|^${key}=$|${key}=${val}|" "$ENV_FILE"
            echo "[ensure-onchain-env] $key : valeur remplie."
            added=$((added + 1))
        else
            echo "[ensure-onchain-env] $key : deja renseigne (skip)."
        fi
    else
        echo "" >> "$ENV_FILE"
        echo "$placeholder_msg" >> "$ENV_FILE"
        echo "${key}=${val}" >> "$ENV_FILE"
        echo "[ensure-onchain-env] $key : ajoute."
        added=$((added + 1))
    fi
}

ensure_var "BLOCKCHAIN_REVENUE_LEDGER_ADDRESS" "$LEDGER_ADDR" \
    "# V2 P (07/06/2026) audit on-chain revenus + distributions"
ensure_var "BLOCKCHAIN_KYC_REGISTRY_ADDRESS" "$KYC_ADDR" \
    "# V2 T (07/06/2026) audit on-chain KYC RGPD-safe"

# KYC_ONCHAIN_SALT : on ne le regenere JAMAIS. Si absent, on previent l'operateur
# mais on ne genere pas automatiquement (risque de perdre le sel d'origine).
if ! grep -q "^KYC_ONCHAIN_SALT=" "$ENV_FILE"; then
    echo "" >> "$ENV_FILE"
    echo "# CRITIQUE : sel secret pour hash KYC. NE JAMAIS changer apres mise en service." >> "$ENV_FILE"
    echo "KYC_ONCHAIN_SALT=$(openssl rand -hex 32)" >> "$ENV_FILE"
    echo "[ensure-onchain-env] KYC_ONCHAIN_SALT : genere (premiere installation)."
    echo "                     >>> Sauvegarde-le immediatement dans un coffre-fort."
    added=$((added + 1))
fi

if ! grep -q "^KYC_DUREE_VALIDITE_JOURS=" "$ENV_FILE"; then
    echo "KYC_DUREE_VALIDITE_JOURS=365" >> "$ENV_FILE"
    echo "[ensure-onchain-env] KYC_DUREE_VALIDITE_JOURS=365 : ajoute."
    added=$((added + 1))
fi

if [ "$added" -eq 0 ]; then
    echo "[ensure-onchain-env] Tout est en place. Aucune modification."
    rm "${ENV_FILE}.backup-${TS}"
else
    echo "[ensure-onchain-env] $added variable(s) ajoutee(s). Backup : ${ENV_FILE}.backup-${TS}"
    echo "[ensure-onchain-env] Lance maintenant : docker compose up -d fursa-backend"
fi
