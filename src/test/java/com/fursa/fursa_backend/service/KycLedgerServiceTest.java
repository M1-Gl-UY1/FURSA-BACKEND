package com.fursa.fursa_backend.service;

import com.fursa.fursa_backend.model.enumeration.TypeSyncBlockchain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.web3j.crypto.Credentials;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V2 U (07/06/2026) : tests unitaires KycLedgerService.
 *
 * Cible :
 *   - hashKyc deterministique et normalise (insensible casse + espaces)
 *   - no-op silencieux si registry non configure
 *   - no-op si wallet investisseur vide
 *   - enqueue dans la queue R si broadcast leve une exception
 */
class KycLedgerServiceTest {

    private BlockchainRpcClient rpc;
    private Credentials credentials;
    private BlockchainSyncQueueService queueService;

    private static final String SEL = "test-sel-min-32-chars-deterministe-aaaaaaa";
    private static final String WALLET = "0xabcabcabcabcabcabcabcabcabcabcabcabcabca";

    @BeforeEach
    void setUp() {
        rpc = mock(BlockchainRpcClient.class);
        credentials = mock(Credentials.class);
        queueService = mock(BlockchainSyncQueueService.class);
    }

    private KycLedgerService serviceAvec(String registryAddr) {
        return new KycLedgerService(
                rpc, credentials,
                11155111L, 20_000_000_000L, 200_000L,
                registryAddr, SEL, 365,
                queueService
        );
    }

    // =========================================================================
    // hashKyc
    // =========================================================================

    @Test
    void hashKyc_est_deterministique_pour_le_meme_input() {
        KycLedgerService s = serviceAvec("0xRegistry");
        String h1 = s.hashKyc("Jorel", "Tiomela",
                LocalDate.of(1990, 10, 3), "softengineit@gmail.com");
        String h2 = s.hashKyc("Jorel", "Tiomela",
                LocalDate.of(1990, 10, 3), "softengineit@gmail.com");
        assertThat(h1).isEqualTo(h2);
        assertThat(h1).startsWith("0x").hasSize(66); // "0x" + 64 hex chars
    }

    @Test
    void hashKyc_normalise_casse_et_espaces() {
        KycLedgerService s = serviceAvec("0xRegistry");
        String reference = s.hashKyc("jorel", "tiomela",
                LocalDate.of(1990, 10, 3), "softengineit@gmail.com");
        String upperSpace = s.hashKyc("  JOREL  ", "Tiomela",
                LocalDate.of(1990, 10, 3), "  SoftEngineIt@Gmail.com  ");
        assertThat(upperSpace).isEqualTo(reference);
    }

    @Test
    void hashKyc_change_si_un_champ_change() {
        KycLedgerService s = serviceAvec("0xRegistry");
        String ref = s.hashKyc("Jorel", "Tiomela",
                LocalDate.of(1990, 10, 3), "softengineit@gmail.com");
        String autreNom = s.hashKyc("Jorel", "Tiomelaa",
                LocalDate.of(1990, 10, 3), "softengineit@gmail.com");
        String autreDate = s.hashKyc("Jorel", "Tiomela",
                LocalDate.of(1991, 10, 3), "softengineit@gmail.com");
        String autreEmail = s.hashKyc("Jorel", "Tiomela",
                LocalDate.of(1990, 10, 3), "autre@gmail.com");
        assertThat(autreNom).isNotEqualTo(ref);
        assertThat(autreDate).isNotEqualTo(ref);
        assertThat(autreEmail).isNotEqualTo(ref);
    }

    @Test
    void hashKyc_change_si_le_sel_change() {
        KycLedgerService s1 = new KycLedgerService(rpc, credentials,
                11155111L, 0L, 0L, "0xRegistry",
                "sel-A-1234567890-1234567890-1234567890", 365,
                queueService);
        KycLedgerService s2 = new KycLedgerService(rpc, credentials,
                11155111L, 0L, 0L, "0xRegistry",
                "sel-B-1234567890-1234567890-1234567890", 365,
                queueService);
        String h1 = s1.hashKyc("Jorel", "Tiomela", LocalDate.of(1990, 1, 1), "a@b.c");
        String h2 = s2.hashKyc("Jorel", "Tiomela", LocalDate.of(1990, 1, 1), "a@b.c");
        assertThat(h1).isNotEqualTo(h2);
    }

    // =========================================================================
    // estActif / no-op
    // =========================================================================

    @Test
    void estActif_false_si_registry_vide() {
        assertThat(serviceAvec("").estActif()).isFalse();
        assertThat(serviceAvec("  ").estActif()).isFalse();
        assertThat(serviceAvec(null).estActif()).isFalse();
    }

    @Test
    void estActif_true_si_registry_renseigne() {
        assertThat(serviceAvec("0x6CA0931ee581DA12317d50dD5273Da34b043A78F")
                .estActif()).isTrue();
    }

    @Test
    void enregistrerKyc_est_no_op_si_registry_vide() throws Exception {
        KycLedgerService s = serviceAvec("");
        s.enregistrerKyc(WALLET, "Jorel", "T", LocalDate.of(1990, 1, 1), "a@b.c");
        verify(rpc, never()).getNonce(any());
        verify(queueService, never()).enqueue(any(), any(), any());
    }

    @Test
    void enregistrerKyc_est_no_op_si_wallet_vide() throws Exception {
        KycLedgerService s = serviceAvec("0xRegistry");
        s.enregistrerKyc("", "Jorel", "T", LocalDate.of(1990, 1, 1), "a@b.c");
        s.enregistrerKyc(null, "Jorel", "T", LocalDate.of(1990, 1, 1), "a@b.c");
        verify(rpc, never()).getNonce(any());
        verify(queueService, never()).enqueue(any(), any(), any());
    }

    @Test
    void enregistrerKyc_no_op_si_wallet_pas_format_0x() throws Exception {
        KycLedgerService s = serviceAvec("0xRegistry");
        s.enregistrerKyc("pas-une-adresse-eth", "Jorel", "T",
                LocalDate.of(1990, 1, 1), "a@b.c");
        verify(rpc, never()).getNonce(any());
        verify(queueService, never()).enqueue(any(), any(), any());
    }

    // =========================================================================
    // Enqueue si broadcast leve
    // =========================================================================

    @Test
    void enregistrerKyc_enqueue_si_broadcast_throw() throws Exception {
        when(credentials.getAddress()).thenReturn("0xDeployer");
        when(rpc.getNonce(any())).thenThrow(new RuntimeException("RPC down"));

        KycLedgerService s = serviceAvec("0xRegistry");
        s.enregistrerKyc(WALLET, "Jorel", "Tiomela",
                LocalDate.of(1990, 10, 3), "softengineit@gmail.com");

        // Le push async appelle broadcast → throw → enqueue.
        // @Async ne s'applique pas en test unitaire (sans EnableAsync) :
        // le code execute synchroniquement le bloc try/catch.
        ArgumentCaptor<TypeSyncBlockchain> typeCap = ArgumentCaptor.forClass(TypeSyncBlockchain.class);
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Map> payloadCap = ArgumentCaptor.forClass(Map.class);
        verify(queueService, atLeastOnce()).enqueue(
                typeCap.capture(), eq(null), payloadCap.capture());
        assertThat(typeCap.getValue()).isEqualTo(TypeSyncBlockchain.ENREGISTRER_KYC);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = payloadCap.getValue();
        assertThat(payload.get("walletAdresse")).isEqualTo(WALLET);
        assertThat(payload).containsKey("hashHex");
        assertThat(payload).containsKey("dateValidationUnix");
        assertThat(payload).containsKey("expireLeUnix");
    }

    @Test
    void revoquerKyc_enqueue_si_broadcast_throw() throws Exception {
        when(credentials.getAddress()).thenReturn("0xDeployer");
        when(rpc.getNonce(any())).thenThrow(new RuntimeException("RPC down"));

        KycLedgerService s = serviceAvec("0xRegistry");
        s.revoquerKyc(WALLET, "Fraude detectee");

        ArgumentCaptor<TypeSyncBlockchain> typeCap = ArgumentCaptor.forClass(TypeSyncBlockchain.class);
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Map> payloadCap = ArgumentCaptor.forClass(Map.class);
        verify(queueService).enqueue(typeCap.capture(), eq(null), payloadCap.capture());
        assertThat(typeCap.getValue()).isEqualTo(TypeSyncBlockchain.REVOQUER_KYC);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = payloadCap.getValue();
        assertThat(payload.get("walletAdresse")).isEqualTo(WALLET);
        assertThat(payload.get("motif")).isEqualTo("Fraude detectee");
    }

    @Test
    void revoquerKyc_motif_par_defaut_si_null() throws Exception {
        when(credentials.getAddress()).thenReturn("0xDeployer");
        when(rpc.getNonce(any())).thenThrow(new RuntimeException("RPC down"));

        KycLedgerService s = serviceAvec("0xRegistry");
        s.revoquerKyc(WALLET, null);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Map> payloadCap = ArgumentCaptor.forClass(Map.class);
        verify(queueService).enqueue(any(), any(), payloadCap.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = payloadCap.getValue();
        assertThat(payload.get("motif")).isEqualTo("Sans motif");
    }
}
