package com.bingang.calendar.repository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.bingang.calendar.entity.AcademicEvent;

public interface AcademicEventRepository extends JpaRepository<AcademicEvent, Long> {

	Optional<AcademicEvent> findBySourceId(Long sourceId);

	List<AcademicEvent> findBySourceIdIn(Collection<Long> sourceIds);

	@Query("select e.substituteDay from AcademicEvent e where e.substituteDay is not null and e.startDate <= :date and e.endDate >= :date")
	List<DayOfWeek> findSubstituteDays(LocalDate date);

}
