package com.bingang.common.jpa;

import java.time.DayOfWeek;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** DayOfWeek ↔ SMALLINT (1=월 ~ 7=일, ISO 순서와 같음) */
@Converter(autoApply = true)
public class DayOfWeekConverter implements AttributeConverter<DayOfWeek, Short> {

	@Override
	public Short convertToDatabaseColumn(DayOfWeek day) {
		return day == null ? null : (short) day.getValue();
	}

	@Override
	public DayOfWeek convertToEntityAttribute(Short value) {
		return value == null ? null : DayOfWeek.of(value);
	}

}
