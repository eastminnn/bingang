package com.bingang.calendar.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CalendarParserTest {

	private final CalendarParser parser = new CalendarParser();

	@Test
	void 페이지의_calData를_일정_목록으로_읽는다() {
		List<CalendarItem> items = parser.parse(fixture());

		// 날짜 없는 항목 1건은 건너뛴다
		assertThat(items).hasSize(7);
		assertThat(items.getFirst())
			.isEqualTo(new CalendarItem(90001L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), "개강", null));
		assertThat(items).filteredOn(i -> i.sourceId() == 90003L)
			.singleElement()
			.satisfies(i -> assertThat(i.endDate()).isEqualTo(LocalDate.of(2026, 10, 26)));
	}

	@Test
	void 끝날짜가_없으면_시작일과_같은_하루_일정으로_본다() {
		List<CalendarItem> items = parser.parse(fixture());

		assertThat(items).filteredOn(i -> i.sourceId() == 90004L || i.sourceId() == 90005L)
			.allSatisfy(i -> assertThat(i.endDate()).isEqualTo(i.startDate()));
	}

	@Test
	void 보강일은_대체_요일을_채우고_나머지는_비운다() {
		List<CalendarItem> items = parser.parse(fixture());

		assertThat(items).extracting(CalendarItem::sourceId, CalendarItem::substituteDay)
			.contains(tuple(90004L, DayOfWeek.THURSDAY),
					tuple(90005L, DayOfWeek.FRIDAY),
					tuple(90006L, DayOfWeek.MONDAY),
					tuple(90003L, null));
	}

	@Test
	void 제목_앞뒤_공백을_지운다() {
		assertThat(parser.parse(fixture())).extracting(CalendarItem::title).contains("방학");
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "공휴일수업대체지정일(09.24.(목) 추석연휴일)|THURSDAY",
			"공휴일수업대체지정일(10.09.(금) 한글날)|FRIDAY", "공휴일수업대체지정일( 5.5 (화) 어린이날)|TUESDAY" })
	void 보강일_제목에서_요일을_읽는다(String title, DayOfWeek expected) {
		assertThat(CalendarParser.substituteDay(title)).isEqualTo(expected);
	}

	@Test
	void 보강일이_아니면_요일이_없다() {
		assertThat(CalendarParser.substituteDay("중간시험")).isNull();
		assertThat(CalendarParser.substituteDay("공휴일수업대체지정일 안내")).isNull();
	}

	@Test
	void 따옴표_없는_키와_작은따옴표도_읽는다() {
		String html = "<script>var calData = [{startDt:'2026-09-01', text:'개강', articleNo:1},];</script>";

		assertThat(parser.parse(html)).singleElement().satisfies(i -> assertThat(i.title()).isEqualTo("개강"));
	}

	@Test
	void calData가_없으면_예외를_던진다() {
		assertThatThrownBy(() -> parser.parse("<html><body>점검 중입니다</body></html>"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("calData");
	}

	static String fixture() {
		try (InputStream in = CalendarParserTest.class.getResourceAsStream("/fixtures/calendar.html")) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

}
