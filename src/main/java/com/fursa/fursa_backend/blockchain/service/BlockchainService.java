package com.fursa.fursa_backend.blockchain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.*;

import com.fursa.fursa_backend.blockchain.wrapper.RevenueDistribution;

@Service
@Slf4j
public class BlockchainService {

    private final Web3j web3j;
    private final Credentials credentials;
    private final ContractGasProvider gasProvider;
    private final TransactionManager transactionManager;
    private final String contractAddress;
    private final long chainId;

    private static final BigInteger GAS_PRICE = BigInteger.valueOf(2_000_000_000L);
    private static final BigInteger GAS_LIMIT  = BigInteger.valueOf(300_000L);

    public BlockchainService(
            Web3j web3j,
            Credentials credentials,
            ContractGasProvider gasProvider,
            @Qualifier("web3TransactionManager") TransactionManager transactionManager,
            @Value("${blockchain.contract-address}") String contractAddress,
            @Value("${blockchain.chain-id}") long chainId) {

        this.web3j = web3j;
        this.credentials = credentials;
        this.gasProvider = gasProvider;
        this.transactionManager = transactionManager;
        this.contractAddress = contractAddress;
        this.chainId = chainId;
    }

    // ==============================
    // UTIL
    // ==============================

    private String normalizeAddress(String address) {
        return Keys.toChecksumAddress(address.toLowerCase());
    }

    private BigInteger getNonce() throws Exception {
        return web3j.ethGetTransactionCount(
                credentials.getAddress(),
                DefaultBlockParameterName.LATEST
        ).send().getTransactionCount();
    }

    private String sendRawTransaction(String encodedFunction, BigInteger weiValue) throws Exception {

        BigInteger nonce = getNonce();

        RawTransaction rawTx = RawTransaction.createTransaction(
                nonce,
                GAS_PRICE,
                GAS_LIMIT,
                normalizeAddress(contractAddress),
                weiValue,
                encodedFunction
        );

        byte[] signedTx = TransactionEncoder.signMessage(rawTx, chainId, credentials);
        String hexTx = Numeric.toHexString(signedTx);

        EthSendTransaction response = web3j.ethSendRawTransaction(hexTx).send();

        if (response.hasError()) {
            throw new RuntimeException("Erreur blockchain: " + response.getError().getMessage());
        }

        String txHash = response.getTransactionHash();
        log.info("✅ Transaction envoyée: {}", txHash);

        return txHash;
    }

    // ==============================
    // INVESTORS MANAGEMENT
    // ==============================

    public String addInvestor(String investorAddress) throws Exception {

        String cleanAddress = normalizeAddress(investorAddress);

        Function function = new Function(
            "addInvestor",
            Arrays.asList(
                new org.web3j.abi.datatypes.Address(cleanAddress)
            ),
            Collections.emptyList()
        );

        log.info("Ajout investisseur {}", cleanAddress);
        return sendRawTransaction(FunctionEncoder.encode(function), BigInteger.ZERO);
    }

    // ==============================
    // FUND CONTRACT (pre-alimentation)
    // ==============================

    /**
     * Envoie {@code amountWei} ETH au contrat via sendMoneyAInvestir() (payable).
     * A appeler AVANT toute distribution pour s'assurer que le contrat a un solde
     * suffisant pour payer les investisseurs (payInvestor revert sinon).
     */
    public String fundContract(BigInteger amountWei) throws Exception {
        Function function = new Function(
                "sendMoneyAInvestir",
                Collections.emptyList(),
                Collections.emptyList());

        log.info("Alimentation du contrat : {} wei", amountWei);
        return sendRawTransaction(FunctionEncoder.encode(function), amountWei);
    }

    // ==============================
    // PAY INVESTOR (NEW CORE FUNCTION)
    // ==============================

    public String payInvestor(String investorAddress, BigInteger amountWei) throws Exception {

        String cleanAddress = normalizeAddress(investorAddress);

        Function function = new Function(
                "payInvestor",
                Arrays.asList(
                        new org.web3j.abi.datatypes.Address(cleanAddress),
                        new org.web3j.abi.datatypes.generated.Uint256(amountWei)
                ),
                Collections.emptyList()
        );

        log.info("Paiement de {} wei à {}", amountWei, cleanAddress);

        // IMPORTANT: on ne met PAS amountWei en value
        return sendRawTransaction(FunctionEncoder.encode(function), BigInteger.ZERO);
    }

    // ==============================
    // DISTRIBUTION (SPRING LOGIC)
    // ==============================

    public Map<String, String> distributeToAll(
            List<String> investorAddresses,
            List<Integer> shares,
            BigInteger totalAmountWei) throws Exception {

        if (investorAddresses.size() != shares.size()) {
            throw new IllegalArgumentException("Mismatch data");
        }

        int totalShares = shares.stream().mapToInt(Integer::intValue).sum();

        Map<String, String> txHashes = new LinkedHashMap<>();

        for (int i = 0; i < investorAddresses.size(); i++) {

            BigInteger amount = totalAmountWei
                    .multiply(BigInteger.valueOf(shares.get(i)))
                    .divide(BigInteger.valueOf(totalShares));

            String txHash = payInvestor(investorAddresses.get(i), amount);

            txHashes.put(investorAddresses.get(i), txHash);

            log.info("Paid {} wei to {}", amount, investorAddresses.get(i));
        }

        return txHashes;
    }

    // ==============================
    // READ FUNCTIONS (VIEW)
    // ==============================

    public BigInteger getDividende(String investorAddress) throws Exception {

        String cleanAddress = normalizeAddress(investorAddress);

        RevenueDistribution contract = RevenueDistribution.load(
                normalizeAddress(contractAddress),
                web3j,
                transactionManager,
                gasProvider
        );

        BigInteger dividend = contract.getDividende(cleanAddress).send();

        log.info("Dividende {} = {} wei", cleanAddress, dividend);

        return dividend;
    }

    public BigInteger getContractBalance() throws Exception {

        RevenueDistribution contract = RevenueDistribution.load(
                normalizeAddress(contractAddress),
                web3j,
                transactionManager,
                gasProvider
        );

        BigInteger balance = contract.getContractBalance().send();

        log.info("Solde contrat: {} wei", balance);

        return balance;
    }

    // ==============================
    // UTILS
    // ==============================

    public BigInteger ethToWei(double ethAmount) {
        return Convert.toWei(String.valueOf(ethAmount), Convert.Unit.ETHER).toBigInteger();
    }
}