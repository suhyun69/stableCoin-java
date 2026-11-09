package com.example.demo.payout.domain;

import com.example.demo.common.exception.InvalidInputException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigInteger;
import static org.junit.jupiter.api.Assertions.*;

class PayoutDomainTest {
    private static final String ADDRESS = "0x" + "ab".repeat(20);

    @Test
    void convertsEthWithoutRounding() {
        assertEquals(BigInteger.ONE, EthAmount.fromEth("0.000000000000000001").wei());
        assertEquals(new BigInteger("1234567890123456789"), EthAmount.fromEth("1.234567890123456789").wei());
        assertEquals("1.23", EthAmount.fromEth("01.2300").toEth());
        BigInteger maximum = BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE);
        assertEquals(maximum, new EthAmount(maximum).wei());
        assertThrows(InvalidInputException.class, () -> new EthAmount(maximum.add(BigInteger.ONE)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0", "0.000", "-1", "+1", "1e3", ".1", "1.", " 1", "1.0000000000000000001", "1000000000000000000000000000000000000000000000000000000000000000000000000000000"})
    void rejectsInvalidAmounts(String amount) {
        assertThrows(InvalidInputException.class, () -> EthAmount.fromEth(amount));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0x0000000000000000000000000000000000000000", "0x1234", "recipient", "0xgggggggggggggggggggggggggggggggggggggggg"})
    void rejectsInvalidRecipients(String address) {
        assertThrows(InvalidInputException.class, () -> new EthereumAddress(address));
    }

    @Test
    void fingerprintUsesCanonicalAmountAndAddress() {
        var first = new PayoutRequest("merchant", new EthereumAddress(ADDRESS), EthAmount.fromEth("1.0"), "key1");
        var retry = new PayoutRequest("merchant", new EthereumAddress("0x" + "AB".repeat(20)), EthAmount.fromEth("01.00"), "key2");
        assertEquals(first.fingerprint(), retry.fingerprint());
        assertNotEquals(first.fingerprint(), new PayoutRequest("other", first.recipientAddress(), first.amount(), "key1").fingerprint());
        assertThrows(InvalidInputException.class, () -> new PayoutRequest(" ", first.recipientAddress(), first.amount(), "key"));
        assertThrows(InvalidInputException.class, () -> new PayoutRequest("merchant", first.recipientAddress(), first.amount(), ""));
    }

    @Test
    void finalizedPayoutCannotBeSentAgain() {
        assertTrue(PayoutStatus.REQUESTED.canTransitionTo(PayoutStatus.AWAITING_CONFIRMATION));
        assertTrue(PayoutStatus.AWAITING_CONFIRMATION.canTransitionTo(PayoutStatus.SETTLED));
        assertTrue(PayoutStatus.SETTLED.canTransitionTo(PayoutStatus.REQUIRES_REVIEW));
        assertFalse(PayoutStatus.SETTLED.canTransitionTo(PayoutStatus.REQUESTED));
        assertFalse(PayoutStatus.FAILED.canTransitionTo(PayoutStatus.AWAITING_CONFIRMATION));
        assertFalse(PayoutStatus.REQUIRES_REVIEW.canTransitionTo(PayoutStatus.SETTLED));
    }
}
