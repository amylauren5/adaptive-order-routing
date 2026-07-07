# adaptive-order-routing

This project implements an adaptive order‑routing system built on an event‑driven architecture. Orders flow through a lifecycle (create → approve → dispatch → complete or cancel), and routing decisions are shaped by workload characteristics, queue priorities, and synthetic traffic patterns. The system uses Axon Server for command and event handling, PostgreSQL for query‑model storage, and a blockchain contract to anchor tamper‑evident hashes for integrity verification.

While the system is primarily designed to explore routing behaviour under dynamic workloads, it also incorporates a hybrid architecture: operational data is handled by scalable centralised services, while critical integrity metadata is anchored on‑chain. This ensures verifiable state transitions without compromising throughput or latency.

### Author
**amylauren5**

## Project Structure

- **adaptive-order-routing/**  
  Contains the semi-decentralised application components and supporting scripts:
  - **order-hybrid-producer**: Responsible for producing and sending messages. Combines centralised services (e.g., Axon Server) with decentralised blockchain components to enhance performance and scalability.  
  - **order-hybrid-consumer**: Responsible for consuming and processing messages. Integrates centralised and decentralised systems to ensure efficient data handling and improved scalability.  
  - **scripts/**: Automation and utility scripts supporting development and testing workflows:
    - `extract-ganache.sh`: Extracts wallet addresses and private keys from the Ganache container and updates the `.env` file.  
    - `start.sh`: Starts containers and services using Docker Compose.  
    - `teardown.sh`: Stops containers and cleans up Docker networks after testing.

## Prerequisites

- **Java**: Version 21.0.4  
- **Apache Maven**: Version 3.9.9  
- **Truffle**: v5.11.5  
- **Ganache**: v7.9.1  
- **Solidity**: v0.5.16 (solc-js)  
- **Web3.js**: v1.10.0  
- **Node.js**: v22.14.0 
- **npm**: v11.4.0 
- **Docker**: v27.0.3
- **Docker Compose**: v2.28.1-desktop.1

### How to Use (Linux or Unix-like environment)

1. Ensure you have a properly configured `.env` file with wallet addresses, private keys, RabbitMQ credentials, and PostgreSQL settings. Then run `./start.sh` from the root directory. This will:  
   - Extract Ganache wallet address and private key using `extract-ganache.sh`  
   - Launch all services with Docker Compose  

Note: The Dockerfiles in `order-hybrid-producer` and `order-hybrid-consumer` require a JAR file. If needed, you can generate the JAR by running the following command in the respective project directory:

```bash
mvn clean package
```

2. Wait until Axon Server is healthy, then open `http://localhost:8024` to complete the **single-node setup**.

3. Once setup is complete, the **hybrid producer and consumer apps** will start automatically and connect to Axon Server.

4. Test API endpoints with Postman using the collection and environment files in the `postman/` directory, reflecting the pharmaceutical supply chain scenario.

5. To run automated tests, execute `baseline-test-run.js` and `chaos-test-run.js` in the `scripts/` directory. These scripts generate graphs in the `baseline-graph` and `chaos-graph` folders.

6. When finished, run `./teardown.sh` to stop containers and clean up the Docker network.

## License
This project is licensed under the MIT License - see the LICENSE file for details.
