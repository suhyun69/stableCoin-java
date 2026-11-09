package com.example.demo.chain.domain;
public record EthReceipt(String hash, boolean successful, long blockNumber, String blockHash) {}
