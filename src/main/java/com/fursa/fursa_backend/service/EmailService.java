package com.fursa.fursa_backend.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/**
 * V2 G.6 (05/06/2026) : envoi des emails transactionnels via Postal (SMTP).
 *
 * <p>Postal est deja installe sur le VPS FURSA. Les credentials passent via
 * variables d'environnement (cf {@code application.yaml} spring.mail.*).
 *
 * <p>Si {@code app.mail.enabled=false} ou {@code spring.mail.host} est vide,
 * le service tombe en "log-only" : les emails sont logges au lieu d'etre
 * envoyes (utile pour les tests d'integration ou un environnement sans Postal).
 *
 * <p>Tous les envois sont {@code @Async} : ils ne bloquent pas le thread de
 * la requete utilisateur. Si l'envoi echoue, l'exception est loggee mais
 * n'interrompt PAS le flow metier (la notification in-app a deja ete creee).
 */
@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String fromAddress;
    private final String fromName;
    private final String frontBaseUrl;
    private final String smtpHost;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${app.mail.enabled:true}") boolean enabled,
            @Value("${app.mail.from:noreply@fursa.seed-innov.com}") String fromAddress,
            @Value("${app.mail.from-name:FURSA}") String fromName,
            @Value("${app.mail.front-base-url:https://fursa.seed-innov.com}") String frontBaseUrl,
            @Value("${spring.mail.host:}") String smtpHost) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
        this.frontBaseUrl = frontBaseUrl;
        this.smtpHost = smtpHost;
    }

    // ========================================================================
    // Bas niveau : envoi brut
    // ========================================================================

    /**
     * Envoi d'un email HTML simple (utilise par les helpers metier).
     * Asynchrone : ne bloque pas le thread appelant.
     */
    @Async
    public void envoyer(String to, String sujet, String corpsHtml) {
        if (!enabled || smtpHost == null || smtpHost.isBlank()) {
            log.info("[Email LOG-ONLY] to={} sujet={} (mail.enabled={}, smtp.host='{}')",
                    to, sujet, enabled, smtpHost);
            return;
        }
        if (to == null || to.isBlank()) {
            log.warn("Tentative d'envoi a un destinataire vide, abandonne. Sujet={}", sujet);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(fromAddress, fromName, StandardCharsets.UTF_8.name()));
            helper.setTo(to);
            helper.setSubject(sujet);
            helper.setText(corpsHtml, true);
            mailSender.send(message);
            log.info("[Email] Envoye to={} sujet={}", to, sujet);
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("[Email] Echec envoi to={} sujet={} : {}", to, sujet, e.getMessage(), e);
        } catch (Exception e) {
            // Postal peut etre temporairement down (timeout SMTP). On ne fait pas
            // echouer le flow metier : la notif in-app reste creee.
            log.error("[Email] Erreur SMTP (probablement Postal indispo) to={} : {}",
                    to, e.getMessage());
        }
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
