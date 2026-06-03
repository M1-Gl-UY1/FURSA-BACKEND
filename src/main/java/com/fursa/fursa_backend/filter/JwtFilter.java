package com.fursa.fursa_backend.filter;

import com.fursa.fursa_backend.config.JwtUtils;
import com.fursa.fursa_backend.service.CustomUserService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final CustomUserService customUserDetailsService;
    private final JwtUtils jwtUtils;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        String username = null;
        String jwt;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7);
            // Token expire -> on renvoie un 401 propre (au lieu d'un 403 ou 500).
            // Cela permet a l'interceptor axios cote front de declencher le refresh
            // token automatiquement. Cf bug "sauvegarde impossible" 03/06/2026 :
            // l'ExpiredJwtException remontait au servlet et donnait 403, le
            // refresh axios n'etait pas declenche (il n'est branche que sur 401).
            try {
                username = jwtUtils.extraUsername(jwt);
            } catch (ExpiredJwtException e) {
                log.debug("JWT expire (sub={}, exp={})", e.getClaims().getSubject(), e.getClaims().getExpiration());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write(
                    "{\"error\":\"token_expired\",\"message\":\"Token JWT expire, rafraichissez votre session.\"}");
                return;
            } catch (JwtException e) {
                // Autre erreur JWT (signature invalide, malforme, ...)
                log.debug("JWT invalide : {}", e.getMessage());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write(
                    "{\"error\":\"invalid_token\",\"message\":\"Token JWT invalide.\"}");
                return;
            }

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);
                if (jwtUtils.validateToken(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
