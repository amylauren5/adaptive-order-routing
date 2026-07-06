// SPDX-License-Identifier: MIT
pragma solidity ^0.8.10;

contract PharmaTrackingContract {

    // Enum for product state
    enum State { Manufactured, SoldToDistributor, ShippedToDistributor, DeliveredToDistributor,
        SoldToReceiver, ShippedToReceiver, DeliveredToReceiver, CancelledByReceiver }

    // Structure to hold product tracking information
    struct Tracking {
        string hash; // Hashed data of the product
        address from; // Address of the sender
        address to; // Address of the receiver
        State[] states; // States of the product
    }

    // Event to emit when a new product hash is added
    event BatchTrackingAdded(string indexed batchID, string hash, address indexed from, State state);
    event BatchStateUpdated(string indexed batchID, State state);

    // Mapping to store product information associated with a batch ID
    mapping(string => Tracking) private batchTracking;

    // Mapping to define valid state transitions
    mapping(State => mapping(State => bool)) public validTransitions;

    constructor() {
        // Define valid state transitions
        validTransitions[State.Manufactured][State.SoldToDistributor] = true;
        validTransitions[State.SoldToDistributor][State.ShippedToDistributor] = true;
        validTransitions[State.ShippedToDistributor][State.DeliveredToDistributor] = true;
        validTransitions[State.DeliveredToDistributor][State.SoldToReceiver] = true;
        validTransitions[State.SoldToReceiver][State.ShippedToReceiver] = true;
        validTransitions[State.SoldToReceiver][State.CancelledByReceiver] = true;
        validTransitions[State.ShippedToReceiver][State.DeliveredToReceiver] = true;
    }

    // Function to add a hashed product data with tracking information
    function addBatchTracking(string memory batchID, string memory hash, State state) public {
        require(bytes(batchTracking[batchID].hash).length == 0, "Batch hash already exists");

        // Directly modify the states array and push the initial state
        batchTracking[batchID].states.push(state);

        // Set tracking information
        batchTracking[batchID].hash = hash;
        batchTracking[batchID].from = msg.sender;
        batchTracking[batchID].to = address(0);

        emit BatchTrackingAdded(batchID, hash, msg.sender, state);
    }

    // Function to update the status of an existing batch
    function updateBatchState(string memory batchID, State newState) public {
        require(bytes(batchTracking[batchID].hash).length > 0, "Batch hash does not exist.");
        require(batchTracking[batchID].from == msg.sender, "Only the sender can update the status");

        // Check for valid state transition
        require(batchTracking[batchID].states.length > 0, "No previous state found.");
        State lastState = batchTracking[batchID].states[batchTracking[batchID].states.length - 1];
        require(validTransitions[lastState][newState], "Invalid state transition");

        // Update the state
        batchTracking[batchID].states.push(newState);
        emit BatchStateUpdated(batchID, newState);
    }

    // Function to retrieve a tracking struct
    function getBatchTracking(string memory batchID) public view returns (Tracking memory) {
        require(batchTracking[batchID].from != address(0), "Tracking does not exist for batchID.");
        return batchTracking[batchID];
    }

    // Function to retrieve a tracking struct
    function getLatestBatchState(string memory batchID) public view returns (State) {
        require(batchTracking[batchID].from != address(0), "Tracking does not exist for batchID.");
        require(batchTracking[batchID].states.length > 0, "No states available for this batch.");
        return batchTracking[batchID].states[batchTracking[batchID].states.length - 1];
    }

    // Function to retrieve a tracking struct
    function getAllBatchStates(string memory batchID) public view returns (State[] memory) {
        require(batchTracking[batchID].from != address(0), "Tracking does not exist for batchID.");
        return batchTracking[batchID].states;
    }

    // Function to retrieve a data hash
    function getDataHash(string memory batchID) public view returns (string memory) {
        require(bytes(batchTracking[batchID].hash).length > 0, "Batch hash does not exist.");
        return batchTracking[batchID].hash;
    }
}
