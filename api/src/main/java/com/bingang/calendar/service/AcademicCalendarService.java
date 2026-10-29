package com.bingang.calendar.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bingang.calendar.entity.AcademicEvent;
import com.bingang.calendar.repository.AcademicEventRepository;
import com.bingang.common.exception.BusinessException;
import com.bingang.common.exception.error.ErrorCode;

@Service
@Transactional(readOnly = true)
public class AcademicCalendarService {

	private static final long MAX_RANGE_DAYS = 366;

	private final AcademicEventRepository events;

	public AcademicCalendarService(AcademicEventRepository events) {
		this.events = events;
	}

	/** 그날 판정에 쓸 요일. 보강일이면 지정된 요일, 아니면 그날 요일 */
	public DayOfWeek effectiveDayOf(LocalDate date) {
		return events.findSubstituteDays(date).stream().findFirst().orElse(date.getDayOfWeek());
	}

	/** 기간과 하루라도 겹치는 일정, 시작일 순 */
	public List<AcademicEvent> findBetween(LocalDate from, LocalDate to) {
		if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
			throw new BusinessException(ErrorCode.INVALID_DATE_RANGE);
		}
		return events.findOverlapping(from, to);
	}

}
