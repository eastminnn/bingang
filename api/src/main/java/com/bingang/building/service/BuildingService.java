package com.bingang.building.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bingang.building.entity.Building;
import com.bingang.building.repository.BuildingRepository;
import com.bingang.common.exception.BusinessException;
import com.bingang.common.exception.error.ErrorCode;

@Service
@Transactional(readOnly = true)
public class BuildingService {

	private final BuildingRepository buildings;

	public BuildingService(BuildingRepository buildings) {
		this.buildings = buildings;
	}

	public List<Building> findVisible() {
		return buildings.findByVisibleTrueOrderByCode();
	}

	// 숨긴 건물은 목록에 없으므로 직접 주소로 들어와도 없는 건물로 본다
	public Building getVisible(String code) {
		return buildings.findByCode(code)
			.filter(Building::isVisible)
			.orElseThrow(() -> new BusinessException(ErrorCode.BUILDING_NOT_FOUND));
	}

}
