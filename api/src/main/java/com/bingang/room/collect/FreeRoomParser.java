package com.bingang.room.collect;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** 빈강의실조회 응답 {"result7":[…]}을 강의실 목록으로 바꾼다 */
public final class FreeRoomParser {

	private static final JsonMapper MAPPER = JsonMapper.builder()
		.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
		.build();

	private FreeRoomParser() {
	}

	/** 형식이 다르면(로그인이 풀려 안내 페이지가 오는 경우 등) 예외를 던진다 */
	public static List<FreeRoom> parse(String body) {
		Response response;
		try {
			response = MAPPER.readValue(body, Response.class);
		}
		catch (JacksonException e) {
			throw new IllegalStateException("빈강의실조회 응답을 읽을 수 없습니다. 로그인이 풀렸거나 형식이 바뀌었을 수 있습니다.", e);
		}
		if (response.result7() == null) {
			throw new IllegalStateException("빈강의실조회 응답에 result7이 없습니다.");
		}
		return response.result7().stream().map(FreeRoomParser::toFreeRoom).filter(Objects::nonNull).toList();
	}

	// ltRoomNo는 "B03-201"처럼 건물 코드와 호수를 함께 담는다
	private static FreeRoom toFreeRoom(Raw raw) {
		String roomNo = trim(raw.ltRoomNo());
		if (roomNo == null) {
			return null;
		}
		int dash = roomNo.indexOf('-');
		String buildingCode = dash > 0 ? roomNo.substring(0, dash) : trim(raw.ltDong());
		String number = dash > 0 ? roomNo.substring(dash + 1) : roomNo;
		if (buildingCode == null || number.isEmpty()) {
			return null;
		}
		String buildingName = trim(raw.ltDongNm());
		return new FreeRoom(buildingCode, buildingName != null ? buildingName : buildingCode, number,
				trim(raw.ltRoomNm()), trim(raw.ltRoomType1()), toInt(raw.admtRcnt()));
	}

	private static String trim(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static Integer toInt(String value) {
		String trimmed = trim(value);
		if (trimmed == null) {
			return null;
		}
		try {
			int parsed = Integer.parseInt(trimmed);
			return parsed > 0 ? parsed : null;
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Response(List<Raw> result7) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Raw(String ltDong, String ltDongNm, String ltRoomNo, String ltRoomNm, String ltRoomType1,
			String admtRcnt) {
	}

}
