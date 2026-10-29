package com.bingang.room.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import com.bingang.TestcontainersConfiguration;
import com.bingang.building.entity.Building;
import com.bingang.building.repository.BuildingRepository;
import com.bingang.room.entity.Room;
import com.bingang.room.entity.RoomSchedule;

import jakarta.persistence.EntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class RoomRepositoryTest {

	@Autowired
	BuildingRepository buildings;

	@Autowired
	RoomRepository rooms;

	@Autowired
	RoomScheduleRepository schedules;

	@Autowired
	EntityManager em;

	@Test
	void 건물의_요일별_사용_시간을_조회한다() {
		Building building = buildings.save(new Building("B03", "인문관"));
		Room room = rooms.save(new Room(building, "201", (short) 2));
		schedules.save(new RoomSchedule(room, DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(10, 30)));
		schedules.save(new RoomSchedule(room, DayOfWeek.THURSDAY, LocalTime.of(13, 0), LocalTime.of(14, 30)));
		em.flush();
		em.clear();

		Building found = buildings.findByCode("B03").orElseThrow();
		assertThat(found.getZone()).isEqualTo("B");
		assertThat(found.isVisible()).isTrue();
		assertThat(rooms.findByBuilding(found)).extracting(Room::getNumber).containsExactly("201");
		assertThat(schedules.findByBuildingAndDay(found, DayOfWeek.WEDNESDAY))
			.singleElement()
			.satisfies(s -> {
				assertThat(s.getStartTime()).isEqualTo(LocalTime.of(9, 0));
				assertThat(s.getEndTime()).isEqualTo(LocalTime.of(10, 30));
			});
	}

	@Test
	void 끝나는_시각이_시작보다_빠르면_저장되지_않는다() {
		Building building = buildings.save(new Building("B03", "인문관"));
		Room room = rooms.save(new Room(building, "201", (short) 2));

		assertThatThrownBy(() -> schedules.saveAndFlush(
				new RoomSchedule(room, DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(9, 0))))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 같은_건물에_같은_호수는_저장되지_않는다() {
		Building building = buildings.save(new Building("B03", "인문관"));
		rooms.saveAndFlush(new Room(building, "201", (short) 2));

		assertThatThrownBy(() -> rooms.saveAndFlush(new Room(building, "201", (short) 2)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}
