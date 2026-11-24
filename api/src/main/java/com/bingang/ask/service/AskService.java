package com.bingang.ask.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.bingang.ask.service.AskCondition.Rejection;
import com.bingang.building.entity.Building;
import com.bingang.building.service.BuildingService;
import com.bingang.common.config.ClockConfig;
import com.bingang.common.exception.BusinessException;
import com.bingang.common.exception.error.ErrorCode;
import com.bingang.common.monitoring.UsageMetrics;
import com.bingang.room.service.AvailabilityService;
import com.bingang.room.service.RangeAvailability;

/**
 * 물어보기 (#74): AI가 질문을 조건으로 바꾸고, 빈 강의실은 기존 판정 로직으로 구간 내내 비는 곳을 찾는다.
 * AI 결과는 그대로 믿지 않고 여기서 범위를 다시 맞춘다 (09:00~21:00, 오늘부터 2주, 보이는 건물만)
 */
@Service
@RequiredArgsConstructor
public class AskService {

	private static final Logger log = LoggerFactory.getLogger(AskService.class);

	static final LocalTime OPEN = LocalTime.of(9, 0);

	static final LocalTime CLOSE = LocalTime.of(21, 0);

	static final int MAX_DAYS_AHEAD = 14;

	/** 시간을 말하지 않았을 때 보여 줄 빈 시간대의 최소 길이 */
	static final Duration MIN_SLOT = Duration.ofHours(1);

	private final QuestionParser parser;

	private final AskRateLimiter rateLimiter;

	private final BuildingService buildingService;

	private final AvailabilityService availabilityService;

	private final UsageMetrics usageMetrics;

	private final Clock clock;

	/** 빈 강의실 질문이 아니거나 해석하지 못하면 understood=false인 결과 */
	public AskResult ask(String question, String client) {
		if (!parser.available()) {
			usageMetrics.asked("unavailable");
			throw new BusinessException(ErrorCode.ASK_UNAVAILABLE);
		}
		if (!rateLimiter.tryAcquire(client)) {
			usageMetrics.asked("limited");
			throw new BusinessException(ErrorCode.ASK_RATE_LIMITED);
		}
		ZonedDateTime now = ZonedDateTime.now(clock.withZone(ClockConfig.SEOUL));
		List<Building> visible = buildingService.findVisible();

		AskCondition raw;
		try {
			raw = parser.parse(question.strip(), now, visible);
		}
		catch (AskUnavailableException e) {
			log.warn("물어보기 AI 호출 실패: {}", e.getMessage());
			usageMetrics.asked("failed");
			throw new BusinessException(ErrorCode.ASK_UNAVAILABLE);
		}

		AskCondition condition = normalize(raw, now);
		if (!condition.understood()) {
			usageMetrics.asked("not_understood");
			return AskResult.rejected(condition.rejection());
		}
		Map<String, Building> byCode = visible.stream().collect(Collectors.toMap(Building::getCode, Function.identity()));
		// 목록에 없는 코드는 버린다. 하나도 안 남으면 전체 건물에서 찾는다
		List<Building> named = condition.buildingCodes().stream().distinct().map(byCode::get).filter(Objects::nonNull)
			.toList();
		List<Building> targets = named.isEmpty() ? visible : named;
		RangeAvailability rooms = condition.wholeDay()
				? availabilityService.freeSlots(condition.date(), condition.start(), condition.end(), MIN_SLOT, targets)
				: availabilityService.freeBetween(condition.date(), condition.start(), condition.end(), targets);
		usageMetrics.asked("ok");
		return new AskResult(true, rooms);
	}

	/**
	 * AI가 준 조건의 빈칸을 채우고 범위를 맞춘다. 날짜 없음 → 오늘. 운영 시간(09:00~21:00) 밖은 잘라 낸다.
	 * 시각을 하나도 말하지 않았으면: 다른 날은 그날 하루(09:00~21:00)의 빈 시간대(wholeDay), 오늘은 지금부터 2시간.
	 * 시작만 있으면 시작 + 2시간, 끝만 있으면 오늘은 지금부터·다른 날은 09:00부터
	 */
	static AskCondition normalize(AskCondition c, ZonedDateTime now) {
		if (!c.understood()) {
			return c;
		}
		LocalDate today = now.toLocalDate();
		LocalDate date = c.date() == null ? today : c.date();
		if (date.isBefore(today) || date.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
			return AskCondition.rejected(Rejection.OUT_OF_RANGE);
		}
		if (c.start() != null && c.end() != null && !c.start().isBefore(c.end())) {
			return AskCondition.rejected(Rejection.INVALID_TIME);
		}
		if (c.start() == null && c.end() == null && !date.equals(today)) {
			return new AskCondition(true, date, OPEN, CLOSE, c.buildingCodes(), true);
		}
		LocalTime start = c.start();
		if (start == null) {
			start = date.equals(today) ? now.toLocalTime().withSecond(0).withNano(0) : OPEN;
		}
		LocalTime end = c.end() == null ? start.plusHours(2) : c.end();
		if (end.isBefore(start) && c.end() == null) {
			end = CLOSE; // plusHours가 자정을 넘긴 경우
		}
		start = start.isBefore(OPEN) ? OPEN : start;
		end = end.isAfter(CLOSE) ? CLOSE : end;
		if (!start.isBefore(end)) {
			return AskCondition.rejected(Rejection.OUT_OF_HOURS);
		}
		return new AskCondition(true, date, start, end, c.buildingCodes());
	}

}
