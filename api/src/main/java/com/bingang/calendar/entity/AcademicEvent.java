package com.bingang.calendar.entity;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcademicEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 학교 페이지의 articleNo */
	@Column(nullable = false, unique = true)
	private Long sourceId;

	@Column(nullable = false)
	private LocalDate startDate;

	@Column(nullable = false)
	private LocalDate endDate;

	@Column(nullable = false, length = 200)
	private String title;

	/** 보강일이면 그날 적용할 요일, 아니면 null */
	private DayOfWeek substituteDay;

	@Column(nullable = false)
	private Instant fetchedAt;

	public AcademicEvent(Long sourceId, LocalDate startDate, LocalDate endDate, String title,
			DayOfWeek substituteDay, Instant fetchedAt) {
		this.sourceId = sourceId;
		update(startDate, endDate, title, substituteDay, fetchedAt);
	}

	/** 같은 articleNo를 다시 수집했을 때 내용을 덮어쓴다 */
	public void update(LocalDate startDate, LocalDate endDate, String title, DayOfWeek substituteDay,
			Instant fetchedAt) {
		this.startDate = startDate;
		this.endDate = endDate;
		this.title = title;
		this.substituteDay = substituteDay;
		this.fetchedAt = fetchedAt;
	}
}
