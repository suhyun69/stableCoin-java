package com.example.demo.usecase.impl;
import com.example.demo.usecase.RecoverBroadcastsUseCase;
import com.example.demo.chain.application.port.out.*;
import com.example.demo.chain.domain.SignedEthTransaction;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
@Service
@ConditionalOnProperty(name="stablepay.chain.enabled",havingValue="true")
@Transactional(propagation=Propagation.NOT_SUPPORTED)
public class RecoverBroadcastsUseCaseImpl implements RecoverBroadcastsUseCase {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(RecoverBroadcastsUseCaseImpl.class);
    private final EthChainPort chain;private final ChainTransactionStorePort transactions;private final BroadcastStorePort broadcasts;private final RecoveryStorePort store;private final TransactionTemplate tx;
    public RecoverBroadcastsUseCaseImpl(EthChainPort chain,ChainTransactionStorePort transactions,BroadcastStorePort broadcasts,RecoveryStorePort store,PlatformTransactionManager manager) {
        this.chain=chain;this.transactions=transactions;this.broadcasts=broadcasts;this.store=store;this.tx=new TransactionTemplate(manager);
    }
    public int recover() {
        int count=0;
        for(String id:store.candidates(50)) {
            try {
                var saved=transactions.find(id).orElseThrow();
                if(!chain.sender().equalsIgnoreCase(saved.sender())) {
                    tx.executeWithoutResult(s -> store.review(saved,"SIGNER_CHANGED"));continue;
                }
                var observed=chain.transaction(saved.hash());
                if(observed.isEmpty() && chain.confirmedNonce()>saved.nonce()) {
                    // The nonce is mined, but the saved hash is absent: never create a replacement payout.
                    tx.executeWithoutResult(s -> store.review(saved,"NONCE_CONFLICT_OR_TRANSACTION_UNAVAILABLE"));continue;
                }
                boolean accepted=true;String error=null;
                if(observed.isEmpty()) {
                    try {chain.broadcast(new SignedEthTransaction(saved.raw(),saved.hash()));}
                    catch(RuntimeException e) {accepted=false;error=e.toString().substring(0,Math.min(2000,e.toString().length()));}
                }
                boolean result=accepted;String reason=error;
                tx.executeWithoutResult(s -> broadcasts.complete(null,id,result,reason));count++;
            } catch(RuntimeException e) {log.warn("broadcast recovery failed for {}: {}",id,e.getMessage());}
        }
        return count;
    }
}
