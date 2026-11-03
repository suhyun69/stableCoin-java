package com.stablecoin.chain.application;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

/**
 * 지갑별 전송 잠금. 같은 지갑에서 nonce를 정하고 서명해 보내는 동안 다른 전송이 끼어들어 같은 nonce를 쓰지 않게 한다.
 * 다른 지갑끼리는 서로 기다리지 않는다. 이 인스턴스 안에서만 보장된다.
 */
@Component
public class WalletSendLocks {

	private final ConcurrentMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

	public <T> T withLock(String address, Supplier<T> action) {
		ReentrantLock lock = locks.computeIfAbsent(address.toLowerCase(Locale.ROOT), key -> new ReentrantLock());
		lock.lock();
		try {
			return action.get();
		}
		finally {
			lock.unlock();
		}
	}

}
