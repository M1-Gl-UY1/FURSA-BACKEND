package com.fursa.fursa_backend.controller;


import com.fursa.fursa_backend.config.JwtUtils;
import com.fursa.fursa_backend.dto.AuthResponse;
import com.fursa.fursa_backend.dto.LoginRequest;
import com.fursa.fursa_backend.dto.RegisterRequest;
import com.fursa.fursa_backend.dto.RegisterResponse;
import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.model.User;
import com.fursa.fursa_backend.model.enumeration.Role;
import com.fursa.fursa_backend.repository.UserRepository;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentification & Utilisateurs", description = "Inscription, connexion JWT et gestion des utilisateurs")
public class UserController {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final AuthenticationManager authenticationManager;
    private final com.fursa.fursa_backend.service.AuthenticatedInvestisseurService authInvestisseur;

    @Operation(summary = "Profil de l'utilisateur courant", description = "Retourne le profil de l'investisseur authentifie.")
    @GetMapping("/me")
    public ResponseEntity<RegisterResponse> me() {
        return ResponseEntity.ok(new RegisterResponse(authInvestisseur.current()));
    }

    @Operation(summary = "Lister les utilisateurs (admin)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<java.util.List<RegisterResponse>> listerTous() {
        return ResponseEntity.ok(userRepository.findAll().stream()
                .filter(u -> u instanceof Investisseur)
                .map(u -> new RegisterResponse((Investisseur) u))
                .toList());
    }

    @Operation(
            summary = "Valider un compte investisseur (admin)",
            description = "Passe `isVerified = true` sur l'investisseur cible.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Compte valide"),
            @ApiResponse(responseCode = "404", description = "Utilisateur inconnu")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/valider")
    public ResponseEntity<RegisterResponse> valider(@PathVariable Long id) {
        User u = userRepository.findById(id).orElse(null);
        if (u == null || !(u instanceof Investisseur inv)) {
            return ResponseEntity.notFound().build();
        }
        inv.setIsVerified(true);
        userRepository.save(inv);
        return ResponseEntity.ok(new RegisterResponse(inv));
    }

    @Operation(
            summary = "Creer un compte investisseur",
            description = """
                    Inscription publique. Force role=INVESTISSEUR et isVerified=false cote serveur
                    pour empecher toute injection de privilege via le body. Le mot de passe doit
                    faire au moins 8 caracteres avec au moins une lettre et un chiffre.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Compte cree"),
            @ApiResponse(responseCode = "400", description = "Validation echouee ou email deja utilise")
    })
    @SecurityRequirements
    @PostMapping("/auth/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        if (userRepository.findByEmail(req.email()).isPresent()) {
            return ResponseEntity.badRequest()
                    .body(java.util.Map.of("message", "Email deja utilise"));
        }

        Investisseur inv = new Investisseur();
        inv.setEmail(req.email());
        inv.setPassword(passwordEncoder.encode(req.password()));
        inv.setNom(req.nom());
        inv.setPrenom(req.prenom());
        inv.setTelephone(req.telephone());
        inv.setWallet_address(req.walletAddress());
        // Champs sensibles : forces cote serveur, jamais depuis le client.
        inv.setRole(Role.INVESTISSEUR);
        inv.setIsVerified(false);

        userRepository.save(inv);
        return ResponseEntity.status(HttpStatus.CREATED).body(new RegisterResponse(inv));
    }

    @Operation(
            summary = "Se connecter et obtenir un JWT",
            description = "Retourne un token Bearer a utiliser dans l'entete Authorization pour tous les autres endpoints.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token emis"),
            @ApiResponse(responseCode = "401", description = "Identifiants invalides")
    })
    @SecurityRequirements
    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.email(), req.password()));
            if (authentication.isAuthenticated()) {
                AuthResponse authData = new AuthResponse();
                authData.setToken(jwtUtils.generateToken(req.email()));
                authData.setType("Bearer");
                return ResponseEntity.ok(authData);
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(java.util.Map.of("message", "Identifiants invalides"));
        } catch (AuthenticationException e) {
            log.warn("Echec d'authentification pour {}", req.email());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(java.util.Map.of("message", "Identifiants invalides"));
        }
    }

    @Operation(summary = "Modifier un utilisateur",
            description = "L'utilisateur ne peut modifier que son propre profil, sauf un admin. Seuls nom, prenom, telephone sont modifies.")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    @PutMapping("/update/{id}")
    public ResponseEntity<?> update(
            @PathVariable Long id,
            @RequestBody Investisseur data
    ){
        Optional<User> optionalInvestisseur = userRepository.findById(id);

        if (optionalInvestisseur.isEmpty()){
            return ResponseEntity.notFound().build();
        }

        Investisseur userToUpdate = (Investisseur) optionalInvestisseur.get();

        userToUpdate.setNom(data.getNom());
        userToUpdate.setPrenom(data.getPrenom());
        userToUpdate.setTelephone(data.getTelephone());
//        userToUpdate.setWallet_address(data.getWallet_address());

        userRepository.save(userToUpdate);

        RegisterResponse registerResponse = new RegisterResponse(userToUpdate);

        return ResponseEntity.ok(registerResponse);
    }

    @Operation(summary = "Supprimer un utilisateur", description = "Self ou admin uniquement.")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> delete(
            @PathVariable Long id
    ){
        Optional<User> optionalInvestisseur = userRepository.findById(id);
        if (optionalInvestisseur.isPresent()){
            userRepository.delete(optionalInvestisseur.get());
            return ResponseEntity.status(HttpStatus.OK).build();
        }
        return ResponseEntity.notFound().build();
    }

    @Operation(summary = "Recuperer un utilisateur par id", description = "Self ou admin uniquement.")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    @GetMapping("/{id}")
    public ResponseEntity<?> read(
            @PathVariable Long id
    ){
        Optional<User> userToRetrieve = userRepository.findById(id);
        RegisterResponse registerResponse = null;
        if (userToRetrieve.isPresent()){
            registerResponse = new RegisterResponse((Investisseur) userToRetrieve.get());
            return ResponseEntity.ok(registerResponse);
        }
        return ResponseEntity.notFound().build();
    }
}
