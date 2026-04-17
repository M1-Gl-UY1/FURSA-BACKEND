package com.fursa.fursa_backend.controller;


import com.fursa.fursa_backend.config.JwtUtils;
import com.fursa.fursa_backend.dto.AuthResponse;
import com.fursa.fursa_backend.dto.RegisterResponse;
import com.fursa.fursa_backend.model.Investisseur;
import com.fursa.fursa_backend.model.User;
import com.fursa.fursa_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class UserController {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final AuthenticationManager authenticationManager;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Investisseur user){
        if (userRepository.findByEmail(user.getEmail()).isPresent()){
            return ResponseEntity.badRequest().body("User with this email:"+user.getEmail()+" already exist");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userRepository.save(user);

        RegisterResponse registerResponse = new RegisterResponse(user);

        return ResponseEntity.ok(registerResponse);
    }

    @PostMapping("/login")
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

}
