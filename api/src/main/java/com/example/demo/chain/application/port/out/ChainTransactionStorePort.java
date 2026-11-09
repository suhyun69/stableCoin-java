package com.example.demo.chain.application.port.out;
import com.example.demo.chain.domain.*;
import java.util.*;
public interface ChainTransactionStorePort {
    Optional<ChainTransaction> find(String payoutId);
    ChainTransaction prepare(String payoutId, String sender, long initialNonce);
    ChainTransaction signed(String payoutId, SignedEthTransaction signed);
}
