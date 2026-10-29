package com.bingang.common.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 판정·수집 시각은 서버 시간대와 상관없이 항상 한국 시간으로 계산한다
@Configuration
public class ClockConfig {

	public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	@Bean
	public Clock clock() {
		return Clock.system(SEOUL);
	}

}
