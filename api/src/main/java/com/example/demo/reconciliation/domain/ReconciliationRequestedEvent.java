package com.example.demo.reconciliation.domain;
public record ReconciliationRequestedEvent(String eventId,int schemaVersion,String fromBlock,String toBlock) {
    public ReconciliationRequestedEvent {
        if(eventId==null || !eventId.startsWith("run_") || eventId.length()>100 || schemaVersion!=1) throw new IllegalArgumentException("unsupported reconciliation event");
        BlockRange.parse(fromBlock,toBlock);
    }
}
