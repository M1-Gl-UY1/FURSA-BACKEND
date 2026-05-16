package com.fursa.fursa_backend.blockchain.wrapper;

import io.reactivex.Flowable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.BaseEventResponse;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tuples.generated.Tuple3;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

/**
 * <p>Auto generated code.
 * <p><strong>Do not modify!</strong>
 * <p>Please use the <a href="https://docs.web3j.io/command_line.html">web3j command line tools</a>,
 * or the org.web3j.codegen.SolidityFunctionWrapperGenerator in the 
 * <a href="https://github.com/LFDT-web3j/web3j/tree/main/codegen">codegen module</a> to update.
 *
 * <p>Generated with web3j version 1.8.0.
 */
@SuppressWarnings("rawtypes")
@Generated("org.web3j.codegen.SolidityFunctionWrapperGenerator")
public class RevenueDistribution extends Contract {
    public static final String BINARY = "6080604052348015600e575f5ffd5b50335f5f6101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff16021790555061112c8061005b5f395ff3fe608060405260043610610094575f3560e01c8063b196440c11610058578063b196440c1461019d578063ca840dfd146101c5578063d061c81e14610201578063e8930efd1461022b578063ec20b45714610269576100e9565b80632ec9f0a7146100ed5780633ccfd60b146100f75780636f9fb98a1461010d5780637ddb9614146101375780638da5cb5b14610173576100e9565b366100e9573373ffffffffffffffffffffffffffffffffffffffff167f7a7b15f5f0f3c582c8d341fe0ee52bbd457182456bf4f834e027c218a6f06c2c346040516100df9190610af1565b60405180910390a2005b5f5ffd5b6100f5610291565b005b348015610102575f5ffd5b5061010b610323565b005b348015610118575f5ffd5b5061012161049a565b60405161012e9190610af1565b60405180910390f35b348015610142575f5ffd5b5061015d60048036038101906101589190610b68565b6104a1565b60405161016a9190610af1565b60405180910390f35b34801561017e575f5ffd5b506101876104ea565b6040516101949190610ba2565b60405180910390f35b3480156101a8575f5ffd5b506101c360048036038101906101be9190610be5565b61050e565b005b3480156101d0575f5ffd5b506101eb60048036038101906101e69190610c23565b6107bb565b6040516101f89190610ba2565b60405180910390f35b34801561020c575f5ffd5b506102156107f6565b6040516102229190610af1565b60405180910390f35b348015610236575f5ffd5b50610251600480360381019061024c9190610b68565b6107fc565b60405161026093929190610c68565b60405180910390f35b348015610274575f5ffd5b5061028f600480360381019061028a9190610be5565b61082e565b005b5f34116102d3576040517f08c379a00000000000000000000000000000000000000000000000000000000081526004016102ca90610cf7565b60405180910390fd5b3373ffffffffffffffffffffffffffffffffffffffff167f7a7b15f5f0f3c582c8d341fe0ee52bbd457182456bf4f834e027c218a6f06c2c346040516103199190610af1565b60405180910390a2565b5f60025f3373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f206001015490505f81116103a9576040517f08c379a00000000000000000000000000000000000000000000000000000000081526004016103a090610d5f565b60405180910390fd5b5f60025f3373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f20600101819055505f3373ffffffffffffffffffffffffffffffffffffffff168260405161041390610daa565b5f6040518083038185875af1925050503d805f811461044d576040519150601f19603f3d011682016040523d82523d5f602084013e610452565b606091505b5050905080610496576040517f08c379a000000000000000000000000000000000000000000000000000000000815260040161048d90610e08565b60405180910390fd5b5050565b5f47905090565b5f60025f8373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f20600101549050919050565b5f5f9054906101000a900473ffffffffffffffffffffffffffffffffffffffff1681565b5f5f9054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff163373ffffffffffffffffffffffffffffffffffffffff161461059c576040517f08c379a000000000000000000000000000000000000000000000000000000000815260040161059390610e70565b60405180910390fd5b60025f8373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f206002015f9054906101000a900460ff16610628576040517f08c379a000000000000000000000000000000000000000000000000000000000815260040161061f90610ed8565b60405180910390fd5b5f811161066a576040517f08c379a000000000000000000000000000000000000000000000000000000000815260040161066190610f40565b60405180910390fd5b8060025f8473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f206001015f8282546106b99190610f8b565b925050819055505f8273ffffffffffffffffffffffffffffffffffffffff16826040516106e590610daa565b5f6040518083038185875af1925050503d805f811461071f576040519150601f19603f3d011682016040523d82523d5f602084013e610724565b606091505b5050905080610768576040517f08c379a000000000000000000000000000000000000000000000000000000000815260040161075f90611008565b60405180910390fd5b8273ffffffffffffffffffffffffffffffffffffffff167f42ed5d158bb00a3dbdd37942ce6b72d84664671b888b9488e4c38d64707dd144836040516107ae9190610af1565b60405180910390a2505050565b600381815481106107ca575f80fd5b905f5260205f20015f915054906101000a900473ffffffffffffffffffffffffffffffffffffffff1681565b60015481565b6002602052805f5260405f205f91509050805f015490806001015490806002015f9054906101000a900460ff16905083565b5f5f9054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff163373ffffffffffffffffffffffffffffffffffffffff16146108bc576040517f08c379a00000000000000000000000000000000000000000000000000000000081526004016108b390610e70565b60405180910390fd5b60025f8373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f206002015f9054906101000a900460ff1615610949576040517f08c379a000000000000000000000000000000000000000000000000000000000815260040161094090611070565b60405180910390fd5b5f811161098b576040517f08c379a0000000000000000000000000000000000000000000000000000000008152600401610982906110d8565b60405180910390fd5b60405180606001604052808281526020015f81526020016001151581525060025f8473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020015f205f820151815f0155602082015181600101556040820151816002015f6101000a81548160ff021916908315150217905550905050600382908060018154018082558091505060019003905f5260205f20015f9091909190916101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff1602179055508060015f828254610a8b9190610f8b565b925050819055508173ffffffffffffffffffffffffffffffffffffffff167f1c1fd6a3e03da9a8b40ce77099c31e591a0f1976c7efdb2dd0b40c951f49228e60405160405180910390a25050565b5f819050919050565b610aeb81610ad9565b82525050565b5f602082019050610b045f830184610ae2565b92915050565b5f5ffd5b5f73ffffffffffffffffffffffffffffffffffffffff82169050919050565b5f610b3782610b0e565b9050919050565b610b4781610b2d565b8114610b51575f5ffd5b50565b5f81359050610b6281610b3e565b92915050565b5f60208284031215610b7d57610b7c610b0a565b5b5f610b8a84828501610b54565b91505092915050565b610b9c81610b2d565b82525050565b5f602082019050610bb55f830184610b93565b92915050565b610bc481610ad9565b8114610bce575f5ffd5b50565b5f81359050610bdf81610bbb565b92915050565b5f5f60408385031215610bfb57610bfa610b0a565b5b5f610c0885828601610b54565b9250506020610c1985828601610bd1565b9150509250929050565b5f60208284031215610c3857610c37610b0a565b5b5f610c4584828501610bd1565b91505092915050565b5f8115159050919050565b610c6281610c4e565b82525050565b5f606082019050610c7b5f830186610ae2565b610c886020830185610ae2565b610c956040830184610c59565b949350505050565b5f82825260208201905092915050565b7f45544820726571756973000000000000000000000000000000000000000000005f82015250565b5f610ce1600a83610c9d565b9150610cec82610cad565b602082019050919050565b5f6020820190508181035f830152610d0e81610cd5565b9050919050565b7f7269656e206120726574697265720000000000000000000000000000000000005f82015250565b5f610d49600e83610c9d565b9150610d5482610d15565b602082019050919050565b5f6020820190508181035f830152610d7681610d3d565b9050919050565b5f81905092915050565b50565b5f610d955f83610d7d565b9150610da082610d87565b5f82019050919050565b5f610db482610d8a565b9150819050919050565b7f6563686f756500000000000000000000000000000000000000000000000000005f82015250565b5f610df2600683610c9d565b9150610dfd82610dbe565b602082019050919050565b5f6020820190508181035f830152610e1f81610de6565b9050919050565b7f7365756c206f776e6572000000000000000000000000000000000000000000005f82015250565b5f610e5a600a83610c9d565b9150610e6582610e26565b602082019050919050565b5f6020820190508181035f830152610e8781610e4e565b9050919050565b7f696e6578697374616e74000000000000000000000000000000000000000000005f82015250565b5f610ec2600a83610c9d565b9150610ecd82610e8e565b602082019050919050565b5f6020820190508181035f830152610eef81610eb6565b9050919050565b7f6d6f6e74616e7420696e76616c696465000000000000000000000000000000005f82015250565b5f610f2a601083610c9d565b9150610f3582610ef6565b602082019050919050565b5f6020820190508181035f830152610f5781610f1e565b9050919050565b7f4e487b71000000000000000000000000000000000000000000000000000000005f52601160045260245ffd5b5f610f9582610ad9565b9150610fa083610ad9565b9250828201905080821115610fb857610fb7610f5e565b5b92915050565b7f7472616e7366657274206563686f7565000000000000000000000000000000005f82015250565b5f610ff2601083610c9d565b9150610ffd82610fbe565b602082019050919050565b5f6020820190508181035f83015261101f81610fe6565b9050919050565b7f6578697374652064656a610000000000000000000000000000000000000000005f82015250565b5f61105a600b83610c9d565b915061106582611026565b602082019050919050565b5f6020820190508181035f8301526110878161104e565b9050919050565b7f736861726573203e2030000000000000000000000000000000000000000000005f82015250565b5f6110c2600a83610c9d565b91506110cd8261108e565b602082019050919050565b5f6020820190508181035f8301526110ef816110b6565b905091905056fea2646970667358221220359f61dfae51aa9caaf86bf611924089981bc0f8a205519cf4725133b46c036b64736f6c63430008220033";

    private static String librariesLinkedBinary;

    public static final String FUNC_ADDINVESTOR = "addInvestor";

    public static final String FUNC_PAYINVESTOR = "payInvestor";

    public static final String FUNC_SENDMONEYAINVESTIR = "sendMoneyAInvestir";

    public static final String FUNC_WITHDRAW = "withdraw";

    public static final String FUNC_GETCONTRACTBALANCE = "getContractBalance";

    public static final String FUNC_GETDIVIDENDE = "getDividende";

    public static final String FUNC_INVESTORS = "Investors";

    public static final String FUNC_INVESTORSLIST = "InvestorsList";

    public static final String FUNC_OWNER = "owner";

    public static final String FUNC_TOTALSHARES = "totalshares";

    public static final Event DIVIDENDEVERSEEVENT_EVENT = new Event("DividendeVerseEvent", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Uint256>() {}));
    ;

    public static final Event INVESTISSEURAJOUTEREVENT_EVENT = new Event("InvestisseurAjouterEvent", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}));
    ;

    public static final Event PAIEMENTRECUEVENT_EVENT = new Event("PaiementRecuEvent", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Uint256>() {}));
    ;

    @Deprecated
    protected RevenueDistribution(String contractAddress, Web3j web3j, Credentials credentials,
            BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    protected RevenueDistribution(String contractAddress, Web3j web3j, Credentials credentials,
            ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    @Deprecated
    protected RevenueDistribution(String contractAddress, Web3j web3j,
            TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    protected RevenueDistribution(String contractAddress, Web3j web3j,
            TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static List<DividendeVerseEventEventResponse> getDividendeVerseEventEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(DIVIDENDEVERSEEVENT_EVENT, transactionReceipt);
        ArrayList<DividendeVerseEventEventResponse> responses = new ArrayList<DividendeVerseEventEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            DividendeVerseEventEventResponse typedResponse = new DividendeVerseEventEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.investor = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.amount = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static DividendeVerseEventEventResponse getDividendeVerseEventEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(DIVIDENDEVERSEEVENT_EVENT, log);
        DividendeVerseEventEventResponse typedResponse = new DividendeVerseEventEventResponse();
        typedResponse.log = log;
        typedResponse.investor = (String) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.amount = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
        return typedResponse;
    }

    public Flowable<DividendeVerseEventEventResponse> dividendeVerseEventEventFlowable(
            EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getDividendeVerseEventEventFromLog(log));
    }

    public Flowable<DividendeVerseEventEventResponse> dividendeVerseEventEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(DIVIDENDEVERSEEVENT_EVENT));
        return dividendeVerseEventEventFlowable(filter);
    }

    public static List<InvestisseurAjouterEventEventResponse> getInvestisseurAjouterEventEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(INVESTISSEURAJOUTEREVENT_EVENT, transactionReceipt);
        ArrayList<InvestisseurAjouterEventEventResponse> responses = new ArrayList<InvestisseurAjouterEventEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            InvestisseurAjouterEventEventResponse typedResponse = new InvestisseurAjouterEventEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.investor = (String) eventValues.getIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static InvestisseurAjouterEventEventResponse getInvestisseurAjouterEventEventFromLog(
            Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(INVESTISSEURAJOUTEREVENT_EVENT, log);
        InvestisseurAjouterEventEventResponse typedResponse = new InvestisseurAjouterEventEventResponse();
        typedResponse.log = log;
        typedResponse.investor = (String) eventValues.getIndexedValues().get(0).getValue();
        return typedResponse;
    }

    public Flowable<InvestisseurAjouterEventEventResponse> investisseurAjouterEventEventFlowable(
            EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getInvestisseurAjouterEventEventFromLog(log));
    }

    public Flowable<InvestisseurAjouterEventEventResponse> investisseurAjouterEventEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(INVESTISSEURAJOUTEREVENT_EVENT));
        return investisseurAjouterEventEventFlowable(filter);
    }

    public static List<PaiementRecuEventEventResponse> getPaiementRecuEventEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(PAIEMENTRECUEVENT_EVENT, transactionReceipt);
        ArrayList<PaiementRecuEventEventResponse> responses = new ArrayList<PaiementRecuEventEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            PaiementRecuEventEventResponse typedResponse = new PaiementRecuEventEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.from = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.amount = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static PaiementRecuEventEventResponse getPaiementRecuEventEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(PAIEMENTRECUEVENT_EVENT, log);
        PaiementRecuEventEventResponse typedResponse = new PaiementRecuEventEventResponse();
        typedResponse.log = log;
        typedResponse.from = (String) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.amount = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
        return typedResponse;
    }

    public Flowable<PaiementRecuEventEventResponse> paiementRecuEventEventFlowable(
            EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getPaiementRecuEventEventFromLog(log));
    }

    public Flowable<PaiementRecuEventEventResponse> paiementRecuEventEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(PAIEMENTRECUEVENT_EVENT));
        return paiementRecuEventEventFlowable(filter);
    }

    public RemoteFunctionCall<TransactionReceipt> addInvestor(String _investor,
            BigInteger _shares) {
        final Function function = new Function(
                FUNC_ADDINVESTOR, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _investor), 
                new org.web3j.abi.datatypes.generated.Uint256(_shares)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> payInvestor(String _investor,
            BigInteger _amount) {
        final Function function = new Function(
                FUNC_PAYINVESTOR, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _investor), 
                new org.web3j.abi.datatypes.generated.Uint256(_amount)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> sendMoneyAInvestir(BigInteger weiValue) {
        final Function function = new Function(
                FUNC_SENDMONEYAINVESTIR, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function, weiValue);
    }

    public RemoteFunctionCall<TransactionReceipt> withdraw() {
        final Function function = new Function(
                FUNC_WITHDRAW, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<BigInteger> getContractBalance() {
        final Function function = new Function(FUNC_GETCONTRACTBALANCE, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> getDividende(String _investor) {
        final Function function = new Function(FUNC_GETDIVIDENDE, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _investor)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<Tuple3<BigInteger, BigInteger, Boolean>> Investors(String param0) {
        final Function function = new Function(FUNC_INVESTORS, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, param0)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}, new TypeReference<Bool>() {}));
        return new RemoteFunctionCall<Tuple3<BigInteger, BigInteger, Boolean>>(function,
                new Callable<Tuple3<BigInteger, BigInteger, Boolean>>() {
                    @Override
                    public Tuple3<BigInteger, BigInteger, Boolean> call() throws Exception {
                        List<Type> results = executeCallMultipleValueReturn(function);
                        return new Tuple3<BigInteger, BigInteger, Boolean>(
                                (BigInteger) results.get(0).getValue(), 
                                (BigInteger) results.get(1).getValue(), 
                                (Boolean) results.get(2).getValue());
                    }
                });
    }

    public RemoteFunctionCall<String> InvestorsList(BigInteger param0) {
        final Function function = new Function(FUNC_INVESTORSLIST, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(param0)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    public RemoteFunctionCall<String> owner() {
        final Function function = new Function(FUNC_OWNER, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    public RemoteFunctionCall<BigInteger> totalshares() {
        final Function function = new Function(FUNC_TOTALSHARES, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    @Deprecated
    public static RevenueDistribution load(String contractAddress, Web3j web3j,
            Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return new RevenueDistribution(contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    @Deprecated
    public static RevenueDistribution load(String contractAddress, Web3j web3j,
            TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return new RevenueDistribution(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    public static RevenueDistribution load(String contractAddress, Web3j web3j,
            Credentials credentials, ContractGasProvider contractGasProvider) {
        return new RevenueDistribution(contractAddress, web3j, credentials, contractGasProvider);
    }

    public static RevenueDistribution load(String contractAddress, Web3j web3j,
            TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return new RevenueDistribution(contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static RemoteCall<RevenueDistribution> deploy(Web3j web3j, Credentials credentials,
            ContractGasProvider contractGasProvider) {
        return deployRemoteCall(RevenueDistribution.class, web3j, credentials, contractGasProvider, getDeploymentBinary(), "");
    }

    public static RemoteCall<RevenueDistribution> deploy(Web3j web3j,
            TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(RevenueDistribution.class, web3j, transactionManager, contractGasProvider, getDeploymentBinary(), "");
    }

    @Deprecated
    public static RemoteCall<RevenueDistribution> deploy(Web3j web3j, Credentials credentials,
            BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(RevenueDistribution.class, web3j, credentials, gasPrice, gasLimit, getDeploymentBinary(), "");
    }

    @Deprecated
    public static RemoteCall<RevenueDistribution> deploy(Web3j web3j,
            TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(RevenueDistribution.class, web3j, transactionManager, gasPrice, gasLimit, getDeploymentBinary(), "");
    }

    public static void linkLibraries(List<Contract.LinkReference> references) {
        librariesLinkedBinary = linkBinaryWithReferences(BINARY, references);
    }

    private static String getDeploymentBinary() {
        if (librariesLinkedBinary != null) {
            return librariesLinkedBinary;
        } else {
            return BINARY;
        }
    }

    public static class DividendeVerseEventEventResponse extends BaseEventResponse {
        public String investor;

        public BigInteger amount;
    }

    public static class InvestisseurAjouterEventEventResponse extends BaseEventResponse {
        public String investor;
    }

    public static class PaiementRecuEventEventResponse extends BaseEventResponse {
        public String from;

        public BigInteger amount;
    }
}
