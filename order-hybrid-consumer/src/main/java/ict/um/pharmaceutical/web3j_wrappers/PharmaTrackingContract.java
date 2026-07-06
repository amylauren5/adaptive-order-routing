package ict.um.pharmaceutical.web3j_wrappers;

import io.reactivex.Flowable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.DynamicStruct;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.abi.datatypes.reflection.Parameterized;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.BaseEventResponse;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

/**
 * <p>Auto generated code.
 * <p><strong>Do not modify!</strong>
 * <p>Please use the <a href="https://docs.web3j.io/command_line.html">web3j command line tools</a>,
 * or the org.web3j.codegen.SolidityFunctionWrapperGenerator in the 
 * <a href="https://github.com/hyperledger/web3j/tree/main/codegen">codegen module</a> to update.
 *
 * <p>Generated with web3j version 1.6.1.
 */
@SuppressWarnings("rawtypes")
public class PharmaTrackingContract extends Contract {
    public static final String BINARY = "0x608060405234801561001057600080fd5b507fccfe08badd7fbee8a36c1d2ba2b3090f679bf1a4970d307adddb9d938fc7bd72805460ff1990811660019081179092557f58e76cff22dd72278c8f84685a17f449f02ff85d2e9a03f82022b6f39564086080548216831790557fc4660acc4bd0a40bb2aaddc291a83b2fbde6034df1730ebf08010adf2b67864780548216831790557f2f0b2f4b0fc93ab043d3d4fa5e90f5122cc1c0be4812f3da329115acdaa7df9380548216831790557f5ae0364af9a0a8aa54a8f596975bfc354c6d70994fb2ec282cc5df2494a8f77d80548216831790557f90a99528dafde6a79f826ded12efd3eafe6e21c2b784dcaa4eb83f0e9e170b06805482168317905560066000527fe2689cd4a84e23ad2f564004f1c9013e9589d260bde6380aba3ca7e09e4df40c6020527faa358b62fd9d86899972a45c784907018f4e543f9fe56a6a723d514ffbe72ad9805490911690911790556111f2806101726000396000f3fe608060405234801561001057600080fd5b506004361061007d5760003560e01c806324b5b6ef1161005b57806324b5b6ef146100e0578063a5350ea614610100578063ef85a91f1461013e578063fca301e71461015e57600080fd5b8063048fb86214610082578063089e0251146100ab5780630cd1da55146100cb575b600080fd5b610095610090366004610ce9565b610171565b6040516100a29190610d76565b60405180910390f35b6100be6100b9366004610ce9565b6102a1565b6040516100a29190610dc8565b6100de6100d9366004610df0565b6103fa565b005b6100f36100ee366004610ce9565b610766565b6040516100a29190610e3e565b61012e61010e366004610ed1565b600160209081526000928352604080842090915290825290205460ff1681565b60405190151581526020016100a2565b61015161014c366004610ce9565b61096a565b6040516100a29190610efb565b6100de61016c366004610f46565b610a5f565b6060600080836040516101849190610fba565b908152604051908190036020019020805461019e90610fd6565b9050116101f25760405162461bcd60e51b815260206004820152601a60248201527f4261746368206861736820646f6573206e6f742065786973742e00000000000060448201526064015b60405180910390fd5b6000826040516102029190610fba565b908152604051908190036020019020805461021c90610fd6565b80601f016020809104026020016040519081016040528092919081815260200182805461024890610fd6565b80156102955780601f1061026a57610100808354040283529160200191610295565b820191906000526020600020905b81548152906001019060200180831161027857829003601f168201915b50505050509050919050565b6000806001600160a01b03166000836040516102bd9190610fba565b908152604051908190036020019020600101546001600160a01b0316036102f65760405162461bcd60e51b81526004016101e990611010565b600080836040516103079190610fba565b90815260405190819003602001902060030154116103735760405162461bcd60e51b815260206004820152602360248201527f4e6f2073746174657320617661696c61626c6520666f7220746869732062617460448201526231b41760e91b60648201526084016101e9565b6000826040516103839190610fba565b908152602001604051809103902060030160016000846040516103a69190610fba565b908152604051908190036020019020600301546103c39190611054565b815481106103d3576103d3611075565b90600052602060002090602091828204019190069054906101000a900460ff169050919050565b6000808360405161040b9190610fba565b908152604051908190036020019020805461042590610fd6565b9050116104745760405162461bcd60e51b815260206004820152601a60248201527f4261746368206861736820646f6573206e6f742065786973742e00000000000060448201526064016101e9565b336001600160a01b031660008360405161048e9190610fba565b908152604051908190036020019020600101546001600160a01b0316146105055760405162461bcd60e51b815260206004820152602560248201527f4f6e6c79207468652073656e6465722063616e20757064617465207468652073604482015264746174757360d81b60648201526084016101e9565b600080836040516105169190610fba565b90815260405190819003602001902060030154116105765760405162461bcd60e51b815260206004820152601860248201527f4e6f2070726576696f757320737461746520666f756e642e000000000000000060448201526064016101e9565b600080836040516105879190610fba565b908152602001604051809103902060030160016000856040516105aa9190610fba565b908152604051908190036020019020600301546105c79190611054565b815481106105d7576105d7611075565b60009182526020808320908204015460ff601f9092166101000a900416915060019082600781111561060b5761060b610d90565b600781111561061c5761061c610d90565b8152602001908152602001600020600083600781111561063e5761063e610d90565b600781111561064f5761064f610d90565b815260208101919091526040016000205460ff166106af5760405162461bcd60e51b815260206004820152601860248201527f496e76616c6964207374617465207472616e736974696f6e000000000000000060448201526064016101e9565b6000836040516106bf9190610fba565b90815260405160209181900382019020600301805460018101825560009182529082902091810490910180548492601f166101000a60ff8102199091169083600781111561070f5761070f610d90565b0217905550826040516107229190610fba565b60405180910390207fb7c01fab42a07a66144eec793c5eed1044fcd118b7e16a8aea300e403abb7edb836040516107599190610dc8565b60405180910390a2505050565b6107a360405180608001604052806060815260200160006001600160a01b0316815260200160006001600160a01b03168152602001606081525090565b60006001600160a01b03166000836040516107be9190610fba565b908152604051908190036020019020600101546001600160a01b0316036107f75760405162461bcd60e51b81526004016101e990611010565b6000826040516108079190610fba565b908152602001604051809103902060405180608001604052908160008201805461083090610fd6565b80601f016020809104026020016040519081016040528092919081815260200182805461085c90610fd6565b80156108a95780601f1061087e576101008083540402835291602001916108a9565b820191906000526020600020905b81548152906001019060200180831161088c57829003601f168201915b505050918352505060018201546001600160a01b039081166020808401919091526002840154909116604080840191909152600384018054825181850281018501909352808352606090940193919290919083018282801561095a57602002820191906000526020600020906000905b82829054906101000a900460ff16600781111561093857610938610d90565b8152602060019283018181049485019490930390920291018084116109195790505b5050505050815250509050919050565b606060006001600160a01b03166000836040516109879190610fba565b908152604051908190036020019020600101546001600160a01b0316036109c05760405162461bcd60e51b81526004016101e990611010565b6000826040516109d09190610fba565b908152604080519182900360209081018320600301805480830285018301909352828452919083018282801561029557602002820191906000526020600020906000905b82829054906101000a900460ff166007811115610a3357610a33610d90565b815260206001928301818104948501949093039092029101808411610a14575094979650505050505050565b600083604051610a6f9190610fba565b9081526040519081900360200190208054610a8990610fd6565b159050610ad85760405162461bcd60e51b815260206004820152601960248201527f4261746368206861736820616c7265616479206578697374730000000000000060448201526064016101e9565b600083604051610ae89190610fba565b90815260405160209181900382019020600301805460018101825560009182529082902091810490910180548392601f166101000a60ff81021990911690836007811115610b3857610b38610d90565b021790555081600084604051610b4e9190610fba565b90815260405190819003602001902090610b6890826110da565b5033600084604051610b7a9190610fba565b908152602001604051809103902060010160006101000a8154816001600160a01b0302191690836001600160a01b0316021790555060008084604051610bc09190610fba565b90815260405190819003602001812060020180546001600160a01b03939093166001600160a01b0319909316929092179091553390610c00908590610fba565b60405180910390207ff9ef3eeb41bbb7918522ba1343eb5cbc561fb5fed5e761633e2655a482d58c1f8484604051610c3992919061119a565b60405180910390a3505050565b634e487b7160e01b600052604160045260246000fd5b600082601f830112610c6d57600080fd5b813567ffffffffffffffff80821115610c8857610c88610c46565b604051601f8301601f19908116603f01168101908282118183101715610cb057610cb0610c46565b81604052838152866020858801011115610cc957600080fd5b836020870160208301376000602085830101528094505050505092915050565b600060208284031215610cfb57600080fd5b813567ffffffffffffffff811115610d1257600080fd5b610d1e84828501610c5c565b949350505050565b60005b83811015610d41578181015183820152602001610d29565b50506000910152565b60008151808452610d62816020860160208601610d26565b601f01601f19169290920160200192915050565b602081526000610d896020830184610d4a565b9392505050565b634e487b7160e01b600052602160045260246000fd5b60088110610dc457634e487b7160e01b600052602160045260246000fd5b9052565b60208101610dd68284610da6565b92915050565b803560088110610deb57600080fd5b919050565b60008060408385031215610e0357600080fd5b823567ffffffffffffffff811115610e1a57600080fd5b610e2685828601610c5c565b925050610e3560208401610ddc565b90509250929050565b600060208083528351608082850152610e5a60a0850182610d4a565b858301516001600160a01b0390811660408781019190915287015116606080870191909152860151858203601f19016080870152805180835290840192506000918401905b80831015610ec657610eb2828551610da6565b928401926001929092019190840190610e9f565b509695505050505050565b60008060408385031215610ee457600080fd5b610eed83610ddc565b9150610e3560208401610ddc565b6020808252825182820181905260009190848201906040850190845b81811015610f3a57610f2a838551610da6565b9284019291840191600101610f17565b50909695505050505050565b600080600060608486031215610f5b57600080fd5b833567ffffffffffffffff80821115610f7357600080fd5b610f7f87838801610c5c565b94506020860135915080821115610f9557600080fd5b50610fa286828701610c5c565b925050610fb160408501610ddc565b90509250925092565b60008251610fcc818460208701610d26565b9190910192915050565b600181811c90821680610fea57607f821691505b60208210810361100a57634e487b7160e01b600052602260045260246000fd5b50919050565b60208082526024908201527f547261636b696e6720646f6573206e6f7420657869737420666f7220626174636040820152633424a21760e11b606082015260800190565b81810381811115610dd657634e487b7160e01b600052601160045260246000fd5b634e487b7160e01b600052603260045260246000fd5b601f8211156110d557600081815260208120601f850160051c810160208610156110b25750805b601f850160051c820191505b818110156110d1578281556001016110be565b5050505b505050565b815167ffffffffffffffff8111156110f4576110f4610c46565b611108816111028454610fd6565b8461108b565b602080601f83116001811461113d57600084156111255750858301515b600019600386901b1c1916600185901b1785556110d1565b600085815260208120601f198616915b8281101561116c5788860151825594840194600190910190840161114d565b508582101561118a5787850151600019600388901b60f8161c191681555b5050505050600190811b01905550565b6040815260006111ad6040830185610d4a565b9050610d896020830184610da656fea264697066735822122006759642ae25f1bf42a3467871f7ab91df2790cc91f10e8339f719a964ac9be464736f6c63430008110033";

    private static String librariesLinkedBinary;

    public static final String FUNC_VALIDTRANSITIONS = "validTransitions";

    public static final String FUNC_ADDBATCHTRACKING = "addBatchTracking";

    public static final String FUNC_UPDATEBATCHSTATE = "updateBatchState";

    public static final String FUNC_GETBATCHTRACKING = "getBatchTracking";

    public static final String FUNC_GETLATESTBATCHSTATE = "getLatestBatchState";

    public static final String FUNC_GETALLBATCHSTATES = "getAllBatchStates";

    public static final String FUNC_GETDATAHASH = "getDataHash";

    public static final Event BATCHSTATEUPDATED_EVENT = new Event("BatchStateUpdated", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Utf8String>(true) {}, new TypeReference<Uint8>() {}));
    ;

    public static final Event BATCHTRACKINGADDED_EVENT = new Event("BatchTrackingAdded", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Utf8String>(true) {}, new TypeReference<Utf8String>() {}, new TypeReference<Address>(true) {}, new TypeReference<Uint8>() {}));
    ;

    protected static final HashMap<String, String> _addresses;

    static {
        _addresses = new HashMap<String, String>();
    }

    @Deprecated
    protected PharmaTrackingContract(String contractAddress, Web3j web3j, Credentials credentials,
            BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    protected PharmaTrackingContract(String contractAddress, Web3j web3j, Credentials credentials,
            ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    @Deprecated
    protected PharmaTrackingContract(String contractAddress, Web3j web3j,
            TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    protected PharmaTrackingContract(String contractAddress, Web3j web3j,
            TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static List<BatchStateUpdatedEventResponse> getBatchStateUpdatedEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(BATCHSTATEUPDATED_EVENT, transactionReceipt);
        ArrayList<BatchStateUpdatedEventResponse> responses = new ArrayList<BatchStateUpdatedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            BatchStateUpdatedEventResponse typedResponse = new BatchStateUpdatedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.batchID = (byte[]) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.state = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static BatchStateUpdatedEventResponse getBatchStateUpdatedEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(BATCHSTATEUPDATED_EVENT, log);
        BatchStateUpdatedEventResponse typedResponse = new BatchStateUpdatedEventResponse();
        typedResponse.log = log;
        typedResponse.batchID = (byte[]) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.state = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
        return typedResponse;
    }

    public Flowable<BatchStateUpdatedEventResponse> batchStateUpdatedEventFlowable(
            EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getBatchStateUpdatedEventFromLog(log));
    }

    public Flowable<BatchStateUpdatedEventResponse> batchStateUpdatedEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(BATCHSTATEUPDATED_EVENT));
        return batchStateUpdatedEventFlowable(filter);
    }

    public static List<BatchTrackingAddedEventResponse> getBatchTrackingAddedEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(BATCHTRACKINGADDED_EVENT, transactionReceipt);
        ArrayList<BatchTrackingAddedEventResponse> responses = new ArrayList<BatchTrackingAddedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            BatchTrackingAddedEventResponse typedResponse = new BatchTrackingAddedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.batchID = (byte[]) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.from = (String) eventValues.getIndexedValues().get(1).getValue();
            typedResponse.hash = (String) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.state = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static BatchTrackingAddedEventResponse getBatchTrackingAddedEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(BATCHTRACKINGADDED_EVENT, log);
        BatchTrackingAddedEventResponse typedResponse = new BatchTrackingAddedEventResponse();
        typedResponse.log = log;
        typedResponse.batchID = (byte[]) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.from = (String) eventValues.getIndexedValues().get(1).getValue();
        typedResponse.hash = (String) eventValues.getNonIndexedValues().get(0).getValue();
        typedResponse.state = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
        return typedResponse;
    }

    public Flowable<BatchTrackingAddedEventResponse> batchTrackingAddedEventFlowable(
            EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getBatchTrackingAddedEventFromLog(log));
    }

    public Flowable<BatchTrackingAddedEventResponse> batchTrackingAddedEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(BATCHTRACKINGADDED_EVENT));
        return batchTrackingAddedEventFlowable(filter);
    }

    public RemoteFunctionCall<Boolean> call_validTransitions(BigInteger param0, BigInteger param1) {
        final Function function = new Function(FUNC_VALIDTRANSITIONS, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint8(param0), 
                new org.web3j.abi.datatypes.generated.Uint8(param1)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
        return executeRemoteCallSingleValueReturn(function, Boolean.class);
    }

    public RemoteFunctionCall<TransactionReceipt> send_validTransitions(BigInteger param0,
            BigInteger param1) {
        final Function function = new Function(
                FUNC_VALIDTRANSITIONS, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint8(param0), 
                new org.web3j.abi.datatypes.generated.Uint8(param1)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> send_addBatchTracking(String batchID, String hash,
            BigInteger state) {
        final Function function = new Function(
                FUNC_ADDBATCHTRACKING, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Utf8String(batchID), 
                new org.web3j.abi.datatypes.Utf8String(hash), 
                new org.web3j.abi.datatypes.generated.Uint8(state)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> send_updateBatchState(String batchID,
            BigInteger newState) {
        final Function function = new Function(
                FUNC_UPDATEBATCHSTATE, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Utf8String(batchID), 
                new org.web3j.abi.datatypes.generated.Uint8(newState)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<Tracking> call_getBatchTracking(String batchID) {
        final Function function = new Function(FUNC_GETBATCHTRACKING, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Utf8String(batchID)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Tracking>() {}));
        return executeRemoteCallSingleValueReturn(function, Tracking.class);
    }

    public RemoteFunctionCall<TransactionReceipt> send_getBatchTracking(String batchID) {
        final Function function = new Function(
                FUNC_GETBATCHTRACKING, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Utf8String(batchID)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<BigInteger> call_getLatestBatchState(String batchID) {
        final Function function = new Function(FUNC_GETLATESTBATCHSTATE, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Utf8String(batchID)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint8>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<TransactionReceipt> send_getLatestBatchState(String batchID) {
        final Function function = new Function(
                FUNC_GETLATESTBATCHSTATE, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Utf8String(batchID)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<List> call_getAllBatchStates(String batchID) {
        final Function function = new Function(FUNC_GETALLBATCHSTATES, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Utf8String(batchID)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<DynamicArray<Uint8>>() {}));
        return new RemoteFunctionCall<List>(function,
                new Callable<List>() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public List call() throws Exception {
                        List<Type> result = (List<Type>) executeCallSingleValueReturn(function, List.class);
                        return convertToNative(result);
                    }
                });
    }

    public RemoteFunctionCall<TransactionReceipt> send_getAllBatchStates(String batchID) {
        final Function function = new Function(
                FUNC_GETALLBATCHSTATES, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Utf8String(batchID)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<String> call_getDataHash(String batchID) {
        final Function function = new Function(FUNC_GETDATAHASH, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Utf8String(batchID)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Utf8String>() {}));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    public RemoteFunctionCall<TransactionReceipt> send_getDataHash(String batchID) {
        final Function function = new Function(
                FUNC_GETDATAHASH, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Utf8String(batchID)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    @Deprecated
    public static PharmaTrackingContract load(String contractAddress, Web3j web3j,
            Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return new PharmaTrackingContract(contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    @Deprecated
    public static PharmaTrackingContract load(String contractAddress, Web3j web3j,
            TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return new PharmaTrackingContract(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    public static PharmaTrackingContract load(String contractAddress, Web3j web3j,
            Credentials credentials, ContractGasProvider contractGasProvider) {
        return new PharmaTrackingContract(contractAddress, web3j, credentials, contractGasProvider);
    }

    public static PharmaTrackingContract load(String contractAddress, Web3j web3j,
            TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return new PharmaTrackingContract(contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static RemoteCall<PharmaTrackingContract> deploy(Web3j web3j, Credentials credentials,
            ContractGasProvider contractGasProvider) {
        return deployRemoteCall(PharmaTrackingContract.class, web3j, credentials, contractGasProvider, getDeploymentBinary(), "");
    }

    public static RemoteCall<PharmaTrackingContract> deploy(Web3j web3j,
            TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(PharmaTrackingContract.class, web3j, transactionManager, contractGasProvider, getDeploymentBinary(), "");
    }

    @Deprecated
    public static RemoteCall<PharmaTrackingContract> deploy(Web3j web3j, Credentials credentials,
            BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(PharmaTrackingContract.class, web3j, credentials, gasPrice, gasLimit, getDeploymentBinary(), "");
    }

    @Deprecated
    public static RemoteCall<PharmaTrackingContract> deploy(Web3j web3j,
            TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(PharmaTrackingContract.class, web3j, transactionManager, gasPrice, gasLimit, getDeploymentBinary(), "");
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

    protected String getStaticDeployedAddress(String networkId) {
        return _addresses.get(networkId);
    }

    public static String getPreviouslyDeployedAddress(String networkId) {
        return _addresses.get(networkId);
    }

    public static class Tracking extends DynamicStruct {
        public String hash;

        public String from;

        public String to;

        public List<BigInteger> states;

        public Tracking(String hash, String from, String to, List<BigInteger> states) {
            super(new org.web3j.abi.datatypes.Utf8String(hash), 
                    new org.web3j.abi.datatypes.Address(from), 
                    new org.web3j.abi.datatypes.Address(to), 
                    new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.generated.Uint8>(
                            org.web3j.abi.datatypes.generated.Uint8.class,
                            org.web3j.abi.Utils.typeMap(states, org.web3j.abi.datatypes.generated.Uint8.class)));
            this.hash = hash;
            this.from = from;
            this.to = to;
            this.states = states;
        }

        public Tracking(Utf8String hash, Address from, Address to,
                @Parameterized(type = Uint8.class) DynamicArray<Uint8> states) {
            super(hash, from, to, states);
            this.hash = hash.getValue();
            this.from = from.getValue();
            this.to = to.getValue();
            this.states = states.getValue().stream().map(v -> v.getValue()).collect(Collectors.toList());
        }
    }

    public static class BatchStateUpdatedEventResponse extends BaseEventResponse {
        public byte[] batchID;

        public BigInteger state;
    }

    public static class BatchTrackingAddedEventResponse extends BaseEventResponse {
        public byte[] batchID;

        public String from;

        public String hash;

        public BigInteger state;
    }
}
