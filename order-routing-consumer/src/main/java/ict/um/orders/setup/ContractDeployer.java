package ict.um.orders.setup;

import ict.um.orders.web3j.OrderLifecycleContract;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.DefaultGasProvider;

public final class ContractDeployer {

    private ContractDeployer() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: ContractDeployer <web3-provider> <private-key>"
            );
        }

        String web3Provider = args[0];
        String privateKey = args[1];

        Web3j web3j =
                Web3j.build(
                        new HttpService(web3Provider)
                );

        try {
            Credentials credentials =
                    Credentials.create(privateKey);

            OrderLifecycleContract contract =
                    OrderLifecycleContract.deploy(
                            web3j,
                            credentials,
                            new DefaultGasProvider()
                    ).send();

            System.out.println(contract.getContractAddress());

        } finally {
            web3j.shutdown();
        }
    }
}
