package com.bingang.room.repository;

import java.time.DayOfWeek;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bingang.building.entity.Building;
import com.bingang.room.entity.RoomSchedule;

public interface RoomScheduleRepository extends JpaRepository<RoomSchedule, Long> {

	List<RoomSchedule> findByRoomBuildingAndDayOfWeek(Building building, DayOfWeek dayOfWeek);

}
