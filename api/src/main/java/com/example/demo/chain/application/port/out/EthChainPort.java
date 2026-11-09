package com.example.demo.chain.application.port.out;
import com.example.demo.chain.domain.*;
import java.util.*;
public interface EthChainPort {
    long chainId();
    String sender();
    long pendingNonce();
    long confirmedNonce();
    SignedEthTransaction sign(ChainTransaction transaction);
    void broadcast(SignedEthTransaction transaction);
    Optional<EthTransfer> transaction(String hash);
    Optional<EthReceipt> receipt(String hash);
    long head();
    String blockHash(long number);
    List<EthTransfer> transfers(long from, long to);
}
