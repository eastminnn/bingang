package com.bingang.room.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.bingang.building.entity.Building;
import com.bingang.room.entity.Room;

public interface RoomRepository extends JpaRepository<Room, Long> {

	List<Room> findByBuilding(Building building);

	@Query("select r from Room r join fetch r.building")
	List<Room> findAllWithBuilding();

}
