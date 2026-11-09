package com.example.demo.reconciliation.domain;
import java.util.*;
public final class ReconciliationPolicy {
    private ReconciliationPolicy() {}
    public static List<ReconciliationFinding> classify(List<ReconciliationEntry> ledger,List<ObservedEthTransfer> observed,long required) {
        if(required<1) throw new IllegalArgumentException("required confirmations must be positive");
        List<ReconciliationFinding> findings=new ArrayList<>();Map<String,ObservedEthTransfer> transactions=new HashMap<>();
        for(var event:observed) if(transactions.putIfAbsent(event.hash(),event)!=null)
            findings.add(new ReconciliationFinding("DUPLICATE_CHAIN_TRANSACTION",null,event.hash(),"DUPLICATE_OBSERVATION"));
        Set<String> known=new HashSet<>();
        for(var entry:ledger) {
            known.add(entry.hash());var event=transactions.get(entry.hash());
            if(event==null) {
                if("SETTLED".equals(entry.payoutStatus()) || ("REQUIRES_REVIEW".equals(entry.payoutStatus()) && entry.includedBlock()!=null)) findings.add(new ReconciliationFinding("MISSING_CHAIN_TRANSACTION",entry.payoutId(),entry.hash(),"SETTLED_WITHOUT_OBSERVED_TRANSACTION"));
                continue;
            }
            String kind=null,reason=null;
            if(!entry.consistent() || !entry.sender().equalsIgnoreCase(event.sender()) || !entry.recipient().equalsIgnoreCase(event.recipient()) || !entry.amount().equals(event.amount()) || entry.nonce()!=event.nonce()) {
                kind="TRANSACTION_MISMATCH";reason="ETH_TRANSFER_CONTENT_MISMATCH";
            } else if(entry.includedBlock()!=null && entry.includedBlock()!=event.blockNumber()) {
                kind="STATUS_MISMATCH";reason="STORED_BLOCK_NUMBER_MISMATCH";
            } else if("REQUIRES_REVIEW".equals(entry.payoutStatus())) {
                kind="STATUS_MISMATCH";reason="MANUAL_REVIEW_REQUIRED";
            } else if(("FAILED".equals(entry.payoutStatus()) && event.successful())
                    || ("SETTLED".equals(entry.payoutStatus()) && (!event.successful() || event.confirmations()<required))) {
                kind="STATUS_MISMATCH";reason="FINAL_STATUS_CONTRADICTS_CHAIN";
            } else if((List.of("REQUESTED","AWAITING_CONFIRMATION").contains(entry.payoutStatus()) && (!event.successful() || event.confirmations()>=required))
                    || ("SETTLED".equals(entry.payoutStatus()) && !"CONFIRMED".equals(entry.chainStatus()))) {
                kind="STATUS_LAG";reason="CHAIN_RESULT_NOT_APPLIED";
            }
            if(kind!=null) findings.add(new ReconciliationFinding(kind,entry.payoutId(),entry.hash(),reason));
        }
        for(var event:transactions.values()) if(!known.contains(event.hash())) findings.add(
            new ReconciliationFinding("CHAIN_TRANSACTION_WITHOUT_LEDGER",null,event.hash(),"UNTRACKED_MANAGED_WALLET_TRANSFER"));
        return findings.stream().collect(java.util.stream.Collectors.toMap(ReconciliationFinding::identity,f->f,(first,second)->first,LinkedHashMap::new)).values().stream().toList();
    }
}
