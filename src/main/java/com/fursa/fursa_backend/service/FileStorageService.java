package com.fursa.fursa_backend.service;

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
            java.util.Set.of(".pdf", ".jpg", ".jpeg", ".png", ".webp");
    private static final java.util.Set<String> ALLOWED_CONTENT_TYPES = java.util.Set.of(
            "application/pdf", "image/jpeg", "image/png", "image/webp");
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024; // 10 MB

    private final Path root = Paths.get("uploads");

    public FileStorageService() {
        try {
            if (!Files.exists(root)) Files.createDirectories(root);
        } catch (IOException e) {
            throw new RuntimeException("Impossible de créer le dossier de stockage");
        }
    }

    public String save(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Fichier vide");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("Fichier trop volumineux (max 10 MB)");
        }
        String extension = getExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "Extension non autorisee (autorises : " + ALLOWED_EXTENSIONS + ")");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException(
                    "Type MIME non autorise (autorises : " + ALLOWED_CONTENT_TYPES + ")");
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
            throw new RuntimeException("Fichier introuvable : " + fileName);

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

    // ── Utilitaire privé 
    private String getExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) return "";
        return originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
    }
}