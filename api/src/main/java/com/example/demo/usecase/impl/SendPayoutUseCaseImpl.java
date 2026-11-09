package com.example.demo.usecase.impl;
import com.example.demo.usecase.*;
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
public class SendPayoutUseCaseImpl implements SendPayoutUseCase {
    private final PreparePayoutUseCase prepare;
    private final EthChainPort chain;
    private final ChainTransactionStorePort transactions;
    private final BroadcastStorePort store;
    private final TransactionTemplate tx;
    public SendPayoutUseCaseImpl(PreparePayoutUseCase prepare,EthChainPort chain,ChainTransactionStorePort transactions,BroadcastStorePort store,PlatformTransactionManager manager) {
        this.prepare=prepare;this.chain=chain;this.transactions=transactions;this.store=store;this.tx=new TransactionTemplate(manager);
    }
    public void send(String eventId,String payoutId) {
        store.validateEvent(eventId,payoutId);
        if(store.processed(eventId,payoutId)) return;
        prepare.prepare(payoutId);
        var transaction=transactions.find(payoutId).orElseThrow();
        boolean accepted=true; String error=null;
        if("SIGNED".equals(transaction.status())) {
            try {chain.broadcast(new SignedEthTransaction(transaction.raw(),transaction.hash()));}
            catch(RuntimeException e) {accepted=false;error=e.toString().substring(0,Math.min(2000,e.toString().length()));}
        } else if("BROADCAST_UNKNOWN".equals(transaction.status())) { accepted=false; }
        boolean outcome=accepted; String reason=error;
        tx.executeWithoutResult(status -> store.complete(eventId,payoutId,outcome,reason));
    }
}
