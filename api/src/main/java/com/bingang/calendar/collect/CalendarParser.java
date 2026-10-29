package com.bingang.calendar.collect;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** 학사일정 페이지의 {@code var calData = [...];}를 읽어 일정 목록으로 바꾼다 */
@Component
public class CalendarParser {

	private static final Logger log = LoggerFactory.getLogger(CalendarParser.class);

	private static final Pattern CAL_DATA = Pattern.compile("calData\\s*=\\s*(\\[.*?\\])\\s*;", Pattern.DOTALL);

	// 공휴일수업대체지정일(09.24.(목) 추석연휴일) → 목
	private static final Pattern SUBSTITUTE = Pattern
		.compile("^공휴일수업대체지정일\\(\\s*\\d{1,2}\\.\\d{1,2}\\.?\\s*\\(([월화수목금토일])\\)");

	private static final Map<String, DayOfWeek> DAYS = Map.of("월", DayOfWeek.MONDAY, "화", DayOfWeek.TUESDAY, "수",
			DayOfWeek.WEDNESDAY, "목", DayOfWeek.THURSDAY, "금", DayOfWeek.FRIDAY, "토", DayOfWeek.SATURDAY, "일",
			DayOfWeek.SUNDAY);

	// 스크립트 안의 값이라 따옴표 없는 키, 작은따옴표, 끝 쉼표도 받아들인다
	private final JsonMapper mapper = JsonMapper.builder()
		.enable(JsonReadFeature.ALLOW_UNQUOTED_PROPERTY_NAMES, JsonReadFeature.ALLOW_SINGLE_QUOTES,
				JsonReadFeature.ALLOW_TRAILING_COMMA)
		.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
		.build();

	public List<CalendarItem> parse(String html) {
		Matcher matcher = CAL_DATA.matcher(html);
		if (!matcher.find()) {
			throw new IllegalStateException("학사일정 페이지에서 calData를 찾을 수 없습니다. 페이지 구조가 바뀌었을 수 있습니다.");
		}
		RawEvent[] events = mapper.readValue(matcher.group(1), RawEvent[].class);
		return Arrays.stream(events).map(this::toItem).filter(Objects::nonNull).toList();
	}

	static DayOfWeek substituteDay(String title) {
		Matcher matcher = SUBSTITUTE.matcher(title);
		return matcher.find() ? DAYS.get(matcher.group(1)) : null;
	}

	private CalendarItem toItem(RawEvent event) {
		if (event.articleNo() == null || isBlank(event.startDt()) || isBlank(event.text())) {
			log.warn("필수 값이 없는 학사 일정을 건너뜁니다: {}", event);
			return null;
		}
		LocalDate start = LocalDate.parse(event.startDt().trim());
		LocalDate end = isBlank(event.endDt()) ? start : LocalDate.parse(event.endDt().trim());
		String title = event.text().trim();
		return new CalendarItem(event.articleNo(), start, end, title, substituteDay(title));
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RawEvent(Long articleNo, String startDt, String endDt, String text) {
	}

}
