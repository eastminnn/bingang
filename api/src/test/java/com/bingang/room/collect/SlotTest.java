package com.bingang.room.collect;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;

class SlotTest {

	@Test
	void 하루는_24칸이고_야간_교시는_21번부터다() {
		assertThat(Slot.ALL).hasSize(24);
		assertThat(Slot.ALL).extracting(Slot::code).doesNotHaveDuplicates().startsWith("00", "01").endsWith("21", "22", "23", "24", "25");
		assertThat(Slot.ALL).extracting(Slot::code).doesNotContain("19", "20");
	}

	@Test
	void 주간_교시는_30분_단위로_빈틈없이_이어진다() {
		assertThat(Slot.ALL.get(1)).isEqualTo(new Slot("01", "1A", LocalTime.of(9, 0), LocalTime.of(9, 30)));
		assertThat(Slot.ALL.get(18)).isEqualTo(new Slot("18", "9B", LocalTime.of(17, 30), LocalTime.of(18, 0)));
		for (int i = 1; i < 19; i++) {
			assertThat(Slot.ALL.get(i).start()).isEqualTo(Slot.ALL.get(i - 1).end());
		}
	}

	@Test
	void 야간_교시는_학교_시간표를_따른다() {
		assertThat(Slot.ALL.get(19)).isEqualTo(new Slot("21", "11", LocalTime.of(18, 0), LocalTime.of(18, 50)));
		assertThat(Slot.ALL.get(23)).isEqualTo(new Slot("25", "15", LocalTime.of(21, 20), LocalTime.of(22, 5)));
	}

}
