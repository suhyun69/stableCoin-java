package com.example.demo.payout.domain;

import com.example.demo.common.exception.InvalidInputException;
import java.math.BigDecimal;
import java.math.BigInteger;

public record EthAmount(BigInteger wei) {
    private static final BigInteger MAX_VALUE = BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE);

    public EthAmount {
        if (wei == null || wei.signum() <= 0 || wei.compareTo(MAX_VALUE) > 0) {
            throw new InvalidInputException("amount must be a positive uint256 wei value");
        }
    }

    public static EthAmount fromEth(String amount) {
        if (amount == null || amount.length() > 100 || !amount.matches("[0-9]+(\\.[0-9]{1,18})?")) {
            throw new InvalidInputException("amount must be an ETH decimal string with at most 18 decimal places");
        }
        return new EthAmount(new BigDecimal(amount).movePointRight(18).toBigIntegerExact());
    }

    public String toEth() {
        return new BigDecimal(wei, 18).stripTrailingZeros().toPlainString();
    }
}
