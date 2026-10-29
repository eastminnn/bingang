package com.bingang.calendar.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bingang.calendar.entity.AcademicEvent;

public interface AcademicEventRepository extends JpaRepository<AcademicEvent, Long> {

	Optional<AcademicEvent> findBySourceId(Long sourceId);

}
