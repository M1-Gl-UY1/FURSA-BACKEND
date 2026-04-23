package com.fursa.fursa_backend.controller;


import com.fursa.fursa_backend.config.JwtUtils;
import com.fursa.fursa_backend.dto.AuthResponse;
import com.fursa.fursa_backend.dto.RegisterResponse;
import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.model.User;
import com.fursa.fursa_backend.repository.UserRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentification & Utilisateurs", description = "Inscription, connexion JWT et gestion des utilisateurs (Emile)")
public class UserController {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final AuthenticationManager authenticationManager;

    @Operation(
            summary = "Creer un compte investisseur",
            description = "Inscription publique. Le mot de passe est encode en BCrypt avant persistance.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Compte cree"),
            @ApiResponse(responseCode = "400", description = "Email deja utilise")
    })
    @SecurityRequirements
    @PostMapping("/auth/register")
    public ResponseEntity<?> register(@RequestBody Investisseur user){
        if (userRepository.findByEmail(user.getEmail()).isPresent()){
            return ResponseEntity.badRequest().body("User with this email:"+user.getEmail()+" already exist");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userRepository.save(user);

        RegisterResponse registerResponse = new RegisterResponse(user);

        return ResponseEntity.ok(registerResponse);
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
    public ResponseEntity<?> login (@RequestBody User user){
        AuthResponse authData = new AuthResponse();

        try {
            Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(user.getEmail(),user.getPassword()));
            if (authentication.isAuthenticated()){

                authData.setToken(jwtUtils.generateToken(user.getEmail()));
                authData.setType("Bearer");

                return ResponseEntity.ok(authData);
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid username or password");
        } catch (AuthenticationException e) {
            log.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid username or password");
        }
    }

    @Operation(summary = "Modifier un utilisateur", description = "Met a jour nom, prenom, telephone.")
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

    @Operation(summary = "Supprimer un utilisateur")
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

    @Operation(summary = "Recuperer un utilisateur par id")
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
