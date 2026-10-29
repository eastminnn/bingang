package com.bingang.room.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import com.bingang.TestcontainersConfiguration;
import com.bingang.building.entity.Building;
import com.bingang.building.repository.BuildingRepository;
import com.bingang.calendar.entity.AcademicEvent;
import com.bingang.calendar.repository.AcademicEventRepository;
import com.bingang.calendar.service.AcademicCalendarService;
import com.bingang.room.entity.Room;
import com.bingang.room.entity.RoomSchedule;
import com.bingang.room.repository.RoomRepository;
import com.bingang.room.repository.RoomScheduleRepository;

import jakarta.persistence.EntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, AvailabilityService.class, AcademicCalendarService.class })
class AvailabilityServiceTest {

	@Autowired
	AvailabilityService availability;

	@Autowired
	BuildingRepository buildings;

	@Autowired
	RoomRepository rooms;

	@Autowired
	RoomScheduleRepository schedules;

	@Autowired
	AcademicEventRepository events;

	@Autowired
	EntityManager em;

	Building humanities;

	Building cheonma;

	@BeforeEach
	void setUp() {
		humanities = buildings.save(new Building("B03", "인문관"));
		cheonma = buildings.save(new Building("C03", "천마관"));
		Room r201 = rooms.save(new Room(humanities, "201", (short) 2));
		Room r105 = rooms.save(new Room(humanities, "105", (short) 1));
		rooms.save(new Room(cheonma, "301", (short) 3));

		// 수요일: 201은 09:00-10:15, 11:30-14:00 / 105는 10:30-13:00
		schedules.save(new RoomSchedule(r201, DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(10, 15)));
		schedules.save(new RoomSchedule(r201, DayOfWeek.WEDNESDAY, LocalTime.of(11, 30), LocalTime.of(14, 0)));
		schedules.save(new RoomSchedule(r105, DayOfWeek.WEDNESDAY, LocalTime.of(10, 30), LocalTime.of(13, 0)));
		// 목요일: 201은 하루 종일
		schedules.save(new RoomSchedule(r201, DayOfWeek.THURSDAY, LocalTime.of(9, 0), LocalTime.of(18, 0)));

		LocalDate dec8 = LocalDate.of(2026, 12, 8);
		events.save(new AcademicEvent(1L, dec8, dec8, "공휴일수업대체지정일(09.24.(목) 추석연휴일)", DayOfWeek.THURSDAY,
				Instant.now()));
		em.flush();
		em.clear();
	}

	private static Instant seoul(String dateTime) {
		return OffsetDateTime.parse(dateTime + "+09:00").toInstant();
	}

	@Test
	void 건물의_강의실별_상태를_호수_순서로_돌려준다() {
		// 2026-10-28은 수요일
		BuildingAvailability result = availability.ofBuilding(humanities, seoul("2026-10-28T11:10:00"));

		assertThat(result.dayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);
		assertThat(result.rooms()).extracting(s -> s.room().getNumber()).containsExactly("105", "201");

		RoomStatus r105 = result.rooms().get(0);
		assertThat(r105.state()).isEqualTo(new RoomState(true, null, LocalTime.of(13, 0)));
		assertThat(r105.sessions()).containsExactly(new Session(LocalTime.of(10, 30), LocalTime.of(13, 0)));

		RoomStatus r201 = result.rooms().get(1);
		assertThat(r201.state()).isEqualTo(new RoomState(false, LocalTime.of(11, 30), null));
		assertThat(r201.sessions()).hasSize(2);
	}

	@Test
	void UTC로_들어온_시각도_한국_시간으로_판정한다() {
		// 2026-10-28T02:10Z = 한국 11:10 (수)
		BuildingAvailability result = availability.ofBuilding(humanities, Instant.parse("2026-10-28T02:10:00Z"));

		assertThat(result.at().toLocalTime()).isEqualTo(LocalTime.of(11, 10));
		assertThat(result.rooms().get(0).state().busy()).isTrue();
	}

	@Test
	void 보강일에는_지정된_요일_시간표로_판정한다() {
		// 2026-12-08은 화요일이지만 목요일 시간표
		BuildingAvailability result = availability.ofBuilding(humanities, seoul("2026-12-08T11:10:00"));

		assertThat(result.dayOfWeek()).isEqualTo(DayOfWeek.THURSDAY);
		assertThat(result.rooms().get(1).state()).isEqualTo(new RoomState(true, null, LocalTime.of(18, 0)));
	}

	@Test
	void 수업_데이터가_없는_요일은_모두_비어_있다() {
		// 2026-11-01은 일요일
		BuildingAvailability result = availability.ofBuilding(humanities, seoul("2026-11-01T11:10:00"));

		assertThat(result.rooms()).allSatisfy(s -> {
			assertThat(s.state()).isEqualTo(new RoomState(false, null, null));
			assertThat(s.sessions()).isEmpty();
		});
	}

	@Test
	void 건물별_빈_강의실_수를_센다() {
		var counts = availability.freeRoomCounts(seoul("2026-10-28T11:10:00"));

		// 인문관: 201 빈, 105 사용 중 / 천마관: 301 빈
		assertThat(counts).containsEntry(humanities.getId(), 1L).containsEntry(cheonma.getId(), 1L);
	}

}
