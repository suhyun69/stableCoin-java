package com.example.demo.chain.domain;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import static org.junit.jupiter.api.Assertions.*;
class ConfirmationDecisionTest {
    final ChainTransaction saved=new ChainTransaction("c","p","sender","recipient",BigInteger.ONE,1,"PENDING","raw","hash",null,0);
    final EthTransfer observed=new EthTransfer("hash","sender","recipient",BigInteger.ONE,1,10L,"block");
    @Test void checksConfirmationsReceiptSuccessAndCanonicalBlock() {
        var receipt=new EthReceipt("hash",true,10,"block");
        assertEquals("MINED",ConfirmationDecision.evaluate(saved,observed,receipt,10,"block",2).kind());
        assertEquals("CONFIRMED",ConfirmationDecision.evaluate(saved,observed,receipt,11,"block",2).kind());
        assertEquals("FAILED",ConfirmationDecision.evaluate(saved,observed,new EthReceipt("hash",false,10,"block"),11,"block",2).kind());
        assertEquals("REQUIRES_REVIEW",ConfirmationDecision.evaluate(saved,observed,receipt,11,"other-block",2).kind());
        assertEquals("REQUIRES_REVIEW",ConfirmationDecision.evaluate(saved,null,receipt,11,"block",2).kind());
        var wrong=new EthTransfer("hash","sender","recipient",BigInteger.TWO,1,10L,"block");
        assertEquals("REQUIRES_REVIEW",ConfirmationDecision.evaluate(saved,wrong,receipt,11,"block",2).kind());
    }
}
