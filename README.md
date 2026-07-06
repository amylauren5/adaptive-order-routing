# pharma-hybrid-dapp

Hybrid architectures that combine centralised and decentralised components offer effective solutions for the pharmaceutical supply chain. In this scenario, a manufacturer sells a batch of medicine to a distributor, who then sells it to the consumer. Blockchain ensures security, transparency, and immutability by tracking each transaction and the provenance of drugs. Fully decentralised systems often struggle with scalability and performance, but hybrid systems address these issues by enabling faster data queries, larger storage capacity, and improved transaction speeds. This approach supports key pharmaceutical needs such as regulatory compliance, counterfeit prevention, and reliable supply chain traceability.

### Author
**amylauren5**

## Project Structure

- **pharma-hybrid-dapp/**  
  Contains the semi-decentralised application components and supporting scripts:
  - **pharma-hybrid-producer**: Responsible for producing and sending messages. Combines centralised services (e.g., Axon Server) with decentralised blockchain components to enhance performance and scalability.  
  - **pharma-hybrid-consumer**: Responsible for consuming and processing messages. Integrates centralised and decentralised systems to ensure efficient data handling and improved scalability.  
  - **postman/**: Contains Postman collections and environment configurations for simulating a pharmaceutical supply chain scenario:
    - `pharma_environment.postman_environment.json`:  Defines environment variables (e.g., wallet address) used in requests.
    - `pharma_producer_app.postman_collection.json`: A collection of API requests to simulate producer-side operations within the hybrid system.
  - **scripts/**: Automation and utility scripts supporting development and testing workflows:
    - `baseline-test-run.js`: Runs baseline performance tests without injected faults to measure normal operation timings.  
    - `chaos-test-run.js`: Performs fault injection to evaluate system resilience and recovery behaviour.  
    - `extract-ganache.sh`: Extracts wallet addresses and private keys from the Ganache container and updates the `.env` file.  
    - `start.sh`: Starts containers and services using Docker Compose.  
    - `teardown.sh`: Stops containers and cleans up Docker networks after testing.

- **pharma-full-dapp/**  
  Fully decentralised baseline implementation of the pharmaceutical supply chain without centralised components. Used for comparison and evaluation against the hybrid approach.

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

## Node.js Dependencies and Installation

The testing scripts use the following external Node.js packages:

- **dockerode**: Docker client for managing Docker containers programmatically.  
- **chartjs-node-canvas**: Server-side rendering of Chart.js charts as images.  
- **pdf-lib**: Library for creating and manipulating PDF documents.

Install them using:

```bash
npm install dockerode chartjs-node-canvas pdf-lib
```

### How to Use (Linux or Unix-like environment)

1. Ensure you have a properly configured `.env` file with wallet addresses, private keys, RabbitMQ credentials, and PostgreSQL settings. Then run `./start.sh` from the root directory. This will:  
   - Extract Ganache wallet address and private key using `extract-ganache.sh`  
   - Launch all services with Docker Compose  

Note: The Dockerfiles in `pharma-hybrid-producer` and `pharma-hybrid-consumer` require a JAR file. If needed, you can generate the JAR by running the following command in the respective project directory:

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