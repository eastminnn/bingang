package com.bingang.ask.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.bingang.common.config.ClockConfig;

/**
 * 클라이언트(IP)마다 하루 요청 수를 제한한다. 로그인이 없어 크레딧이 한 사람에게 소진되지 않게 막는 용도.
 * 메모리에만 두므로 재시작·블루-그린 전환 때 초기화된다.
 */
@Component
public class AskRateLimiter {

	private final Clock clock;

	private final int dailyLimit;

	private volatile LocalDate day;

	private final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();

	public AskRateLimiter(Clock clock, @Value("${bingang.ask.daily-limit:20}") int dailyLimit) {
		this.clock = clock;
		this.dailyLimit = dailyLimit;
	}

	/** 한도 안이면 1을 세고 true */
	public boolean tryAcquire(String client) {
		LocalDate today = LocalDate.now(clock.withZone(ClockConfig.SEOUL));
		if (!today.equals(day)) {
			synchronized (this) {
				if (!today.equals(day)) {
					counts.clear();
					day = today;
				}
			}
		}
		return counts.computeIfAbsent(client, k -> new AtomicInteger()).incrementAndGet() <= dailyLimit;
	}

}
