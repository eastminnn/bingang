package com.bingang.room.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.bingang.room.collect.TimetableAssembler.BusySession;
import com.bingang.room.collect.TimetableAssembler.SlotKey;
import com.bingang.room.collect.TimetableAssembler.Timetable;

class TimetableAssemblerTest {

	// 강의실 12개: B03-101 ~ B03-112
	private static final List<FreeRoom> ROOMS = IntStream.rangeClosed(101, 112)
		.mapToObj(n -> new FreeRoom("B03", "인문관", String.valueOf(n), null, null, 40))
		.toList();

	/** 월요일 하루를 만든다. busy: 호수 → 사용 중인 칸 라벨 */
	private static Map<SlotKey, List<FreeRoom>> monday(Map<String, Set<String>> busy) {
		Map<SlotKey, List<FreeRoom>> free = new LinkedHashMap<>();
		for (Slot slot : Slot.ALL) {
			List<FreeRoom> list = new ArrayList<>();
			for (FreeRoom room : ROOMS) {
				if (!busy.getOrDefault(room.number(), Set.of()).contains(slot.label())) {
					list.add(room);
				}
			}
			free.put(new SlotKey(DayOfWeek.MONDAY, slot), list);
		}
		return free;
	}

	private static LocalTime t(String time) {
		return LocalTime.parse(time);
	}

	@Test
	void 빈_강의실을_뒤집어_사용_중인_시간으로_만든다() {
		Timetable timetable = TimetableAssembler.assemble(monday(Map.of("101", Set.of("1A", "1B", "2A", "2B"))));

		assertThat(timetable.rooms()).hasSize(12);
		assertThat(timetable.sessions())
			.containsExactly(new BusySession("B03-101", DayOfWeek.MONDAY, t("09:00"), t("11:00")));
	}

	@Test
	void 사이가_비면_따로_나눈다() {
		Timetable timetable = TimetableAssembler.assemble(monday(Map.of("102", Set.of("1A", "3A", "3B"))));

		assertThat(timetable.sessions()).containsExactly(
				new BusySession("B03-102", DayOfWeek.MONDAY, t("09:00"), t("09:30")),
				new BusySession("B03-102", DayOfWeek.MONDAY, t("11:00"), t("12:00")));
	}

	@Test
	void 야간_교시_사이_5분은_이어_붙이지_않는다() {
		Timetable timetable = TimetableAssembler.assemble(monday(Map.of("103", Set.of("9B", "11", "12", "13"))));

		// 9B(17:30-18:00) + 11(18:00-18:50)은 이어지고, 12(18:55-)는 5분 뒤라 따로, 13(19:40-)은 12에 이어진다
		assertThat(timetable.sessions()).containsExactly(
				new BusySession("B03-103", DayOfWeek.MONDAY, t("17:30"), t("18:50")),
				new BusySession("B03-103", DayOfWeek.MONDAY, t("18:55"), t("20:30")));
	}

	@Test
	void 하루_끝까지_사용_중이어도_닫는다() {
		Timetable timetable = TimetableAssembler.assemble(monday(Map.of("104", Set.of("14", "15"))));

		assertThat(timetable.sessions())
			.containsExactly(new BusySession("B03-104", DayOfWeek.MONDAY, t("20:35"), t("22:05")));
	}

	@Test
	void 모든_칸의_결과가_같으면_시간이_무시된_것으로_보고_실패한다() {
		assertThatThrownBy(() -> TimetableAssembler.assemble(monday(Map.of())))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("시간 조건");
	}

	@Test
	void 강의실이_너무_적으면_실패한다() {
		Map<SlotKey, List<FreeRoom>> free = new LinkedHashMap<>();
		free.put(new SlotKey(DayOfWeek.MONDAY, Slot.ALL.get(0)), ROOMS.subList(0, 3));

		assertThatThrownBy(() -> TimetableAssembler.assemble(free)).isInstanceOf(IllegalStateException.class);
	}

}
