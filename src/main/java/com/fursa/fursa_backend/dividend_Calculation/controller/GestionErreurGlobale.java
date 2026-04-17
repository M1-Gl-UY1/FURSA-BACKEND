package com.fursa.fursa_backend.dividend_Calculation.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GestionErreurGlobale {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> gererTout(Exception e){
        Map<String, Object> reponse = new HashMap<>();
        reponse.put("Erreur", "oups"+ e.getMessage());
        reponse.put("code", 400);
        return ResponseEntity.badRequest().body(reponse);
    }
}
