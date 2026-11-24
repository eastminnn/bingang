package com.bingang.ask.client;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bingang.ask.service.AskCondition;
import com.bingang.ask.service.AskUnavailableException;
import com.bingang.ask.service.QuestionParser;
import com.bingang.building.entity.Building;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * OpenAI Chat Completions로 질문을 조건(JSON)으로 바꾼다. 응답은 JSON 스키마(strict)로 형식을 고정한다.
 * 키는 서버 환경 변수(OPENAI_API_KEY)로만 받고, 질문 원문은 로그에 남기지 않는다.
 */
@Component
public class OpenAiQuestionParser implements QuestionParser {

	// 학생들이 부르는 줄임말
	private static final Map<String, String> ALIASES = Map.ofEntries(Map.entry("E21", "아이티, IT"),
			Map.entry("B05", "사과대, 사회대"), Map.entry("G07", "약대"), Map.entry("A10", "음대, 음악"),
			Map.entry("A07", "미대, 미술"), Map.entry("A06", "미대, 디자인"), Map.entry("A08", "사대, 사범대"),
			Map.entry("B02", "상대, 상경대, 경영, 경제"), Map.entry("G01", "생과대, 생활대"), Map.entry("G02", "생과대, 생활대"),
			Map.entry("F26", "생명대, 생명과학"), Map.entry("F27", "생명대, 생명과학"), Map.entry("F28", "생명대, 생명과학"),
			Map.entry("F29", "생명대, 생명과학"), Map.entry("C02", "외교원, 어학원"));

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private static final Map<String, Object> SCHEMA = schema();

	private final RestClient client;

	private final String apiKey;

	private final String model;

	private final String reasoningEffort;

	public OpenAiQuestionParser(RestClient.Builder builder,
			@Value("${bingang.ask.openai.base-url:https://api.openai.com}") String baseUrl,
			@Value("${bingang.ask.openai.api-key:}") String apiKey,
			@Value("${bingang.ask.openai.model:gpt-5.4-nano}") String model,
			@Value("${bingang.ask.openai.reasoning-effort:low}") String reasoningEffort) {
		this.client = builder.clone().baseUrl(baseUrl).build();
		this.apiKey = apiKey;
		this.model = model;
		this.reasoningEffort = reasoningEffort;
	}

	@Override
	public boolean available() {
		return !apiKey.isBlank();
	}

	@Override
	public AskCondition parse(String question, ZonedDateTime now, List<Building> buildings) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", model);
		body.put("messages", List.of(Map.of("role", "system", "content", instructions(now, buildings)),
				Map.of("role", "user", "content", question)));
		body.put("response_format", Map.of("type", "json_schema", "json_schema",
				Map.of("name", "room_query", "strict", true, "schema", SCHEMA)));
		// 추론 모델이 아니면 이 값을 받지 않으므로 비워 두면 보내지 않는다
		if (!reasoningEffort.isBlank()) {
			body.put("reasoning_effort", reasoningEffort);
		}
		try {
			ChatResponse response = client.post()
				.uri("/v1/chat/completions")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.body(ChatResponse.class);
			return toCondition(MAPPER.readValue(content(response), Parsed.class));
		}
		catch (RestClientException | JacksonException | IllegalStateException e) {
			throw new AskUnavailableException("OpenAI 호출 실패: " + e.getClass().getSimpleName(), e);
		}
	}

	static String instructions(ZonedDateTime now, List<Building> buildings) {
		String list = buildings.stream()
			.map(b -> b.getCode() + " " + b.getName()
					+ (ALIASES.containsKey(b.getCode()) ? " (" + ALIASES.get(b.getCode()) + ")" : ""))
			.collect(Collectors.joining("\n"));
		return """
				너는 영남대학교 빈 강의실 검색 조건을 뽑는 도구다. 질문을 정해진 JSON 형식으로만 바꾼다.
				지금: %s %s (한국 시간)

				날짜표 (주는 월요일 시작. 날짜는 반드시 이 표에서 고른다)
				%s

				규칙
				- 빈 강의실·공부할 곳·비는 시간을 찾는 질문이 아니면 understood=false, 나머지는 null/빈 배열.
				- date: 날짜를 말하지 않으면 오늘. "목요일"처럼 요일만 말하면 오늘부터 가장 가까운 그 요일(오늘 포함).
				  "이번 주 X요일"은 이번 주 줄, "다음 주 X요일"은 다음 주 줄, "다다음 주 X요일"은 다다음 주 줄에서 고른다.
				  표보다 먼 날짜도 계산해서 그대로 넣는다 (서버가 범위를 확인한다).
				- start, end: HH:MM (24시간). 말한 시각을 그대로 바꾸고, 범위를 벗어나도 고치지 않는다 (서버가 확인한다).
				- 오전·오후 없이 "1시"~"8시"는 오후(13:00~20:00), "9시"~"12시"는 그대로(09:00~12:00).
				- 시각 하나만 말하면 그 시각이 start이고 end=null. "N시까지"처럼 끝만 말하면 start=null.
				- "지금부터"는 지금 시각. "오전"=09:00~12:00, "오후"=12:00~18:00, "저녁"=18:00~21:00, "밤 N시"=오후 N시.
				- "2시간"처럼 길이를 말하면 end=start+길이.
				- 시각을 전혀 말하지 않으면 start=null, end=null.
				- buildings: 아래 목록의 코드만. 건물을 말하지 않으면 빈 배열. 줄임말·번호(예: E21)도 알아본다.

				예 (오늘이 %s일 때)
				- "목요일 1시부터 3시까지 IT관" → date=가장 가까운 목요일, start=13:00, end=15:00, buildings=[E21]
				- "다음 주 월요일 3시 상경관" → date=다음 주 월요일, start=15:00, end=null, buildings=[B02]
				- "내일 오전" → date=내일, start=09:00, end=12:00
				- "금요일" → date=가장 가까운 금요일, start=null, end=null
				- "4시까지" → date=오늘, start=null, end=16:00
				- "학식 메뉴" → understood=false

				건물 목록 (코드 이름 (줄임말))
				%s
				""".formatted(dayLabel(now.toLocalDate()), now.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")),
				calendar(now.toLocalDate()), dayLabel(now.toLocalDate()), list);
	}

	// 이번 주·다음 주·다다음 주 (월~일). 작은 모델이 요일 계산을 틀리지 않게 표로 준다
	static String calendar(LocalDate today) {
		LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		String[] names = { "이번 주", "다음 주", "다다음 주" };
		StringBuilder table = new StringBuilder();
		for (int w = 0; w < names.length; w++) {
			table.append(names[w]).append(':');
			for (int d = 0; d < 7; d++) {
				LocalDate date = monday.plusWeeks(w).plusDays(d);
				table.append(' ').append(dayLabel(date));
				if (date.equals(today)) {
					table.append("=오늘");
				}
				else if (date.equals(today.plusDays(1))) {
					table.append("=내일");
				}
				else if (date.equals(today.plusDays(2))) {
					table.append("=모레");
				}
			}
			table.append('\n');
		}
		return table.toString().strip();
	}

	private static String dayLabel(LocalDate date) {
		return date + "(" + date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN) + ")";
	}

	private static String content(ChatResponse response) {
		if (response == null || response.choices() == null || response.choices().isEmpty()
				|| response.choices().getFirst().message() == null
				|| response.choices().getFirst().message().content() == null) {
			throw new IllegalStateException("응답에 내용이 없습니다");
		}
		return response.choices().getFirst().message().content();
	}

	static AskCondition toCondition(Parsed parsed) {
		if (!parsed.understood()) {
			return AskCondition.notUnderstood();
		}
		try {
			return new AskCondition(true, parsed.date() == null ? null : LocalDate.parse(parsed.date()),
					parsed.start() == null ? null : LocalTime.parse(parsed.start()),
					parsed.end() == null ? null : LocalTime.parse(parsed.end()),
					parsed.buildings() == null ? List.of() : List.copyOf(parsed.buildings()));
		}
		catch (DateTimeParseException e) {
			// 형식은 스키마로 고정되지만 값까지 강제되지는 않는다 (예: "25:00"). 해석 못 한 질문으로 본다
			return AskCondition.notUnderstood();
		}
	}

	private static Map<String, Object> schema() {
		Map<String, Object> nullableString = Map.of("type", List.of("string", "null"));
		Map<String, Object> properties = new LinkedHashMap<>();
		properties.put("understood", Map.of("type", "boolean"));
		properties.put("date", nullableString);
		properties.put("start", nullableString);
		properties.put("end", nullableString);
		properties.put("buildings", Map.of("type", "array", "items", Map.of("type", "string")));
		return Map.of("type", "object", "properties", properties, "required", new ArrayList<>(properties.keySet()),
				"additionalProperties", false);
	}

	record Parsed(boolean understood, String date, String start, String end, List<String> buildings) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ChatResponse(List<Choice> choices) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Choice(Message message) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Message(String content) {
	}

}
