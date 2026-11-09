package com.example.demo.chain.application.port.out;
import com.example.demo.chain.domain.ChainTransaction;
import java.util.List;
public interface RecoveryStorePort {
    List<String> candidates(int limit);
    void review(ChainTransaction transaction, String reason);
}
