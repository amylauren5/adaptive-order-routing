#!/bin/sh

start_blockchain() {
    start_ganache
    extract_ganache_account
    deploy_order_contract
}

start_ganache() {
    echo "Removing previous Ganache container..."
    docker rm -f "$GANACHE_CONTAINER" \
        >/dev/null 2>&1 || true

    echo "Starting Ganache..."

    docker run -d \
        --name "$GANACHE_CONTAINER" \
        --network "$NETWORK_NAME" \
        -p 8545:8545 \
        trufflesuite/ganache-cli \
        --gasLimit 12000000 \
        --accounts 10 \
        --defaultBalanceEther 100 \
        >/dev/null

    wait_for_ganache
}

extract_ganache_account() {
    echo "Extracting Ganache account information..."

    retries=10
    attempt=1

    wallet_address=""
    private_key=""

    while [ "$attempt" -le "$retries" ]; do
        logs=$(
            docker logs "$GANACHE_CONTAINER" \
                --tail 100 2>&1
        )

        wallet_address=$(
            printf '%s\n' "$logs" \
                | grep -m1 '^(0)' \
                | awk '{print $2}'
        )

        private_key=$(
            printf '%s\n' "$logs" \
                | awk '
                    /Private Keys/ {found=1}
                    found && /\(0\)/ {
                        print $2
                        exit
                    }
                '
        )

        if [ -n "$wallet_address" ] \
            && [ -n "$private_key" ]; then
            break
        fi

        echo "Ganache account information not ready; retrying..."

        attempt=$((attempt + 1))
        sleep 2
    done

    if [ -z "$wallet_address" ] \
        || [ -z "$private_key" ]; then

        echo "Failed to extract Ganache account information."
        exit 1
    fi

    set_env_value \
        "WALLET_ADDRESS" \
        "$wallet_address"

    set_env_value \
        "PRIVATE_KEY" \
        "$private_key"

    echo "Ganache wallet: $wallet_address"
}

deploy_order_contract() {
    private_key=$(
        sed -n 's/^PRIVATE_KEY=//p' "$ENV_FILE" \
            | tail -n 1
    )

    if [ -z "$private_key" ]; then
        echo "PRIVATE_KEY was not found in $ENV_FILE"
        exit 1
    fi

    if [ -x "$PROJECT_DIR/mvnw" ]; then
        MAVEN="$PROJECT_DIR/mvnw"
    else
        MAVEN="mvn"
    fi

    echo "Building contract deployment utility..."

    (
        cd "$PROJECT_DIR"
        "$MAVEN" -q -DskipTests install
    )

    echo "Deploying OrderLifecycleContract..."

    deployment_output=$(
        cd "$PROJECT_DIR" &&
        "$MAVEN" -q \
            -pl order-routing-consumer \
            -Dexec.mainClass=ict.um.orders.setup.ContractDeployer \
            -Dexec.args="http://localhost:8545 $private_key" \
            org.codehaus.mojo:exec-maven-plugin:3.5.0:java
    )

    contract_address=$(
        printf '%s\n' "$deployment_output" \
            | grep -Eo '0x[0-9a-fA-F]{40}' \
            | tail -n 1
    )

    if ! printf '%s\n' "$contract_address" \
        | grep -Eq '^0x[0-9a-fA-F]{40}$'; then

        echo "Failed to obtain a valid contract address."
        exit 1
    fi

    set_env_value \
        "CONTRACT_ADDRESS" \
        "$contract_address"

    echo "Contract deployed at: $contract_address"
}