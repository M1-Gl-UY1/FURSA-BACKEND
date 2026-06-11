package com.fursa.fursa_backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * V2 II (12/06/2026) : envoi des emails transactionnels via l'API HTTP Postal
 * (auparavant V2 G.6 utilisait SMTP, refactore pour passer en API REST).
 *
 * <p>Postal expose une API REST sur le VPS. La config se fait via 4 variables
 * d'environnement :
 * <ul>
 *   <li>{@code POSTAL_HOST} : ex. {@code 84.247.183.206:5080}</li>
 *   <li>{@code POSTAL_API_KEY} : credential du server FURSA (cree dans l'admin)</li>
 *   <li>{@code FROM_EMAIL} : ex. {@code noreply@fursa.seed-innov.com}</li>
 *   <li>{@code FROM_NAME} : ex. {@code FURSA}</li>
 * </ul>
 *
 * <p>Si {@code POSTAL_HOST} ou {@code POSTAL_API_KEY} est vide, le service
 * tombe en "log-only" : les emails sont logges au lieu d'etre envoyes (utile
 * pour les tests d'integration ou un environnement sans Postal).
 *
 * <p>Tous les envois sont {@code @Async} : ils ne bloquent pas le thread de
 * la requete utilisateur. Si l'envoi echoue, l'exception est loggee mais
 * n'interrompt PAS le flow metier (la notification in-app a deja ete creee).
 */
@Slf4j
@Service
public class EmailService {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final boolean enabled;
    private final String fromAddress;
    private final String fromName;
    private final String frontBaseUrl;
    private final String postalHost;
    private final String postalApiKey;

    public EmailService(
            @Value("${app.mail.enabled:true}") boolean enabled,
            @Value("${app.mail.from:noreply@fursa.seed-innov.com}") String fromAddress,
            @Value("${app.mail.from-name:FURSA}") String fromName,
            @Value("${app.mail.front-base-url:https://fursa.seed-innov.com}") String frontBaseUrl,
            @Value("${POSTAL_HOST:}") String postalHost,
            @Value("${POSTAL_API_KEY:}") String postalApiKey) {
        this.enabled = enabled;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
        this.frontBaseUrl = frontBaseUrl;
        this.postalHost = postalHost == null ? "" : postalHost.trim();
        this.postalApiKey = postalApiKey == null ? "" : postalApiKey.trim();
    }

    // ========================================================================
    // Bas niveau : envoi brut via API HTTP Postal
    // ========================================================================

    /**
     * Envoi d'un email HTML simple (utilise par les helpers metier).
     * Asynchrone : ne bloque pas le thread appelant.
     *
     * <p>Format API Postal : POST http://{POSTAL_HOST}/api/v1/send/message
     * avec header {@code X-Server-API-Key} et body JSON
     * {@code {"to":[...], "from":"...", "subject":"...", "html_body":"..."}}.
     */
    @Async
    public void envoyer(String to, String sujet, String corpsHtml) {
        if (!enabled || postalHost.isBlank() || postalApiKey.isBlank()) {
            log.info("[Email LOG-ONLY] to={} sujet={} (enabled={}, postal.host='{}', api-key={})",
                    to, sujet, enabled, postalHost,
                    postalApiKey.isBlank() ? "absent" : "present");
            return;
        }
        if (to == null || to.isBlank()) {
            log.warn("Tentative d'envoi a un destinataire vide, abandonne. Sujet={}", sujet);
            return;
        }
        try {
            String fromFull = fromName.isBlank()
                    ? fromAddress
                    : fromName + " <" + fromAddress + ">";
            String body = "{"
                    + "\"to\":[" + jsonString(to) + "],"
                    + "\"from\":" + jsonString(fromFull) + ","
                    + "\"subject\":" + jsonString(sujet) + ","
                    + "\"html_body\":" + jsonString(corpsHtml)
                    + "}";

            // Postal supporte http (interne au VPS) ou https. On force http si
            // le user a juste donne l'IP:port (cas par defaut sur le VPS FURSA).
            String scheme = postalHost.startsWith("http://") || postalHost.startsWith("https://")
                    ? "" : "http://";
            URI uri = URI.create(scheme + postalHost + "/api/v1/send/message");

            HttpRequest req = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("X-Server-API-Key", postalApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            int code = resp.statusCode();
            if (code >= 200 && code < 300 && resp.body().contains("\"status\":\"success\"")) {
                log.info("[Email] Envoye to={} sujet={} (postal code={})", to, sujet, code);
            } else {
                log.error("[Email] Echec Postal HTTP {} to={} sujet={} body={}",
                        code, to, sujet, resp.body());
            }
        } catch (Exception e) {
            // Postal peut etre temporairement down (timeout). On ne fait pas
            // echouer le flow metier : la notif in-app reste creee.
            log.error("[Email] Erreur appel API Postal to={} sujet={} : {}",
                    to, sujet, e.getMessage());
        }
    }

    /**
     * Echappe une chaine pour insertion dans un JSON (encadre par des "double quotes").
     * Gere les caracteres speciaux de base : guillemets, backslash, retours ligne, tabs, control chars.
     */
    private static String jsonString(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    // ========================================================================
    // Templates metier (HTML inline simple, Thymeleaf plus tard si necessaire)
    // ========================================================================

    public void envoyerKycValide(String email, String prenom) {
        String html = layout("Votre identite est vérifiée",
                "<p>Bonjour " + escape(prenom) + ",</p>"
                + "<p>Bonne nouvelle : votre dossier de verification d'identite (KYC) "
                + "a ete <strong>approuve</strong>. Vous pouvez desormais investir sur "
                + "FURSA, retirer des fonds et proposer des biens.</p>"
                + "<p><a href=\"" + frontBaseUrl + "/dashboard\" "
                + "style=\"display:inline-block;padding:12px 24px;background:#c0633b;"
                + "color:#fff;text-decoration:none;border-radius:6px;font-weight:600\">"
                + "Acceder a mon tableau de bord</a></p>");
        envoyer(email, "[FURSA] Votre identite est verifiee", html);
    }

    public void envoyerKycRevoque(String email, String prenom, String motif) {
        String html = layout("Votre verification d'identite a ete revoquee",
                "<p>Bonjour " + escape(prenom) + ",</p>"
                + "<p>Apres examen, votre verification d'identite a ete <strong>"
                + "revoquee</strong>. Votre compte n'est plus marque comme verifie : "
                + "vous ne pouvez plus investir ni proposer de bien tant que vous "
                + "n'aurez pas resoumis et fait revalider votre dossier.</p>"
                + (motif != null && !motif.isBlank()
                    ? "<p><strong>Motif :</strong> " + escape(motif) + "</p>" : "")
                + "<p><a href=\"" + frontBaseUrl + "/kyc\" "
                + "style=\"display:inline-block;padding:12px 24px;background:#c0633b;"
                + "color:#fff;text-decoration:none;border-radius:6px;font-weight:600\">"
                + "Resoumettre mon dossier</a></p>");
        envoyer(email, "[FURSA] Verification d'identite revoquee", html);
    }

    public void envoyerKycRefuse(String email, String prenom, String motif) {
        String html = layout("Votre verification d'identite a ete refusee",
                "<p>Bonjour " + escape(prenom) + ",</p>"
                + "<p>Apres examen, votre dossier de verification d'identite n'a pas "
                + "pu etre approuve.</p>"
                + (motif != null && !motif.isBlank()
                    ? "<p><strong>Motif :</strong> " + escape(motif) + "</p>" : "")
                + "<p>Vous pouvez resoumettre un dossier corrige depuis votre espace.</p>"
                + "<p><a href=\"" + frontBaseUrl + "/kyc\" "
                + "style=\"display:inline-block;padding:12px 24px;background:#c0633b;"
                + "color:#fff;text-decoration:none;border-radius:6px;font-weight:600\">"
                + "Resoumettre mon dossier</a></p>");
        envoyer(email, "[FURSA] Verification d'identite refusee", html);
    }

    public void envoyerProprieteAcceptee(String email, String prenom, String proprieteNom) {
        String html = layout("Votre bien a ete accepte",
                "<p>Bonjour " + escape(prenom) + ",</p>"
                + "<p>Votre proposition de bien <strong>" + escape(proprieteNom)
                + "</strong> a ete validee par notre equipe. Il va etre tokenise et "
                + "publie sur la marketplace tres prochainement.</p>"
                + "<p><a href=\"" + frontBaseUrl + "/mes-proprietes\" "
                + "style=\"display:inline-block;padding:12px 24px;background:#c0633b;"
                + "color:#fff;text-decoration:none;border-radius:6px;font-weight:600\">"
                + "Voir mes proprietes</a></p>");
        envoyer(email, "[FURSA] Votre bien \"" + proprieteNom + "\" est accepte", html);
    }

    public void envoyerProprieteRefusee(String email, String prenom, String proprieteNom, String motif) {
        String html = layout("Votre bien n'a pas pu etre accepte",
                "<p>Bonjour " + escape(prenom) + ",</p>"
                + "<p>Apres examen, votre proposition de bien <strong>"
                + escape(proprieteNom) + "</strong> n'a pas ete acceptee.</p>"
                + (motif != null && !motif.isBlank()
                    ? "<p><strong>Motif :</strong> " + escape(motif) + "</p>" : "")
                + "<p>N'hesitez pas a corriger les points releves et a resoumettre.</p>"
                + "<p><a href=\"" + frontBaseUrl + "/mes-proprietes\" "
                + "style=\"display:inline-block;padding:12px 24px;background:#c0633b;"
                + "color:#fff;text-decoration:none;border-radius:6px;font-weight:600\">"
                + "Voir mes proprietes</a></p>");
        envoyer(email, "[FURSA] Votre bien \"" + proprieteNom + "\" n'a pas ete accepte", html);
    }

    public void envoyerDividendeDistribue(String email, String prenom,
                                           String proprieteNom, BigDecimal montant) {
        String html = layout("Vous avez recu un dividende",
                "<p>Bonjour " + escape(prenom) + ",</p>"
                + "<p>Un dividende vient d'etre credit sur votre wallet :</p>"
                + "<ul>"
                + "<li>Bien : <strong>" + escape(proprieteNom) + "</strong></li>"
                + "<li>Montant : <strong>" + montant.toPlainString() + " USD</strong></li>"
                + "</ul>"
                + "<p><a href=\"" + frontBaseUrl + "/dividendes\" "
                + "style=\"display:inline-block;padding:12px 24px;background:#c0633b;"
                + "color:#fff;text-decoration:none;border-radius:6px;font-weight:600\">"
                + "Voir mes dividendes</a></p>");
        envoyer(email, "[FURSA] Nouveau dividende recu", html);
    }

    public void envoyerRetraitValide(String email, String prenom, BigDecimal montant) {
        String html = layout("Votre retrait est valide",
                "<p>Bonjour " + escape(prenom) + ",</p>"
                + "<p>Votre demande de retrait d'un montant de <strong>"
                + montant.toPlainString() + " USD</strong> a ete validee et est en "
                + "cours d'execution.</p>"
                + "<p>Vous recevrez une seconde notification une fois les fonds "
                + "credit sur votre moyen de paiement.</p>");
        envoyer(email, "[FURSA] Retrait valide", html);
    }

    public void envoyerPaiementRecu(String email, String prenom,
                                     String proprieteNom, int nombreParts, BigDecimal montant) {
        String html = layout("Confirmation d'achat",
                "<p>Bonjour " + escape(prenom) + ",</p>"
                + "<p>Votre achat de parts est confirme :</p>"
                + "<ul>"
                + "<li>Bien : <strong>" + escape(proprieteNom) + "</strong></li>"
                + "<li>Parts : <strong>" + nombreParts + "</strong></li>"
                + "<li>Montant : <strong>" + montant.toPlainString() + " USD</strong></li>"
                + "</ul>"
                + "<p><a href=\"" + frontBaseUrl + "/portefeuille\" "
                + "style=\"display:inline-block;padding:12px 24px;background:#c0633b;"
                + "color:#fff;text-decoration:none;border-radius:6px;font-weight:600\">"
                + "Voir mon portefeuille</a></p>");
        envoyer(email, "[FURSA] Confirmation d'achat - " + proprieteNom, html);
    }

    // ========================================================================
    // Layout HTML commun
    // ========================================================================

    private String layout(String titre, String contenuHtml) {
        return "<!DOCTYPE html>"
            + "<html><head><meta charset=\"UTF-8\"><title>" + escape(titre) + "</title></head>"
            + "<body style=\"font-family:Arial,sans-serif;background:#f5efe6;margin:0;padding:24px\">"
            + "<table style=\"max-width:600px;margin:0 auto;background:#fff;border-radius:12px;"
            + "padding:32px;color:#2a1e10;line-height:1.5\"><tr><td>"
            + "<div style=\"font-size:24px;font-weight:700;color:#c0633b;margin-bottom:8px\">FURSA</div>"
            + "<div style=\"font-size:11px;color:#888;text-transform:uppercase;letter-spacing:1px;"
            + "margin-bottom:24px\">Investir dans l'immobilier fractionne</div>"
            + "<h1 style=\"font-size:20px;color:#2a1e10;margin:0 0 16px\">" + escape(titre) + "</h1>"
            + contenuHtml
            + "<hr style=\"border:none;border-top:1px solid #e0d6c6;margin:32px 0 16px\">"
            + "<p style=\"font-size:12px;color:#888;margin:0\">"
            + "Vous recevez cet email parce que vous etes inscrit sur FURSA. "
            + "Pour toute question, repondez directement a cet email.</p>"
            + "</td></tr></table>"
            + "</body></html>";
    }

    /** Echappement HTML basique pour eviter les injections via les donnees user. */
    private String escape(String s) {
        if (s == null) return "";
        return s
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
