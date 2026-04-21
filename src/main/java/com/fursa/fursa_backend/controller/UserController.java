package com.fursa.fursa_backend.controller;


import com.fursa.fursa_backend.config.JwtUtils;
import com.fursa.fursa_backend.dto.AuthResponse;
import com.fursa.fursa_backend.dto.RegisterResponse;
import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.model.User;
import com.fursa.fursa_backend.repository.UserRepository;
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
public class UserController {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final AuthenticationManager authenticationManager;

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
