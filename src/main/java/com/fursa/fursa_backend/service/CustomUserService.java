package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserService implements UserDetailsService {
    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var user = userRepository.findByEmail(username)
                .orElseThrow(()-> new UsernameNotFoundException("User with email : "+username+" not found"));
        // Refuser le login pour les utilisateurs soft-deleted.
        if (user.getDeletedAt() != null) {
            throw new UsernameNotFoundException("User with email : "+username+" not found");
        }
        return user;
    }
}
