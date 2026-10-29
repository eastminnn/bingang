package com.bingang.room.repository;

import java.time.DayOfWeek;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.bingang.building.entity.Building;
import com.bingang.room.entity.RoomSchedule;

public interface RoomScheduleRepository extends JpaRepository<RoomSchedule, Long> {

	// room을 함께 가져와 강의실마다 쿼리가 한 번씩 더 나가는 것(N+1)을 막는다
	@Query("select s from RoomSchedule s join fetch s.room r where r.building = :building and s.dayOfWeek = :day")
	List<RoomSchedule> findByBuildingAndDay(Building building, DayOfWeek day);

	@Query("select s from RoomSchedule s join fetch s.room where s.dayOfWeek = :day")
	List<RoomSchedule> findByDay(DayOfWeek day);

	// 한 번에 지우고 영속성 컨텍스트도 비워, 메모리에 남은 시간표가 지운 강의실을 다시 참조하지 않게 한다
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from RoomSchedule")
	void deleteAllSchedules();

}
