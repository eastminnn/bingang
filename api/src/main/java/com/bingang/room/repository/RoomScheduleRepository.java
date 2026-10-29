package com.bingang.room.repository;

import java.time.DayOfWeek;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.bingang.building.entity.Building;
import com.bingang.room.entity.RoomSchedule;

public interface RoomScheduleRepository extends JpaRepository<RoomSchedule, Long> {

	// room을 함께 가져와 강의실마다 쿼리가 한 번씩 더 나가는 것(N+1)을 막는다
	@Query("select s from RoomSchedule s join fetch s.room r where r.building = :building and s.dayOfWeek = :day")
	List<RoomSchedule> findByBuildingAndDay(Building building, DayOfWeek day);

	@Query("select s from RoomSchedule s join fetch s.room where s.dayOfWeek = :day")
	List<RoomSchedule> findByDay(DayOfWeek day);

}
