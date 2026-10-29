package com.bingang.room.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.bingang.TestcontainersConfiguration;
import com.bingang.building.entity.Building;
import com.bingang.building.repository.BuildingRepository;
import com.bingang.room.entity.Room;
import com.bingang.room.entity.RoomSchedule;
import com.bingang.room.repository.RoomRepository;
import com.bingang.room.repository.RoomScheduleRepository;

@SpringBootTest(properties = "bingang.calendar.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class RoomControllerTest {

	// 2026-10-28(수) 11:10
	private static final String WED_1110 = "2026-10-28T11:10:00+09:00";

	@Autowired
	MockMvc mvc;

	@Autowired
	BuildingRepository buildings;

	@Autowired
	RoomRepository rooms;

	@Autowired
	RoomScheduleRepository schedules;

	@BeforeEach
	void setUp() {
		Building humanities = buildings.save(new Building("B03", "인문관"));
		Building cheonma = buildings.save(new Building("C03", "천마관"));
		Building library = buildings.save(new Building("B04", "중앙도서관"));
		library.hide();

		Room r201 = rooms.save(new Room(humanities, "201", (short) 2));
		r201.updateDetails(60, "강의실", "일반");
		Room r105 = rooms.save(new Room(humanities, "105", (short) 1));
		rooms.save(new Room(cheonma, "301", (short) 3));
		rooms.save(new Room(library, "101", (short) 1));

		schedules.save(new RoomSchedule(r201, DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(10, 15)));
		schedules.save(new RoomSchedule(r201, DayOfWeek.WEDNESDAY, LocalTime.of(11, 30), LocalTime.of(14, 0)));
		schedules.save(new RoomSchedule(r105, DayOfWeek.WEDNESDAY, LocalTime.of(10, 30), LocalTime.of(13, 0)));
	}

	@Test
	void 건물_목록과_빈_강의실_수를_돌려준다() throws Exception {
		mvc.perform(get("/api/buildings").param("at", WED_1110))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.at").value("2026-10-28T11:10:00+09:00"))
			// 숨긴 건물(B04)은 빠지고 코드 순서
			.andExpect(jsonPath("$.buildings.length()").value(2))
			.andExpect(jsonPath("$.buildings[0].code").value("B03"))
			.andExpect(jsonPath("$.buildings[0].name").value("인문관"))
			.andExpect(jsonPath("$.buildings[0].zone").value("B"))
			.andExpect(jsonPath("$.buildings[0].freeRooms").value(1))
			.andExpect(jsonPath("$.buildings[0].totalRooms").value(2))
			.andExpect(jsonPath("$.buildings[1].code").value("C03"))
			.andExpect(jsonPath("$.buildings[1].freeRooms").value(1));
	}

	@Test
	void 건물의_강의실_상태를_SRS_형식으로_돌려준다() throws Exception {
		mvc.perform(get("/api/buildings/B03/rooms").param("at", WED_1110))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.at").value("2026-10-28T11:10:00+09:00"))
			.andExpect(jsonPath("$.dayOfWeek").value(3))
			.andExpect(jsonPath("$.building.code").value("B03"))
			.andExpect(jsonPath("$.building.name").value("인문관"))
			.andExpect(jsonPath("$.rooms[0].number").value("105"))
			.andExpect(jsonPath("$.rooms[0].busy").value(true))
			.andExpect(jsonPath("$.rooms[0].freeAt").value("13:00"))
			.andExpect(jsonPath("$.rooms[0].freeUntil").doesNotExist())
			.andExpect(jsonPath("$.rooms[1].number").value("201"))
			.andExpect(jsonPath("$.rooms[1].floor").value(2))
			.andExpect(jsonPath("$.rooms[1].capacity").value(60))
			.andExpect(jsonPath("$.rooms[1].busy").value(false))
			.andExpect(jsonPath("$.rooms[1].freeUntil").value("11:30"))
			.andExpect(jsonPath("$.rooms[1].sessions[0][0]").value("09:00"))
			.andExpect(jsonPath("$.rooms[1].sessions[0][1]").value("10:15"))
			.andExpect(jsonPath("$.rooms[1].sessions[1][0]").value("11:30"));
	}

	@Test
	void 다른_시간대로_보내도_한국_시간으로_답한다() throws Exception {
		mvc.perform(get("/api/buildings/B03/rooms?at=2026-10-28T02:10:00Z"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.at").value("2026-10-28T11:10:00+09:00"))
			.andExpect(jsonPath("$.rooms[0].busy").value(true));
	}

	@Test
	void at이_없으면_지금_기준으로_답한다() throws Exception {
		mvc.perform(get("/api/buildings/B03/rooms"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.at").exists())
			.andExpect(jsonPath("$.rooms.length()").value(2));
	}

	@Test
	void 없는_건물은_404() throws Exception {
		mvc.perform(get("/api/buildings/Z99/rooms"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("BUILDING_NOT_FOUND"));
	}

	@Test
	void 숨긴_건물도_404() throws Exception {
		mvc.perform(get("/api/buildings/B04/rooms"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("BUILDING_NOT_FOUND"));
	}

	@Test
	void 시각_형식이_틀리면_400() throws Exception {
		mvc.perform(get("/api/buildings?at=abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PARAMETER_TYPE"))
			.andExpect(jsonPath("$.errors[0].field").value("at"));
	}

}
