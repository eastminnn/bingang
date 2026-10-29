package com.bingang.room.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
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

	protected Building() {
	}

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

	public Long getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public String getZone() {
		return zone;
	}

	public boolean isVisible() {
		return visible;
	}

}
