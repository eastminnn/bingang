package com.bingang.room.collect;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 빈강의실조회의 시간 칸. code는 요청의 sbDate 뒤 두 자리(00 = 08:00, 01 = 1A … 18 = 9B, 21 = 11교시 … 25 = 15교시).
 * 1A~9B는 30분 단위, 11~15교시는 학교 시간표의 야간 교시 시간을 따른다.
 * 19·20번과 26번 이후는 항상 모든 강의실이 비어 있어 쓰지 않는다 (2026-10-29 실제 조회로 확인).
 */
public record Slot(String code, String label, LocalTime start, LocalTime end) {

	public static final List<Slot> ALL = all();

	private static List<Slot> all() {
		List<Slot> slots = new ArrayList<>();
		slots.add(new Slot("00", "0", LocalTime.of(8, 0), LocalTime.of(9, 0)));
		for (int i = 0; i < 18; i++) {
			LocalTime start = LocalTime.of(9, 0).plusMinutes(30L * i);
			String label = (i / 2 + 1) + (i % 2 == 0 ? "A" : "B");
			slots.add(new Slot("%02d".formatted(i + 1), label, start, start.plusMinutes(30)));
		}
		slots.add(new Slot("21", "11", LocalTime.of(18, 0), LocalTime.of(18, 50)));
		slots.add(new Slot("22", "12", LocalTime.of(18, 55), LocalTime.of(19, 40)));
		slots.add(new Slot("23", "13", LocalTime.of(19, 40), LocalTime.of(20, 30)));
		slots.add(new Slot("24", "14", LocalTime.of(20, 35), LocalTime.of(21, 20)));
		slots.add(new Slot("25", "15", LocalTime.of(21, 20), LocalTime.of(22, 5)));
		return List.copyOf(slots);
	}

}
