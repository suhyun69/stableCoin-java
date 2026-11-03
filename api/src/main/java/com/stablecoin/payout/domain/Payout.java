package com.stablecoin.payout.domain;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * 가맹점이 요청한 지급. 금액은 최소 단위(wei) 정수다.
 * <p>
 * requestFingerprint는 요청 내용(가맹점, 받는 주소, 금액)의 해시다. 같은 멱등성 키로 다른 내용이 오면 거부하는 데 쓴다.
 */
public record Payout(UUID id, String merchantId, String recipientAddress, BigInteger amountWei,
		String idempotencyKey, String requestFingerprint, PayoutStatus status) {

	public static Payout requested(String merchantId, String recipientAddress, BigInteger amountWei,
			String idempotencyKey) {
		return new Payout(UUID.randomUUID(), merchantId, recipientAddress, amountWei, idempotencyKey,
				fingerprint(merchantId, recipientAddress, amountWei), PayoutStatus.REQUESTED);
	}

	/**
	 * 요청 내용의 SHA-256 (hex). 주소는 대소문자를 구분하지 않는다.
	 */
	public static String fingerprint(String merchantId, String recipientAddress, BigInteger amountWei) {
		String canonical = merchantId + "|" + recipientAddress.toLowerCase(Locale.ROOT) + "|" + amountWei;
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
