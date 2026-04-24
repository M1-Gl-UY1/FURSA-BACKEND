#!/usr/bin/env bash
# Smoke test de l'API FURSA en production.
# Usage : bash scripts/smoke-test.sh [URL_BASE] [ADMIN_EMAIL] [ADMIN_PASSWORD]
# Defaut : https://api.fursas.duckdns.org avec admin@fursa.test / admin123

set -u

BASE="${1:-https://api.fursas.duckdns.org}"
ADMIN_EMAIL="${2:-admin@fursa.test}"
ADMIN_PASSWORD="${3:-admin123}"

PASS=0
FAIL=0
FAILED_TESTS=()

# --- Helpers ----------------------------------------------------------------

color() { printf "\033[%sm%s\033[0m" "$1" "$2"; }
ok()   { printf "  %s %s\n" "$(color '32' '✓')" "$1"; PASS=$((PASS+1)); }
ko()   { printf "  %s %s\n" "$(color '31' '✗')" "$1"; FAIL=$((FAIL+1)); FAILED_TESTS+=("$1"); }
info() { printf "\n%s\n" "$(color '36' "▸ $1")"; }

# $1=description $2=expected_code $3=actual_code
assert_code() {
    if [[ "$2" == "$3" ]]; then ok "$1 (HTTP $3)"; else ko "$1 - attendu $2, recu $3"; fi
}

# $1=method $2=path $3=token(optional) $4=body(optional) -> prints HTTP status
http() {
    local method="$1" path="$2" token="${3:-}" body="${4:-}"
    local auth=() ; [[ -n "$token" ]] && auth=(-H "Authorization: Bearer $token")
    local ctype=() data=()
    if [[ -n "$body" ]]; then
        ctype=(-H "Content-Type: application/json")
        data=(--data "$body")
    fi
    curl -sS -o /tmp/fursa_body.json -w "%{http_code}" -X "$method" \
        "${auth[@]}" "${ctype[@]}" "${data[@]}" "$BASE$path" 2>/dev/null || echo "000"
}

# Extrait un champ d'une reponse JSON (sans jq, suffisant pour id/token)
jget() {
    local file="$1" key="$2"
    grep -oE "\"$key\"\\s*:\\s*\"[^\"]+\"" "$file" | head -1 | sed -E "s/.*:\s*\"([^\"]+)\".*/\1/"
}
jget_num() {
    local file="$1" key="$2"
    grep -oE "\"$key\"\\s*:\\s*[0-9]+" "$file" | head -1 | sed -E "s/.*:\s*([0-9]+).*/\1/"
}

timestamp() { date +%Y%m%d%H%M%S; }

# --- Scenario ---------------------------------------------------------------

printf "\n%s\n" "$(color '1;35' "FURSA smoke test -> $BASE")"

# ====================================================================
info "1. Health & infra publics"
# ====================================================================
assert_code "GET /api/health"          200 "$(http GET /api/health)"
assert_code "GET /actuator/health"     200 "$(http GET /actuator/health)"
assert_code "GET /actuator/prometheus" 200 "$(http GET /actuator/prometheus)"
assert_code "GET /v3/api-docs"         200 "$(http GET /v3/api-docs)"

# ====================================================================
info "2. Securite - endpoints proteges sans token"
# ====================================================================
code=$(http GET /api/proprietes/public)
if [[ "$code" == "401" || "$code" == "403" ]]; then ok "GET /proprietes sans token -> $code"; else ko "GET /proprietes sans token - attendu 401/403, recu $code"; fi

# ====================================================================
info "3. Login admin"
# ====================================================================
code=$(http POST /api/user/auth/login "" "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}")
assert_code "Login admin" 200 "$code"
ADMIN_TOKEN=$(jget /tmp/fursa_body.json token)
[[ -n "$ADMIN_TOKEN" ]] && ok "Token admin recu (${#ADMIN_TOKEN} chars)" || ko "Pas de token admin"

# ====================================================================
info "4. Register - validations"
# ====================================================================
TS=$(timestamp)
TEST_EMAIL="smoke+${TS}@fursa.test"
TEST_PWD="Password1"

# Register valide
code=$(http POST /api/user/auth/register "" "{\"email\":\"$TEST_EMAIL\",\"password\":\"$TEST_PWD\",\"nom\":\"Smoke\",\"prenom\":\"Tester\",\"telephone\":\"+237600000099\"}")
assert_code "Register valide" 201 "$code"
TEST_USER_ID=$(jget_num /tmp/fursa_body.json id)

# Register - password trop court
code=$(http POST /api/user/auth/register "" "{\"email\":\"tooshort${TS}@fursa.test\",\"password\":\"abc12\",\"nom\":\"X\",\"prenom\":\"Y\",\"telephone\":\"+237600000098\"}")
assert_code "Register password < 8 chars" 400 "$code"

# Register - password sans chiffre
code=$(http POST /api/user/auth/register "" "{\"email\":\"nodigit${TS}@fursa.test\",\"password\":\"longpasswordonly\",\"nom\":\"X\",\"prenom\":\"Y\",\"telephone\":\"+237600000097\"}")
assert_code "Register password sans chiffre" 400 "$code"

# Register - email invalide
code=$(http POST /api/user/auth/register "" "{\"email\":\"pas_un_email\",\"password\":\"Password1\",\"nom\":\"X\",\"prenom\":\"Y\",\"telephone\":\"+237600000096\"}")
assert_code "Register email invalide" 400 "$code"

# Register - injection role=ADMIN (doit etre ignoree)
INJECT_EMAIL="inject+${TS}@fursa.test"
code=$(http POST /api/user/auth/register "" "{\"email\":\"$INJECT_EMAIL\",\"password\":\"Password1\",\"nom\":\"Inj\",\"prenom\":\"Hack\",\"telephone\":\"+237600000095\",\"role\":\"ADMIN\",\"isVerified\":true}")
assert_code "Register avec injection role=ADMIN accepte" 201 "$code"
# Verifier que le role effectif est INVESTISSEUR
code=$(http POST /api/user/auth/login "" "{\"email\":\"$INJECT_EMAIL\",\"password\":\"Password1\"}")
INJ_TOKEN=$(jget /tmp/fursa_body.json token)
http GET /api/user/me "$INJ_TOKEN" > /dev/null
inj_role=$(jget /tmp/fursa_body.json role)
if [[ "$inj_role" == "INVESTISSEUR" ]]; then ok "Role effectif = INVESTISSEUR (injection echouee)"; else ko "FAILLE: role=$inj_role apres injection !"; fi

# Register - email deja utilise
code=$(http POST /api/user/auth/register "" "{\"email\":\"$TEST_EMAIL\",\"password\":\"Password1\",\"nom\":\"X\",\"prenom\":\"Y\",\"telephone\":\"+237600000094\"}")
assert_code "Register email duplicate" 400 "$code"

# ====================================================================
info "5. Login du nouveau compte"
# ====================================================================
code=$(http POST /api/user/auth/login "" "{\"email\":\"$TEST_EMAIL\",\"password\":\"$TEST_PWD\"}")
assert_code "Login investisseur test" 200 "$code"
TEST_TOKEN=$(jget /tmp/fursa_body.json token)

# Login mauvais password
code=$(http POST /api/user/auth/login "" "{\"email\":\"$TEST_EMAIL\",\"password\":\"wrong\"}")
assert_code "Login mauvais password" 401 "$code"

# ====================================================================
info "6. Utilisateurs"
# ====================================================================
assert_code "GET /api/user/me (investor)"                 200 "$(http GET /api/user/me "$TEST_TOKEN")"
assert_code "GET /api/user/{myId} (self)"                 200 "$(http GET "/api/user/$TEST_USER_ID" "$TEST_TOKEN")"
assert_code "GET /api/user/{autreId} (non-admin)"         403 "$(http GET "/api/user/1" "$TEST_TOKEN")"
assert_code "GET /api/user (non-admin)"                   403 "$(http GET "/api/user" "$TEST_TOKEN")"
assert_code "GET /api/user (admin)"                       200 "$(http GET "/api/user" "$ADMIN_TOKEN")"
assert_code "POST /api/user/{id}/valider (non-admin)"     403 "$(http POST "/api/user/$TEST_USER_ID/valider" "$TEST_TOKEN")"
assert_code "POST /api/user/{id}/valider (admin)"         200 "$(http POST "/api/user/$TEST_USER_ID/valider" "$ADMIN_TOKEN")"

# ====================================================================
info "7. Proprietes (catalogue public)"
# ====================================================================
assert_code "GET /api/proprietes/public"            200 "$(http GET /api/proprietes/public "$TEST_TOKEN")"
FIRST_PROP_ID=$(jget_num /tmp/fursa_body.json id)
if [[ -n "$FIRST_PROP_ID" ]]; then
    ok "Premiere propriete id=$FIRST_PROP_ID"
    assert_code "GET /api/proprietes/public/{id}"            200 "$(http GET "/api/proprietes/public/$FIRST_PROP_ID" "$TEST_TOKEN")"
    assert_code "GET /api/proprietes/public/{id}/progression" 200 "$(http GET "/api/proprietes/public/$FIRST_PROP_ID/progression" "$TEST_TOKEN")"
else
    ko "Pas de propriete dans le catalogue - tests marche primaire seront skippes"
fi
assert_code "GET /api/proprietes/public/99999 (inconnue)" 404 "$(http GET "/api/proprietes/public/99999" "$TEST_TOKEN")"
# 415 attendu : l'endpoint exige multipart/form-data, Spring rejette avant @PreAuthorize.
# Un client legitimate qui envoie du multipart recevrait 403.
code=$(http POST "/api/proprietes/admin" "$TEST_TOKEN")
if [[ "$code" == "403" || "$code" == "415" ]]; then ok "POST /api/proprietes/admin (non-admin, sans multipart) -> $code"; else ko "POST /api/proprietes/admin (non-admin) - attendu 403/415, recu $code"; fi

# ====================================================================
info "8. Marche primaire - achat"
# ====================================================================
if [[ -n "$FIRST_PROP_ID" ]]; then
    assert_code "POST /acheter (investisseur)" 200 \
        "$(http POST /api/marche-primaire/acheter "$TEST_TOKEN" "{\"proprieteId\":$FIRST_PROP_ID,\"nombreParts\":2}")"
    assert_code "POST /acheter 0 parts (400)" 400 \
        "$(http POST /api/marche-primaire/acheter "$TEST_TOKEN" "{\"proprieteId\":$FIRST_PROP_ID,\"nombreParts\":0}")"
    assert_code "POST /acheter trop de parts (400)" 400 \
        "$(http POST /api/marche-primaire/acheter "$TEST_TOKEN" "{\"proprieteId\":$FIRST_PROP_ID,\"nombreParts\":999999}")"
fi
assert_code "GET /me/possessions"   200 "$(http GET /api/marche-primaire/me/possessions "$TEST_TOKEN")"
assert_code "GET /me/transactions"  200 "$(http GET /api/marche-primaire/me/transactions "$TEST_TOKEN")"
assert_code "GET /me/paiements"     200 "$(http GET /api/marche-primaire/me/paiements "$TEST_TOKEN")"
assert_code "GET /possessions (non-admin)" 403 "$(http GET /api/marche-primaire/possessions "$TEST_TOKEN")"
assert_code "GET /possessions (admin)"     200 "$(http GET /api/marche-primaire/possessions "$ADMIN_TOKEN")"

# ====================================================================
info "9. Marche secondaire - annonces"
# ====================================================================
if [[ -n "$FIRST_PROP_ID" ]]; then
    code=$(http POST /api/annonces "$TEST_TOKEN" "{\"proprieteId\":$FIRST_PROP_ID,\"nombreDePartsAVendre\":1,\"prixUnitaireDemande\":150.00}")
    assert_code "POST /api/annonces (creer)" 201 "$code"
    ANNONCE_ID=$(jget_num /tmp/fursa_body.json id)

    assert_code "GET /api/annonces (paginee)"           200 "$(http GET "/api/annonces?page=0&size=10" "$TEST_TOKEN")"
    assert_code "GET /api/annonces/me"                  200 "$(http GET /api/annonces/me "$TEST_TOKEN")"
    if [[ -n "$ANNONCE_ID" ]]; then
        assert_code "GET /api/annonces/{id}"                  200 "$(http GET "/api/annonces/$ANNONCE_ID" "$TEST_TOKEN")"
        assert_code "PUT /api/annonces/{id} (modifier)"       200 \
            "$(http PUT "/api/annonces/$ANNONCE_ID" "$TEST_TOKEN" '{"nombreDePartsAVendre":1,"prixUnitaireDemande":160.00}')"
        assert_code "POST /marche-secondaire/annonces/{id}/acheter (soi-meme)" 400 \
            "$(http POST "/api/marche-secondaire/annonces/$ANNONCE_ID/acheter" "$TEST_TOKEN" '{"nombreDeParts":1}')"
        assert_code "DELETE /api/annonces/{id} (vendeur)"     200 "$(http DELETE "/api/annonces/$ANNONCE_ID" "$TEST_TOKEN")"
    fi
fi

# ====================================================================
info "10. Notifications"
# ====================================================================
assert_code "GET /api/notifications/me"                     200 "$(http GET /api/notifications/me "$TEST_TOKEN")"
assert_code "GET /api/notifications/me?nonLuesSeulement=true" 200 "$(http GET "/api/notifications/me?nonLuesSeulement=true" "$TEST_TOKEN")"
assert_code "PUT /api/notifications/me/lu-tout"             200 "$(http PUT /api/notifications/me/lu-tout "$TEST_TOKEN")"
assert_code "GET /notifications/investisseur/{id} (non-admin)" 403 "$(http GET "/api/notifications/investisseur/1" "$TEST_TOKEN")"
assert_code "GET /notifications/investisseur/{id} (admin)"     200 "$(http GET "/api/notifications/investisseur/1" "$ADMIN_TOKEN")"

# ====================================================================
info "11. Revenus (admin only pour POST)"
# ====================================================================
assert_code "POST /api/revenus (non-admin)" 403 "$(http POST /api/revenus "$TEST_TOKEN" "{\"proprieteId\":$FIRST_PROP_ID,\"montantTotal\":1000.00}")"
if [[ -n "$FIRST_PROP_ID" ]]; then
    code=$(http POST /api/revenus "$ADMIN_TOKEN" "{\"proprieteId\":$FIRST_PROP_ID,\"montantTotal\":1000.00}")
    assert_code "POST /api/revenus (admin)" 201 "$code"
    REV_ID=$(jget_num /tmp/fursa_body.json id)
fi
assert_code "GET /api/revenus" 200 "$(http GET /api/revenus "$TEST_TOKEN")"

# ====================================================================
info "12. Distribution & Dividendes"
# ====================================================================
if [[ -n "${REV_ID:-}" ]]; then
    assert_code "POST /api/distribution/{id} (non-admin)" 403 "$(http POST "/api/distribution/$REV_ID" "$TEST_TOKEN")"
    assert_code "POST /api/distribution/{id} (admin)"     200 "$(http POST "/api/distribution/$REV_ID" "$ADMIN_TOKEN")"
fi
assert_code "POST /api/distribution/99999 (admin, revenu inconnu)" 404 "$(http POST "/api/distribution/99999" "$ADMIN_TOKEN")"
assert_code "GET /api/dividendes/me"              200 "$(http GET /api/dividendes/me "$TEST_TOKEN")"
assert_code "GET /api/dividendes (non-admin)"     403 "$(http GET /api/dividendes "$TEST_TOKEN")"
assert_code "GET /api/dividendes (admin)"         200 "$(http GET /api/dividendes "$ADMIN_TOKEN")"

# ====================================================================
info "13. Dashboard"
# ====================================================================
assert_code "GET /api/dashboard/me"                            200 "$(http GET /api/dashboard/me "$TEST_TOKEN")"
assert_code "GET /api/dashboard/investisseur/{id} (non-admin)" 403 "$(http GET "/api/dashboard/investisseur/1" "$TEST_TOKEN")"
assert_code "GET /api/dashboard/investisseur/{id} (admin)"     200 "$(http GET "/api/dashboard/investisseur/1" "$ADMIN_TOKEN")"
assert_code "GET /api/dashboard/admin (non-admin)"             403 "$(http GET /api/dashboard/admin "$TEST_TOKEN")"
assert_code "GET /api/dashboard/admin (admin)"                 200 "$(http GET /api/dashboard/admin "$ADMIN_TOKEN")"

# ====================================================================
info "14. Rate limit login"
# ====================================================================
rate_fail=0
for i in 1 2 3 4 5 6; do
    code=$(http POST /api/user/auth/login "" '{"email":"ratelimit@fursa.test","password":"wrong"}')
    if [[ "$i" -le 5 && "$code" == "401" ]]; then rate_fail=$((rate_fail+0))
    elif [[ "$i" == "6" && "$code" == "429" ]]; then rate_fail=$((rate_fail+0))
    else rate_fail=$((rate_fail+1)); fi
done
[[ $rate_fail -eq 0 ]] && ok "Rate limit login (5 autorises, 6e = 429)" || ko "Rate limit defaillant ($rate_fail anomalies)"

# ====================================================================
info "15. Cleanup du compte de test"
# ====================================================================
code=$(http DELETE "/api/user/delete/$TEST_USER_ID" "$TEST_TOKEN")
# 409 si l'user a des dependances (possessions, paiements, transactions) - c'est le cas
# apres ce smoke test qui effectue un achat. 200/204 si le user n'a rien.
if [[ "$code" == "200" || "$code" == "204" ]]; then
    ok "Delete self -> $code (user sans dependances)"
elif [[ "$code" == "409" ]]; then
    ok "Delete self -> 409 (user avec dependances, comportement attendu)"
else
    ko "Delete self - HTTP $code (attendu 200/204/409)"
fi

# Cleanup du compte injection via admin
INJ_ID=$(curl -sS -H "Authorization: Bearer $ADMIN_TOKEN" "$BASE/api/user" | \
         grep -oE "\"id\":[0-9]+,\"nom\":\"Inj\"" | head -1 | grep -oE "[0-9]+" | head -1)
if [[ -n "$INJ_ID" ]]; then
    http DELETE "/api/user/delete/$INJ_ID" "$ADMIN_TOKEN" > /dev/null
    ok "Delete compte injection (via admin)"
fi

# ====================================================================
# RESUMEE
# ====================================================================
TOTAL=$((PASS+FAIL))
printf "\n%s\n" "$(color '1;35' '──────────────────────────────────────────────────────')"
printf "Total : %s / %s tests\n" "$PASS" "$TOTAL"
printf "  %s : %s\n" "$(color '32' 'Succes')" "$PASS"
printf "  %s : %s\n" "$(color '31' 'Echec ')" "$FAIL"
if [[ $FAIL -gt 0 ]]; then
    printf "\n%s\n" "$(color '31' 'Echecs:')"
    for t in "${FAILED_TESTS[@]}"; do printf "  - %s\n" "$t"; done
fi
printf "%s\n\n" "$(color '1;35' '──────────────────────────────────────────────────────')"

exit $([[ $FAIL -eq 0 ]] && echo 0 || echo 1)
