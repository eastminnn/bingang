package com.bingang.calendar.service;

import java.time.DayOfWeek;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bingang.calendar.repository.AcademicEventRepository;

@Service
@Transactional(readOnly = true)
public class AcademicCalendarService {

	private final AcademicEventRepository events;

	public AcademicCalendarService(AcademicEventRepository events) {
		this.events = events;
	}

	/** 그날 판정에 쓸 요일. 보강일이면 지정된 요일, 아니면 그날 요일 */
	public DayOfWeek effectiveDayOf(LocalDate date) {
		return events.findSubstituteDays(date).stream().findFirst().orElse(date.getDayOfWeek());
	}

}
