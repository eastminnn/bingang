package com.bingang.building.entity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Building {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 학교 시스템의 강의동 코드 (예: B03) */
	@Column(nullable = false, unique = true, length = 10)
	private String code;

	@Column(nullable = false, length = 50)
	private String name;

	/** 구역 = 코드 첫 글자 */
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(nullable = false, length = 1)
	private String zone;

	@Column(nullable = false)
	private boolean visible = true;

	public Building(String code, String name) {
		this.code = code;
		this.name = name;
		this.zone = code.substring(0, 1);
	}

	public void rename(String name) {
		this.name = name;
	}

	public void hide() {
		this.visible = false;
	}

	public void updateVisibility(boolean visible) {
		this.visible = visible;
	}
}
