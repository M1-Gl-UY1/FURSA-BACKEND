package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fichiers")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    @GetMapping("/{fileName:.+}")
    public ResponseEntity<Resource> servir(@PathVariable String fileName) {

        Resource resource = fileStorageService.load(fileName);

        // Détecte automatiquement le type (pdf, image, etc.)
        String contentType = "application/octet-stream";
        String name = resource.getFilename();
        if (name != null) {
            if (name.endsWith(".pdf"))  contentType = "application/pdf";
            else if (name.endsWith(".png"))  contentType = "image/png";
            else if (name.endsWith(".jpg") || name.endsWith(".jpeg")) contentType = "image/jpeg";
            else if (name.endsWith(".webp")) contentType = "image/webp";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }
}