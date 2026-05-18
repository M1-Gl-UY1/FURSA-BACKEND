package com.fursa.fursa_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigInteger;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.web3j.utils.Convert;

import com.fursa.fursa_backend.blockchain.service.BlockchainService;
import com.fursa.fursa_backend.dto.BlockchainResponse;
import com.fursa.fursa_backend.dto.AddInvestorRequest;
import com.fursa.fursa_backend.service.DistributionService;

@RestController
@RequestMapping("/api/blockchain")
@Tag(name = "Blockchain", description = "Distribution des gains via smart contract")
public class RevenueController {

    private final BlockchainService blockchainService;
    private final DistributionService distributionService;

    public RevenueController(BlockchainService blockchainService,
                             DistributionService distributionService) {
        this.blockchainService = blockchainService;
        this.distributionService = distributionService;
    }

    // Ajouter un investisseur au smart contract
    @PostMapping("/investors")
    @Operation(summary = "Ajouter un investisseur au smart contract")
    public ResponseEntity<BlockchainResponse> addInvestor(
            @RequestBody AddInvestorRequest request) {
        try {
            String txHash = blockchainService.addInvestor(
                request.investorAddress()
            );
            return ResponseEntity.ok(new BlockchainResponse(
                true, txHash, "Investisseur ajouté avec succès", null
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new BlockchainResponse(
                false, null, e.getMessage(), null
            ));
        }
    }

    // Distribuer via blockchain — lit PostgreSQL et appelle payInvestor()
    @PostMapping("/distribute/blockchain/{revenuId}")
    @Operation(summary = "Distribuer les dividendes via blockchain pour un revenu donné")
    public ResponseEntity<BlockchainResponse> distribuerViaBlockchain(
            @PathVariable Long revenuId) {
        try {
            distributionService.distribuerViaBlockchain(revenuId);
            return ResponseEntity.ok(new BlockchainResponse(
                true, null,
                "Distribution blockchain effectuée pour le revenu " + revenuId,
                null
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new BlockchainResponse(
                false, null, e.getMessage(), null
            ));
        }
    }

    // Consulter le dividende d'un investisseur
    @GetMapping("/investors/{address}/dividend")
    @Operation(summary = "Consulter le dividende d'un investisseur")
    public ResponseEntity<BlockchainResponse> getDividend(
            @PathVariable String address) {
        try {
            BigInteger dividend = blockchainService.getDividende(address);
            return ResponseEntity.ok(new BlockchainResponse(
                true, null, "OK",
                Map.of(
                    "address", address,
                    "dividendWei", dividend.toString(),
                    "dividendEth", Convert.fromWei(dividend.toString(), Convert.Unit.ETHER)
                )
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new BlockchainResponse(
                false, null, e.getMessage(), null
            ));
        }
    }

    // Solde du contrat intelligent
    @GetMapping("/balance")
    @Operation(summary = "Solde du contrat intelligent")
    public ResponseEntity<BlockchainResponse> getContractBalance() {
        try {
            BigInteger balance = blockchainService.getContractBalance();
            return ResponseEntity.ok(new BlockchainResponse(
                true, null, "OK",
                Map.of(
                    "balanceWei", balance.toString(),
                    "balanceEth", Convert.fromWei(balance.toString(), Convert.Unit.ETHER)
                )
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new BlockchainResponse(
                false, null, e.getMessage(), null
            ));
        }
    }
}