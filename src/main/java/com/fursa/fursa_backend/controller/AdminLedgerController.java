package com.fursa.fursa_backend.controller;

import com.fursa.fursa_backend.dto.LedgerEventResponse;
import com.fursa.fursa_backend.service.KycEventReader;
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
    private final KycEventReader kycReader;

    @Operation(summary = "Etat des contrats audit on-chain",
            description = "Indique si le RevenueLedger et le KycRegistry sont configures.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "actif", reader.estActif() || kycReader.estActif(),
                "ledgerActif", reader.estActif(),
                "kycActif", kycReader.estActif()
        ));
    }

    @Operation(summary = "Events recents (Ledger + KYC)",
            description = "Decode les events RevenuEnregistre + DividendeDistribue + "
                       + "KycEnregistre + KycRevoque sur les N derniers blocs (defaut 10 000). "
                       + "Tri decroissant par block.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/events")
    public ResponseEntity<List<LedgerEventResponse>> events(
            @RequestParam(defaultValue = "10000") int maxBlocks) {
        List<LedgerEventResponse> merged = new java.util.ArrayList<>();
        merged.addAll(reader.getRecent(maxBlocks));
        merged.addAll(kycReader.getRecent(maxBlocks));
        merged.sort((a, b) -> b.blockNumber().compareTo(a.blockNumber()));
        return ResponseEntity.ok(merged);
    }
}
