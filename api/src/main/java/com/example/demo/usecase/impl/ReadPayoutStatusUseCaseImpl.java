package com.example.demo.usecase.impl;
import com.example.demo.usecase.ReadPayoutStatusUseCase;
import com.example.demo.usecase.dto.PayoutStatusAppResponse;
import com.example.demo.payout.application.port.out.PayoutStatusReaderPort;
import com.example.demo.common.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class ReadPayoutStatusUseCaseImpl implements ReadPayoutStatusUseCase {
    private final PayoutStatusReaderPort reader;
    public ReadPayoutStatusUseCaseImpl(PayoutStatusReaderPort reader) { this.reader = reader; }
    @Transactional(readOnly=true)
    public PayoutStatusAppResponse read(String id) {
        var result = reader.findById(id).orElseThrow(() -> new NotFoundException("payout not found"));
        var c = result.chain();
        return new PayoutStatusAppResponse(result.id(),result.status(),c == null ? null : new PayoutStatusAppResponse.ChainAppResponse(c.transactionHash(),c.nonce(),c.status(),c.confirmations()));
    }
}
