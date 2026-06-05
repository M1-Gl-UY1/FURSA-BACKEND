package com.fursa.fursa_backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final java.util.Set<String> ALLOWED_EXTENSIONS =
            java.util.Set.of(".pdf", ".jpg", ".jpeg", ".png", ".webp",
                    // P1 (Hugh 22/05/2026) : video de visite guidee
                    ".mp4", ".mov", ".webm");
    private static final java.util.Set<String> ALLOWED_CONTENT_TYPES = java.util.Set.of(
            "application/pdf", "image/jpeg", "image/png", "image/webp",
            "video/mp4", "video/quicktime", "video/webm");

    // V2 G.5 (05/06/2026) : valeurs par defaut hardcodees, utilisees en
    // fallback si la cle n'existe pas en BDD (migration 029 pas encore
    // appliquee ou setting supprime). Les vraies limites sont lues dans
    // app_setting via AppSettingsService.
    private static final long MAX_VIDEO_SIZE_DEFAULT_MO = 100L;
    private static final long MAX_IMAGE_SIZE_DEFAULT_MO = 4L;
    private static final long MAX_PDF_SIZE_DEFAULT_MO   = 10L;

    /**
     * V2 G.5 : lecture dynamique des limites via le service de settings.
     * {@code @Lazy} pour eviter une dependance circulaire au demarrage du
     * contexte Spring (AppSettingsService depend de JPA qui est initialise
     * apres FileStorageService).
     */
    private final AppSettingsService appSettingsService;

    private final Path root = Paths.get("uploads");

    @Autowired
    public FileStorageService(@Lazy AppSettingsService appSettingsService) {
        this.appSettingsService = appSettingsService;
        try {
            if (!Files.exists(root)) Files.createDirectories(root);
        } catch (IOException e) {
            throw new RuntimeException("Impossible de créer le dossier de stockage");
        }
    }

    private long maxPdfBytes() {
        return appSettingsService.getLong("file.max_size_pdf_mo", MAX_PDF_SIZE_DEFAULT_MO) * 1024L * 1024L;
    }

    private long maxImageBytes() {
        return appSettingsService.getLong("file.max_size_image_mo", MAX_IMAGE_SIZE_DEFAULT_MO) * 1024L * 1024L;
    }

    private long maxVideoBytes() {
        return appSettingsService.getLong("file.max_size_video_mo", MAX_VIDEO_SIZE_DEFAULT_MO) * 1024L * 1024L;
    }

    public String save(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Fichier vide");
        }
        String extension = getExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "Extension non autorisee (autorises : " + ALLOWED_EXTENSIONS + ")");
        }
        // 03/06/2026 : fallback sur l'extension si le browser n'envoie pas un
        // content-type connu (ex Safari iOS ne devine pas video/mp4 pour les .mp4
        // partages depuis WhatsApp -> envoie application/octet-stream et donc rejet).
        // Vu que l'extension est deja whitelisted dans ALLOWED_EXTENSIONS, c'est sur.
        String contentType = file.getContentType();
        boolean mimeUnknown = contentType == null
                || contentType.isBlank()
                || contentType.equalsIgnoreCase("application/octet-stream")
                || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase());
        if (mimeUnknown) {
            String inferred = inferMimeFromExtension(extension);
            if (inferred == null) {
                throw new IllegalArgumentException(
                        "Type MIME non autorise (autorises : " + ALLOWED_CONTENT_TYPES + ")");
            }
            contentType = inferred;
        }

        // Limites de taille DIFFERENCIEES par type (Hugh 26/05/2026) :
        //   - Images : 4 Mo max (rejet si > -> message explicite)
        //   - Videos : 100 Mo max
        //   - PDFs (docs legaux) : 10 Mo max
        final long size = file.getSize();
        final long mb = 1024L * 1024L;
        final String ct = contentType.toLowerCase();
        // V2 G.5 : limites dynamiques via AppSettingsService (cache local).
        if (ct.startsWith("image/")) {
            long limit = maxImageBytes();
            if (size > limit) {
                throw new IllegalArgumentException(
                        "Photo trop lourde (" + (size / mb) + " Mo) : taille max autorisee = "
                                + (limit / mb) + " Mo. "
                                + "Compressez l'image (TinyPNG, Squoosh) avant l'upload.");
            }
        } else if (ct.startsWith("video/")) {
            long limit = maxVideoBytes();
            if (size > limit) {
                throw new IllegalArgumentException(
                        "Video trop lourde (" + (size / mb) + " Mo) : taille max autorisee = "
                                + (limit / mb) + " Mo. "
                                + "Compressez la video (HandBrake, MP4 720p) avant l'upload.");
            }
        } else { // application/pdf
            long limit = maxPdfBytes();
            if (size > limit) {
                throw new IllegalArgumentException(
                        "Document trop lourd (" + (size / mb) + " Mo) : taille max autorisee = "
                                + (limit / mb) + " Mo.");
            }
        }
        try {
            String fileName = UUID.randomUUID() + extension;
            Files.copy(file.getInputStream(), root.resolve(fileName),
                    StandardCopyOption.REPLACE_EXISTING);
            return fileName;
        } catch (IOException e) {
            throw new RuntimeException("Erreur lors du stockage : " + e.getMessage());
        }
    }

    // ── Lecture (pour servir le fichier au client) 
    public Resource load(String fileName) {
        try {
            Path file = root.resolve(fileName).normalize();
            Resource resource = new UrlResource(file.toUri());

            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
            throw new jakarta.persistence.EntityNotFoundException("Fichier introuvable : " + fileName);

        } catch (MalformedURLException e) {
            throw new RuntimeException("Erreur de chemin : " + e.getMessage());
        }
    }

    // ── Suppression 
    public void delete(String fileName) {
        try {
            Path file = root.resolve(fileName).normalize();
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new RuntimeException("Erreur lors de la suppression : " + e.getMessage());
        }
    }

    // ── Utilitaires prives ──
    private String getExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) return "";
        return originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
    }

    /**
     * Map une extension whitelisted vers son content-type canonique.
     * Renvoie null si l'extension n'est pas supportee (ne devrait pas arriver
     * apres le check ALLOWED_EXTENSIONS, mais defensif).
     */
    private String inferMimeFromExtension(String extension) {
        if (extension == null) return null;
        switch (extension.toLowerCase()) {
            case ".pdf":  return "application/pdf";
            case ".jpg":
            case ".jpeg": return "image/jpeg";
            case ".png":  return "image/png";
            case ".webp": return "image/webp";
            case ".mp4":  return "video/mp4";
            case ".mov":  return "video/quicktime";
            case ".webm": return "video/webm";
            default:      return null;
        }
    }
}