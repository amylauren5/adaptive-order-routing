package ict.um.orders.web3j;

import io.reactivex.Flowable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint8;
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
public class OrderLifecycleContract extends Contract {
    public static final String BINARY = "0x608060405234801561001057600080fd5b507fccfe08badd7fbee8a36c1d2ba2b3090f679bf1a4970d307adddb9d938fc7bd72805460ff1990811660019081179092557f58e76cff22dd72278c8f84685a17f449f02ff85d2e9a03f82022b6f39564086080548216831790557fc4660acc4bd0a40bb2aaddc291a83b2fbde6034df1730ebf08010adf2b67864780548216831790557ffa37840a14799ca23d33c55b9b42830805d3d3decc30cbf9394f7f7c62042ab380548216831790557f5d6cd7de0286a98dfeda5747bd584a64ab88877681c0079306854ffd7e7c1a03805482168317905560046000527fd9d16d34ffb15ba3a3d852f0d403e2ce1d691fb54de27ac87cd2f993f3ec330f6020527f350ab52e3bd5168bfe0c12fa1e681e86e66c136cc21ab97368d3d0682461e0f580549091169091179055610e21806101496000396000f3fe608060405234801561001057600080fd5b50600436106100935760003560e01c8063591fc29611610066578063591fc296146101095780636f2465c11461011c578063a5350ea61461012f578063b6a779751461016d578063b96c08661461018d57600080fd5b806304b5bf4f146100985780630ae66920146100ad57806335e2c481146100c05780634a8fc42c146100e9575b600080fd5b6100ab6100a6366004610a16565b6101a0565b005b6100ab6100bb366004610a7a565b6101ff565b6100d36100ce366004610a7a565b61025b565b6040516100e09190610b07565b60405180910390f35b6100fc6100f7366004610a7a565b61035f565b6040516100e09190610b59565b6100ab610117366004610a16565b610432565b6100ab61012a366004610a7a565b6105b2565b61015d61013d366004610b81565b600160209081526000928352604080842090915290825290205460ff1681565b60405190151581526020016100e0565b61018061017b366004610a7a565b610603565b6040516100e09190610bb4565b6100ab61019b366004610a7a565b6106ee565b6101ab82600461073f565b816040516101b99190610bff565b60405180910390207f6cb3f3fa71d549c45372473168978b1f9ea4627df9a8582efba86f38c7969b1a8260046040516101f3929190610c1b565b60405180910390a25050565b61020a81600161073f565b806040516102189190610bff565b60405180910390207f1a0a97d116644d5ab0ca53ba9c390a0044b8da0caa994acdc0773a45a82f61cb60016040516102509190610b59565b60405180910390a250565b60606000808360405161026e9190610bff565b908152604051908190036020019020805461028890610c3d565b9050116102b05760405162461bcd60e51b81526004016102a790610c77565b60405180910390fd5b6000826040516102c09190610bff565b90815260405190819003602001902080546102da90610c3d565b80601f016020809104026020016040519081016040528092919081815260200182805461030690610c3d565b80156103535780601f1061032857610100808354040283529160200191610353565b820191906000526020600020905b81548152906001019060200180831161033657829003601f168201915b50505050509050919050565b6000806000836040516103729190610bff565b908152604051908190036020019020805461038c90610c3d565b9050116103ab5760405162461bcd60e51b81526004016102a790610c77565b6000826040516103bb9190610bff565b908152602001604051809103902060020160016000846040516103de9190610bff565b908152604051908190036020019020600201546103fb9190610ca5565b8154811061040b5761040b610cc6565b90600052602060002090602091828204019190069054906101000a900460ff169050919050565b6000826040516104429190610bff565b908152604051908190036020019020805461045c90610c3d565b1590506104a25760405162461bcd60e51b81526020600482015260146024820152734f7264657220616c72656164792065786973747360601b60448201526064016102a7565b806000836040516104b39190610bff565b908152604051908190036020019020906104cd9082610d2b565b50336000836040516104df9190610bff565b90815260405190819003602001812060010180546001600160a01b03939093166001600160a01b031990931692909217909155600090610520908490610bff565b9081526040516020918190038201812060020180546001810182556000918252908390209281049092018054601f9093166101000a60ff0219909216909155339061056c908490610bff565b60405180910390207f474e2c348bb8a46c74c53eeb7684f174a9149416d202a04e0208d86649a0b29d8360006040516105a6929190610c1b565b60405180910390a35050565b6105bd81600261073f565b806040516105cb9190610bff565b60405180910390207f9dbee98c0ada1de61a679c6234cb495bc02ca82725cdf8a2680bccb05fa16d2260026040516102509190610b59565b6060600080836040516106169190610bff565b908152604051908190036020019020805461063090610c3d565b90501161064f5760405162461bcd60e51b81526004016102a790610c77565b60008260405161065f9190610bff565b908152604080519182900360209081018320600201805480830285018301909352828452919083018282801561035357602002820191906000526020600020906000905b82829054906101000a900460ff1660048111156106c2576106c2610b21565b8152602060019283018181049485019490930390920291018084116106a3575094979650505050505050565b6106f981600361073f565b806040516107079190610bff565b60405180910390207f2b145ab3fca0c14c6abd0350e39280c93fc0f6e35a2f362df331f391107d3e7e60036040516102509190610b59565b600080836040516107509190610bff565b908152604051908190036020019020805461076a90610c3d565b9050116107895760405162461bcd60e51b81526004016102a790610c77565b6000808360405161079a9190610bff565b908152604051908190036020019020600201549050806107f25760405162461bcd60e51b8152602060048201526013602482015272496e76616c6964206f7264657220737461746560681b60448201526064016102a7565b600080846040516108039190610bff565b908152604051908190036020019020600201610820600184610ca5565b8154811061083057610830610cc6565b60009182526020808320908204015460ff601f9092166101000a900416915060019082600481111561086457610864610b21565b600481111561087557610875610b21565b8152602001908152602001600020600084600481111561089757610897610b21565b60048111156108a8576108a8610b21565b815260208101919091526040016000205460ff166109085760405162461bcd60e51b815260206004820152601860248201527f496e76616c6964207374617465207472616e736974696f6e000000000000000060448201526064016102a7565b6000846040516109189190610bff565b90815260405160209181900382019020600201805460018101825560009182529082902091810490910180548592601f166101000a60ff8102199091169083600481111561096857610968610b21565b021790555050505050565b634e487b7160e01b600052604160045260246000fd5b600082601f83011261099a57600080fd5b813567ffffffffffffffff808211156109b5576109b5610973565b604051601f8301601f19908116603f011681019082821181831017156109dd576109dd610973565b816040528381528660208588010111156109f657600080fd5b836020870160208301376000602085830101528094505050505092915050565b60008060408385031215610a2957600080fd5b823567ffffffffffffffff80821115610a4157600080fd5b610a4d86838701610989565b93506020850135915080821115610a6357600080fd5b50610a7085828601610989565b9150509250929050565b600060208284031215610a8c57600080fd5b813567ffffffffffffffff811115610aa357600080fd5b610aaf84828501610989565b949350505050565b60005b83811015610ad2578181015183820152602001610aba565b50506000910152565b60008151808452610af3816020860160208601610ab7565b601f01601f19169290920160200192915050565b602081526000610b1a6020830184610adb565b9392505050565b634e487b7160e01b600052602160045260246000fd5b60058110610b5557634e487b7160e01b600052602160045260246000fd5b9052565b60208101610b678284610b37565b92915050565b803560058110610b7c57600080fd5b919050565b60008060408385031215610b9457600080fd5b610b9d83610b6d565b9150610bab60208401610b6d565b90509250929050565b6020808252825182820181905260009190848201906040850190845b81811015610bf357610be3838551610b37565b9284019291840191600101610bd0565b50909695505050505050565b60008251610c11818460208701610ab7565b9190910192915050565b604081526000610c2e6040830185610adb565b9050610b1a6020830184610b37565b600181811c90821680610c5157607f821691505b602082108103610c7157634e487b7160e01b600052602260045260246000fd5b50919050565b60208082526014908201527313dc99195c88191bd95cc81b9bdd08195e1a5cdd60621b604082015260600190565b81810381811115610b6757634e487b7160e01b600052601160045260246000fd5b634e487b7160e01b600052603260045260246000fd5b601f821115610d2657600081815260208120601f850160051c81016020861015610d035750805b601f850160051c820191505b81811015610d2257828155600101610d0f565b5050505b505050565b815167ffffffffffffffff811115610d4557610d45610973565b610d5981610d538454610c3d565b84610cdc565b602080601f831160018114610d8e5760008415610d765750858301515b600019600386901b1c1916600185901b178555610d22565b600085815260208120601f198616915b82811015610dbd57888601518255948401946001909101908401610d9e565b5085821015610ddb5787850151600019600388901b60f8161c191681555b5050505050600190811b0190555056fea2646970667358221220f692110af1f255274adf2083dd7d6f860c5d74027991ada0c4015f25126230af64736f6c63430008110033";

    private static String librariesLinkedBinary;

    public static final String FUNC_VALIDTRANSITIONS = "validTransitions";

    public static final String FUNC_CREATEORDER = "createOrder";

    public static final String FUNC_APPROVEORDER = "approveOrder";

    public static final String FUNC_DISPATCHORDER = "dispatchOrder";

    public static final String FUNC_COMPLETEORDER = "completeOrder";

    public static final String FUNC_CANCELORDER = "cancelOrder";

    public static final String FUNC_GETORDERSTATES = "getOrderStates";

    public static final String FUNC_GETLATESTSTATE = "getLatestState";

    public static final String FUNC_GETORDERHASH = "getOrderHash";

    public static final Event ORDERAPPROVED_EVENT = new Event("OrderApproved", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Utf8String>(true) {}, new TypeReference<Uint8>() {}));
    ;

    public static final Event ORDERCANCELLED_EVENT = new Event("OrderCancelled", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Utf8String>(true) {}, new TypeReference<Utf8String>() {}, new TypeReference<Uint8>() {}));
    ;

    public static final Event ORDERCOMPLETED_EVENT = new Event("OrderCompleted", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Utf8String>(true) {}, new TypeReference<Uint8>() {}));
    ;

    public static final Event ORDERCREATED_EVENT = new Event("OrderCreated", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Utf8String>(true) {}, new TypeReference<Utf8String>() {}, new TypeReference<Address>(true) {}, new TypeReference<Uint8>() {}));
    ;

    public static final Event ORDERDISPATCHED_EVENT = new Event("OrderDispatched", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Utf8String>(true) {}, new TypeReference<Uint8>() {}));
    ;

    protected static final HashMap<String, String> _addresses;

    static {
        _addresses = new HashMap<String, String>();
    }

    @Deprecated
    protected OrderLifecycleContract(String contractAddress, Web3j web3j, Credentials credentials,
            BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    protected OrderLifecycleContract(String contractAddress, Web3j web3j, Credentials credentials,
            ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    @Deprecated
    protected OrderLifecycleContract(String contractAddress, Web3j web3j,
            TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    protected OrderLifecycleContract(String contractAddress, Web3j web3j,
            TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static List<OrderApprovedEventResponse> getOrderApprovedEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(ORDERAPPROVED_EVENT, transactionReceipt);
        ArrayList<OrderApprovedEventResponse> responses = new ArrayList<OrderApprovedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            OrderApprovedEventResponse typedResponse = new OrderApprovedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.orderId = (byte[]) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.state = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static OrderApprovedEventResponse getOrderApprovedEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(ORDERAPPROVED_EVENT, log);
        OrderApprovedEventResponse typedResponse = new OrderApprovedEventResponse();
        typedResponse.log = log;
        typedResponse.orderId = (byte[]) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.state = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
        return typedResponse;
    }

    public Flowable<OrderApprovedEventResponse> orderApprovedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getOrderApprovedEventFromLog(log));
    }

    public Flowable<OrderApprovedEventResponse> orderApprovedEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(ORDERAPPROVED_EVENT));
        return orderApprovedEventFlowable(filter);
    }

    public static List<OrderCancelledEventResponse> getOrderCancelledEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(ORDERCANCELLED_EVENT, transactionReceipt);
        ArrayList<OrderCancelledEventResponse> responses = new ArrayList<OrderCancelledEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            OrderCancelledEventResponse typedResponse = new OrderCancelledEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.orderId = (byte[]) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.reason = (String) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.state = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static OrderCancelledEventResponse getOrderCancelledEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(ORDERCANCELLED_EVENT, log);
        OrderCancelledEventResponse typedResponse = new OrderCancelledEventResponse();
        typedResponse.log = log;
        typedResponse.orderId = (byte[]) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.reason = (String) eventValues.getNonIndexedValues().get(0).getValue();
        typedResponse.state = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
        return typedResponse;
    }

    public Flowable<OrderCancelledEventResponse> orderCancelledEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getOrderCancelledEventFromLog(log));
    }

    public Flowable<OrderCancelledEventResponse> orderCancelledEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(ORDERCANCELLED_EVENT));
        return orderCancelledEventFlowable(filter);
    }

    public static List<OrderCompletedEventResponse> getOrderCompletedEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(ORDERCOMPLETED_EVENT, transactionReceipt);
        ArrayList<OrderCompletedEventResponse> responses = new ArrayList<OrderCompletedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            OrderCompletedEventResponse typedResponse = new OrderCompletedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.orderId = (byte[]) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.state = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static OrderCompletedEventResponse getOrderCompletedEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(ORDERCOMPLETED_EVENT, log);
        OrderCompletedEventResponse typedResponse = new OrderCompletedEventResponse();
        typedResponse.log = log;
        typedResponse.orderId = (byte[]) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.state = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
        return typedResponse;
    }

    public Flowable<OrderCompletedEventResponse> orderCompletedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getOrderCompletedEventFromLog(log));
    }

    public Flowable<OrderCompletedEventResponse> orderCompletedEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(ORDERCOMPLETED_EVENT));
        return orderCompletedEventFlowable(filter);
    }

    public static List<OrderCreatedEventResponse> getOrderCreatedEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(ORDERCREATED_EVENT, transactionReceipt);
        ArrayList<OrderCreatedEventResponse> responses = new ArrayList<OrderCreatedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            OrderCreatedEventResponse typedResponse = new OrderCreatedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.orderId = (byte[]) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.customer = (String) eventValues.getIndexedValues().get(1).getValue();
            typedResponse.hash = (String) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.state = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static OrderCreatedEventResponse getOrderCreatedEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(ORDERCREATED_EVENT, log);
        OrderCreatedEventResponse typedResponse = new OrderCreatedEventResponse();
        typedResponse.log = log;
        typedResponse.orderId = (byte[]) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.customer = (String) eventValues.getIndexedValues().get(1).getValue();
        typedResponse.hash = (String) eventValues.getNonIndexedValues().get(0).getValue();
        typedResponse.state = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
        return typedResponse;
    }

    public Flowable<OrderCreatedEventResponse> orderCreatedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getOrderCreatedEventFromLog(log));
    }

    public Flowable<OrderCreatedEventResponse> orderCreatedEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(ORDERCREATED_EVENT));
        return orderCreatedEventFlowable(filter);
    }

    public static List<OrderDispatchedEventResponse> getOrderDispatchedEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(ORDERDISPATCHED_EVENT, transactionReceipt);
        ArrayList<OrderDispatchedEventResponse> responses = new ArrayList<OrderDispatchedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            OrderDispatchedEventResponse typedResponse = new OrderDispatchedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.orderId = (byte[]) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.state = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static OrderDispatchedEventResponse getOrderDispatchedEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(ORDERDISPATCHED_EVENT, log);
        OrderDispatchedEventResponse typedResponse = new OrderDispatchedEventResponse();
        typedResponse.log = log;
        typedResponse.orderId = (byte[]) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.state = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
        return typedResponse;
    }

    public Flowable<OrderDispatchedEventResponse> orderDispatchedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getOrderDispatchedEventFromLog(log));
    }

    public Flowable<OrderDispatchedEventResponse> orderDispatchedEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(ORDERDISPATCHED_EVENT));
        return orderDispatchedEventFlowable(filter);
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

    public RemoteFunctionCall<TransactionReceipt> send_createOrder(String orderId, String hash) {
        final Function function = new Function(
                FUNC_CREATEORDER, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Utf8String(orderId), 
                new org.web3j.abi.datatypes.Utf8String(hash)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> send_approveOrder(String orderId) {
        final Function function = new Function(
                FUNC_APPROVEORDER, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Utf8String(orderId)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> send_dispatchOrder(String orderId) {
        final Function function = new Function(
                FUNC_DISPATCHORDER, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Utf8String(orderId)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> send_completeOrder(String orderId) {
        final Function function = new Function(
                FUNC_COMPLETEORDER, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Utf8String(orderId)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> send_cancelOrder(String orderId, String reason) {
        final Function function = new Function(
                FUNC_CANCELORDER, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Utf8String(orderId), 
                new org.web3j.abi.datatypes.Utf8String(reason)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<List> call_getOrderStates(String orderId) {
        final Function function = new Function(FUNC_GETORDERSTATES, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Utf8String(orderId)), 
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

    public RemoteFunctionCall<TransactionReceipt> send_getOrderStates(String orderId) {
        final Function function = new Function(
                FUNC_GETORDERSTATES, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Utf8String(orderId)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<BigInteger> call_getLatestState(String orderId) {
        final Function function = new Function(FUNC_GETLATESTSTATE, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Utf8String(orderId)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint8>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<TransactionReceipt> send_getLatestState(String orderId) {
        final Function function = new Function(
                FUNC_GETLATESTSTATE, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Utf8String(orderId)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<String> call_getOrderHash(String orderId) {
        final Function function = new Function(FUNC_GETORDERHASH, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Utf8String(orderId)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Utf8String>() {}));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    public RemoteFunctionCall<TransactionReceipt> send_getOrderHash(String orderId) {
        final Function function = new Function(
                FUNC_GETORDERHASH, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Utf8String(orderId)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    @Deprecated
    public static OrderLifecycleContract load(String contractAddress, Web3j web3j,
            Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return new OrderLifecycleContract(contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    @Deprecated
    public static OrderLifecycleContract load(String contractAddress, Web3j web3j,
            TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return new OrderLifecycleContract(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    public static OrderLifecycleContract load(String contractAddress, Web3j web3j,
            Credentials credentials, ContractGasProvider contractGasProvider) {
        return new OrderLifecycleContract(contractAddress, web3j, credentials, contractGasProvider);
    }

    public static OrderLifecycleContract load(String contractAddress, Web3j web3j,
            TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return new OrderLifecycleContract(contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static RemoteCall<OrderLifecycleContract> deploy(Web3j web3j, Credentials credentials,
            ContractGasProvider contractGasProvider) {
        return deployRemoteCall(OrderLifecycleContract.class, web3j, credentials, contractGasProvider, getDeploymentBinary(), "");
    }

    public static RemoteCall<OrderLifecycleContract> deploy(Web3j web3j,
            TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(OrderLifecycleContract.class, web3j, transactionManager, contractGasProvider, getDeploymentBinary(), "");
    }

    @Deprecated
    public static RemoteCall<OrderLifecycleContract> deploy(Web3j web3j, Credentials credentials,
            BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(OrderLifecycleContract.class, web3j, credentials, gasPrice, gasLimit, getDeploymentBinary(), "");
    }

    @Deprecated
    public static RemoteCall<OrderLifecycleContract> deploy(Web3j web3j,
            TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(OrderLifecycleContract.class, web3j, transactionManager, gasPrice, gasLimit, getDeploymentBinary(), "");
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

    public static class OrderApprovedEventResponse extends BaseEventResponse {
        public byte[] orderId;

        public BigInteger state;
    }

    public static class OrderCancelledEventResponse extends BaseEventResponse {
        public byte[] orderId;

        public String reason;

        public BigInteger state;
    }

    public static class OrderCompletedEventResponse extends BaseEventResponse {
        public byte[] orderId;

        public BigInteger state;
    }

    public static class OrderCreatedEventResponse extends BaseEventResponse {
        public byte[] orderId;

        public String customer;

        public String hash;

        public BigInteger state;
    }

    public static class OrderDispatchedEventResponse extends BaseEventResponse {
        public byte[] orderId;

        public BigInteger state;
    }
}
