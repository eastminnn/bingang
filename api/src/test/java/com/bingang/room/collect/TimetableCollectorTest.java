package com.bingang.room.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;

import com.bingang.TestcontainersConfiguration;
import com.bingang.building.repository.BuildingRepository;
import com.bingang.room.entity.Room;
import com.bingang.room.repository.RoomRepository;
import com.bingang.room.repository.RoomScheduleRepository;

import jakarta.persistence.EntityManager;

@DataJpaTest(properties = { "bingang.timetable.year=2026", "bingang.timetable.semester-code=U0320021",
		"bingang.timetable.request-interval=0s" })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, TimetableCollector.class })
class TimetableCollectorTest {

	@MockitoBean
	YuPortalClient client;

	@Autowired
	TimetableCollector collector;

	@Autowired
	BuildingRepository buildings;

	@Autowired
	RoomRepository rooms;

	@Autowired
	RoomScheduleRepository schedules;

	@Autowired
	EntityManager em;

	private static List<FreeRoom> rooms(String building, int from, int to) {
		return IntStream.rangeClosed(from, to)
			.mapToObj(n -> new FreeRoom(building, building.equals("B03") ? "인문관" : "천마관", String.valueOf(n), null,
					null, 40))
			.toList();
	}

	/** B03-101은 월요일 1A·1B에 사용 중, 나머지는 항상 비어 있는 학교 시스템 */
	private YuPortalClient.Session portal(List<FreeRoom> all) {
		YuPortalClient.Session session = mock(YuPortalClient.Session.class);
		given(session.freeRooms(anyString(), anyString(), anyInt(), any())).willAnswer(invocation -> {
			int day = invocation.getArgument(2);
			Slot slot = invocation.getArgument(3);
			List<FreeRoom> free = new ArrayList<>(all);
			if (day == 1 && (slot.label().equals("1A") || slot.label().equals("1B"))) {
				free.removeIf(r -> r.key().equals("B03-101"));
			}
			return free;
		});
		return session;
	}

	@Test
	void 수집하면_건물_강의실_시간표를_저장한다() {
		List<FreeRoom> all = new ArrayList<>(rooms("B03", 101, 106));
		all.addAll(rooms("C03", 201, 206));
		YuPortalClient.Session session = portal(all);
		given(client.login()).willReturn(session);

		TimetableCollector.Result result = collector.collect();
		em.flush();
		em.clear();

		assertThat(result).isEqualTo(new TimetableCollector.Result(2, 12, 1, 0));
		assertThat(buildings.findByCode("B03").orElseThrow().getName()).isEqualTo("인문관");
		Room r101 = rooms.findAllWithBuilding()
			.stream()
			.filter(r -> r.getNumber().equals("101"))
			.findFirst()
			.orElseThrow();
		assertThat(r101.getFloor()).isEqualTo((short) 1);
		assertThat(schedules.findAll()).singleElement().satisfies(s -> {
			assertThat(s.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
			assertThat(s.getStartTime()).isEqualTo(LocalTime.of(9, 0));
			assertThat(s.getEndTime()).isEqualTo(LocalTime.of(10, 0));
		});
	}

	@Test
	void 다시_수집하면_시간표를_교체하고_사라진_강의실은_지운다() {
		List<FreeRoom> all = new ArrayList<>(rooms("B03", 101, 106));
		all.addAll(rooms("C03", 201, 206));
		YuPortalClient.Session first = portal(all);
		given(client.login()).willReturn(first);
		collector.collect();
		em.flush();

		List<FreeRoom> fewer = new ArrayList<>(all);
		fewer.removeIf(r -> r.key().equals("C03-206"));
		YuPortalClient.Session second = portal(fewer);
		given(client.login()).willReturn(second);
		TimetableCollector.Result result = collector.collect();
		em.flush();
		em.clear();

		assertThat(result.removedRooms()).isEqualTo(1);
		assertThat(rooms.count()).isEqualTo(11);
		assertThat(schedules.count()).isEqualTo(1);
	}

	@Test
	void 조회_중에_실패하면_기존_데이터를_유지한다() {
		List<FreeRoom> all = new ArrayList<>(rooms("B03", 101, 106));
		all.addAll(rooms("C03", 201, 206));
		YuPortalClient.Session ok = portal(all);
		given(client.login()).willReturn(ok);
		collector.collect();
		em.flush();

		YuPortalClient.Session broken = mock(YuPortalClient.Session.class);
		given(broken.freeRooms(anyString(), anyString(), anyInt(), any()))
			.willThrow(new ResourceAccessException("timeout"));
		given(client.login()).willReturn(broken);

		assertThatThrownBy(collector::collect).isInstanceOf(ResourceAccessException.class);
		assertThat(rooms.count()).isEqualTo(12);
		assertThat(schedules.count()).isEqualTo(1);
	}

	@Test
	void 호수로_층을_정한다() {
		assertThat(TimetableCollector.floorOf("201")).isEqualTo((short) 2);
		assertThat(TimetableCollector.floorOf("1012")).isEqualTo((short) 10);
		assertThat(TimetableCollector.floorOf("B101")).isEqualTo((short) -1);
		assertThat(TimetableCollector.floorOf("강당")).isNull();
	}

}
