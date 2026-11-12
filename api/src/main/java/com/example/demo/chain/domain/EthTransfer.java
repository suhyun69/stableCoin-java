package com.example.demo.chain.domain;

import java.math.BigInteger;

public record EthTransfer(
    String hash,
    String from,
    String to,
    BigInteger value,
    long nonce,
    Long blockNumber,
    String blockHash) {}
