package com.bingang.building.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bingang.building.entity.Building;

public interface BuildingRepository extends JpaRepository<Building, Long> {

	Optional<Building> findByCode(String code);

}
