package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.LedgerEventResponse;
import com.fursa.fursa_backend.service.LedgerEventReader;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * V2 Q (07/06/2026) : endpoints admin pour visualiser les events du RevenueLedger.
 *
 *   - GET /api/admin/ledger/events  : liste des events decodes (revenus + dividendes)
 *   - GET /api/admin/ledger/status  : etat du ledger (configure ou pas)
 */
@RestController
@RequestMapping("/api/admin/ledger")
@RequiredArgsConstructor
@Tag(name = "Admin · Audit on-chain",
     description = "Lecture des events RevenueLedger (revenus + distributions)")
public class AdminLedgerController {

    private final LedgerEventReader reader;

    @Operation(summary = "Etat du Ledger",
            description = "Indique si le ledger on-chain est configure (adresse renseignee).")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of("actif", reader.estActif()));
    }

    @Operation(summary = "Events recents du ledger",
            description = "Decode les events RevenuEnregistre + DividendeDistribue sur "
                       + "les N derniers blocs (defaut 10 000). Tri decroissant par block.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/events")
    public ResponseEntity<List<LedgerEventResponse>> events(
            @RequestParam(defaultValue = "10000") int maxBlocks) {
        return ResponseEntity.ok(reader.getRecent(maxBlocks));
    }
}
