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

    private final Path root = Paths.get("uploads");

    public FileStorageService() {
        try {
            if (!Files.exists(root)) Files.createDirectories(root);
        } catch (IOException e) {
            throw new RuntimeException("Impossible de créer le dossier de stockage");
        }
    }

    // ── Sauvegarde 
    public String save(MultipartFile file) {
        try {
            // UUID + extension propre → jamais de doublon, jamais de caractère bizarre
            String extension = getExtension(file.getOriginalFilename());
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