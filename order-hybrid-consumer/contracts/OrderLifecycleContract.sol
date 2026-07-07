// SPDX-License-Identifier: MIT
pragma solidity ^0.8.10;

contract OrderLifecycleContract {

    // Order lifecycle states (aligned with Axon OrderStatus)
    enum State {
        CREATED,
        APPROVED,
        DISPATCHED,
        COMPLETED,
        CANCELLED
    }

    struct OrderTracking {
        string hash;        // Immutable order hash (off-chain payload hash)
        address customer;   // Customer placing the order
        State[] states;     // Full event-sourced state history
    }

    event OrderCreated(string indexed orderId, string hash, address indexed customer, State state);
    event OrderApproved(string indexed orderId, State state);
    event OrderDispatched(string indexed orderId, State state);
    event OrderCompleted(string indexed orderId, State state);
    event OrderCancelled(string indexed orderId, string reason, State state);

    mapping(string => OrderTracking) private orders;

    // Valid transitions aligned with Axon aggregate
    mapping(State => mapping(State => bool)) public validTransitions;

    constructor() {
        validTransitions[State.CREATED][State.APPROVED] = true;
        validTransitions[State.APPROVED][State.DISPATCHED] = true;
        validTransitions[State.DISPATCHED][State.COMPLETED] = true;

        // Cancellation allowed unless COMPLETED
        validTransitions[State.CREATED][State.CANCELLED] = true;
        validTransitions[State.APPROVED][State.CANCELLED] = true;
        validTransitions[State.DISPATCHED][State.CANCELLED] = true;
    }

    // --- CREATE ---
    function createOrder(string memory orderId, string memory hash) public {
        require(bytes(orders[orderId].hash).length == 0, "Order already exists");

        orders[orderId].hash = hash;
        orders[orderId].customer = msg.sender;
        orders[orderId].states.push(State.CREATED);

        emit OrderCreated(orderId, hash, msg.sender, State.CREATED);
    }

    // --- APPROVE ---
    function approveOrder(string memory orderId) public {
        _transition(orderId, State.APPROVED);
        emit OrderApproved(orderId, State.APPROVED);
    }

    // --- DISPATCH ---
    function dispatchOrder(string memory orderId) public {
        _transition(orderId, State.DISPATCHED);
        emit OrderDispatched(orderId, State.DISPATCHED);
    }

    // --- COMPLETE ---
    function completeOrder(string memory orderId) public {
        _transition(orderId, State.COMPLETED);
        emit OrderCompleted(orderId, State.COMPLETED);
    }

    // --- CANCEL ---
    function cancelOrder(string memory orderId, string memory reason) public {
        _transition(orderId, State.CANCELLED);
        emit OrderCancelled(orderId, reason, State.CANCELLED);
    }

    // --- INTERNAL TRANSITION CHECK ---
    function _transition(string memory orderId, State newState) internal {
        require(bytes(orders[orderId].hash).length > 0, "Order does not exist");

        uint256 len = orders[orderId].states.length;
        require(len > 0, "Invalid order state");

        State lastState = orders[orderId].states[len - 1];
        require(validTransitions[lastState][newState], "Invalid state transition");

        orders[orderId].states.push(newState);
    }

    // --- GETTERS ---
    function getOrderStates(string memory orderId) public view returns (State[] memory) {
        require(bytes(orders[orderId].hash).length > 0, "Order does not exist");
        return orders[orderId].states;
    }

    function getLatestState(string memory orderId) public view returns (State) {
        require(bytes(orders[orderId].hash).length > 0, "Order does not exist");
        return orders[orderId].states[orders[orderId].states.length - 1];
    }

    function getOrderHash(string memory orderId) public view returns (string memory) {
        require(bytes(orders[orderId].hash).length > 0, "Order does not exist");
        return orders[orderId].hash;
    }
}