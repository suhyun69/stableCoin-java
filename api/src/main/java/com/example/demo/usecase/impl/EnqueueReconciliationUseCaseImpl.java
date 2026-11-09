package com.example.demo.usecase.impl;
import com.example.demo.usecase.EnqueueReconciliationUseCase;
import com.example.demo.usecase.dto.ReconciliationAppResponse;
import com.example.demo.reconciliation.application.port.out.ReconciliationJobsPort;
import com.example.demo.reconciliation.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.*;
import java.util.UUID;
@Service
@ConditionalOnProperty(name="stablepay.messaging.enabled",havingValue="true")
@Transactional(propagation=Propagation.NOT_SUPPORTED)
public class EnqueueReconciliationUseCaseImpl implements EnqueueReconciliationUseCase {
    private final ReconciliationJobsPort jobs;
    public EnqueueReconciliationUseCaseImpl(ReconciliationJobsPort jobs) {this.jobs=jobs;}
    public ReconciliationAppResponse enqueue(String from,String to) {
        var range=BlockRange.parse(from,to);String id="run_"+UUID.randomUUID();
        jobs.enqueue(new ReconciliationRequestedEvent(id,1,Long.toString(range.from()),Long.toString(range.to())));
        return new ReconciliationAppResponse(id,true,Long.toString(range.from()),Long.toString(range.to()));
    }
}
