package com.bingang.room.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class DayScheduleTest {

	private static Session session(String start, String end) {
		return new Session(LocalTime.parse(start), LocalTime.parse(end));
	}

	private static DaySchedule schedule(Session... sessions) {
		return DaySchedule.of(List.of(sessions));
	}

	@Test
	void 수업이_없으면_하루_종일_비어_있다() {
		RoomState state = schedule().stateAt(LocalTime.of(11, 0));

		assertThat(state).isEqualTo(new RoomState(false, null, null));
	}

	@Test
	void 수업_시작_정각은_사용_중이다() {
		RoomState state = schedule(session("09:00", "10:30")).stateAt(LocalTime.of(9, 0));

		assertThat(state.busy()).isTrue();
		assertThat(state.freeAt()).isEqualTo(LocalTime.of(10, 30));
	}

	@Test
	void 수업_끝나는_정각은_비어_있다() {
		RoomState state = schedule(session("09:00", "10:30")).stateAt(LocalTime.of(10, 30));

		assertThat(state).isEqualTo(new RoomState(false, null, null));
	}

	@Test
	void 비어_있으면_다음_수업_시작까지가_빈_시간이다() {
		RoomState state = schedule(session("09:00", "10:15"), session("11:30", "14:00")).stateAt(LocalTime.of(11, 10));

		assertThat(state).isEqualTo(new RoomState(false, LocalTime.of(11, 30), null));
	}

	@Test
	void 첫_수업_전에도_다음_수업_시작까지_비어_있다() {
		RoomState state = schedule(session("13:00", "14:00")).stateAt(LocalTime.of(8, 0));

		assertThat(state.freeUntil()).isEqualTo(LocalTime.of(13, 0));
	}

	@Test
	void 마지막_수업_뒤에는_오늘_끝까지_비어_있다() {
		RoomState state = schedule(session("09:00", "10:00")).stateAt(LocalTime.of(15, 0));

		assertThat(state).isEqualTo(new RoomState(false, null, null));
	}

	@Test
	void 이어진_수업은_마지막_수업이_끝나야_빈다() {
		// 교시 단위로 수집하므로 연강은 맞닿은 여러 칸으로 들어온다
		RoomState state = schedule(session("09:00", "09:30"), session("09:30", "10:00"), session("10:00", "10:30"))
			.stateAt(LocalTime.of(9, 10));

		assertThat(state.freeAt()).isEqualTo(LocalTime.of(10, 30));
	}

	@Test
	void 겹치는_수업도_하나로_합친다() {
		DaySchedule schedule = schedule(session("10:00", "12:00"), session("09:00", "10:30"), session("13:00", "14:00"));

		assertThat(schedule.sessions()).containsExactly(session("09:00", "12:00"), session("13:00", "14:00"));
		assertThat(schedule.stateAt(LocalTime.of(9, 30)).freeAt()).isEqualTo(LocalTime.of(12, 0));
	}

	@Test
	void 사이가_조금이라도_비면_따로_본다() {
		DaySchedule schedule = schedule(session("18:00", "18:50"), session("18:55", "19:40"));

		assertThat(schedule.stateAt(LocalTime.of(18, 30)).freeAt()).isEqualTo(LocalTime.of(18, 50));
		assertThat(schedule.stateAt(LocalTime.of(18, 52)).freeUntil()).isEqualTo(LocalTime.of(18, 55));
	}

}
