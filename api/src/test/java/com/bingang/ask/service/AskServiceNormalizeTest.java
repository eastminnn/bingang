package com.bingang.ask.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.bingang.ask.service.AskCondition.Rejection;
import com.bingang.common.config.ClockConfig;

class AskServiceNormalizeTest {

	// 2026-10-28(수) 11:10
	private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 10, 28, 11, 10, 30, 0, ClockConfig.SEOUL);

	private static final LocalDate TODAY = NOW.toLocalDate();

	private static AskCondition understood(LocalDate date, String start, String end) {
		return new AskCondition(true, date, start == null ? null : LocalTime.parse(start),
				end == null ? null : LocalTime.parse(end), List.of("E21"));
	}

	@Test
	void 날짜와_시각이_모두_있으면_그대로_쓴다() {
		AskCondition c = AskService.normalize(understood(TODAY.plusDays(1), "13:00", "15:00"), NOW);

		assertThat(c).isEqualTo(understood(TODAY.plusDays(1), "13:00", "15:00"));
	}

	@Test
	void 아무것도_없으면_오늘_지금부터_2시간이다() {
		AskCondition c = AskService.normalize(understood(null, null, null), NOW);

		assertThat(c).isEqualTo(understood(TODAY, "11:10", "13:10"));
	}

	@Test
	void 다른_날에_시각을_말하지_않으면_그날_하루의_빈_시간대를_찾는다() {
		AskCondition c = AskService.normalize(understood(TODAY.plusDays(2), null, null), NOW);

		assertThat(c).isEqualTo(new AskCondition(true, TODAY.plusDays(2), LocalTime.of(9, 0), LocalTime.of(21, 0),
				List.of("E21"), true));
	}

	@Test
	void 끝만_말하면_다른_날은_09시부터_오늘은_지금부터다() {
		assertThat(AskService.normalize(understood(TODAY.plusDays(1), null, "12:00"), NOW))
			.isEqualTo(understood(TODAY.plusDays(1), "09:00", "12:00"));
		assertThat(AskService.normalize(understood(TODAY, null, "15:00"), NOW))
			.isEqualTo(understood(TODAY, "11:10", "15:00"));
	}

	@Test
	void 운영_시간_밖은_09시_21시로_자른다() {
		AskCondition c = AskService.normalize(understood(TODAY, "07:00", "23:00"), NOW);

		assertThat(c).isEqualTo(understood(TODAY, "09:00", "21:00"));
	}

	@Test
	void 지나간_날짜나_2주_뒤는_해석하지_못한_것으로_본다() {
		assertThat(AskService.normalize(understood(TODAY.minusDays(1), "13:00", "15:00"), NOW).rejection())
			.isEqualTo(Rejection.OUT_OF_RANGE);
		assertThat(AskService.normalize(understood(TODAY.plusDays(15), "13:00", "15:00"), NOW).rejection())
			.isEqualTo(Rejection.OUT_OF_RANGE);
	}

	@Test
	void 시작이_끝보다_늦거나_운영_시간이_끝났으면_해석하지_못한_것으로_본다() {
		assertThat(AskService.normalize(understood(TODAY, "15:00", "13:00"), NOW).rejection())
			.isEqualTo(Rejection.INVALID_TIME);
		assertThat(AskService.normalize(understood(TODAY, "21:30", null), NOW).rejection())
			.isEqualTo(Rejection.OUT_OF_HOURS);
		assertThat(AskService.normalize(understood(TODAY, "22:00", "23:00"), NOW).rejection())
			.isEqualTo(Rejection.OUT_OF_HOURS);
	}

}
