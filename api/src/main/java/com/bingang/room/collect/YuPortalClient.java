package com.bingang.room.collect;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * 학생종합정보시스템에 로그인해 빈강의실조회를 요청한다.
 * 한 계정에 세션이 하나뿐이라 수집할 때마다 새 쿠키 저장소로 로그인한다.
 */
@Component
public class YuPortalClient {

	private static final String LOGIN_PATH = "/std5/com/admn/user/findAdmnLoginCheckN_L_S.do";

	private static final String EMPTY_ROOM_PATH = "/std5/uni/cour/tmtb/findLtRoomInq.do";

	private final RestClient.Builder builder;

	private final String baseUrl;

	private final String userNo;

	private final String password;

	public YuPortalClient(RestClient.Builder builder, @Value("${bingang.timetable.base-url}") String baseUrl,
			@Value("${bingang.timetable.user-no:}") String userNo,
			@Value("${bingang.timetable.password:}") String password) {
		this.builder = builder;
		this.baseUrl = baseUrl;
		this.userNo = userNo;
		this.password = password;
	}

	public boolean hasCredentials() {
		return !userNo.isBlank() && !password.isBlank();
	}

	public Session login() {
		if (!hasCredentials()) {
			throw new IllegalStateException("학교 계정 정보(YU_USERNO, YU_PASSWD)가 없습니다.");
		}
		HttpClient http = HttpClient.newBuilder()
			.cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(5))
			.build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
		factory.setReadTimeout(Duration.ofSeconds(20));
		RestClient client = builder.clone()
			.requestFactory(factory)
			.baseUrl(baseUrl)
			.defaultHeader(HttpHeaders.USER_AGENT, "bingang-collector/1.0")
			.build();

		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("userno", userNo);
		form.add("passwd", password);
		form.add("login_gb", "0");
		client.post().uri(LOGIN_PATH).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve()
			.toBodilessEntity();
		return new Session(client);
	}

	public final class Session {

		private final RestClient client;

		private Session(RestClient client) {
			this.client = client;
		}

		/** 그 요일·시간 칸에 비어 있는 강의실 (day: 1=월 ~ 6=토) */
		public List<FreeRoom> freeRooms(String year, String semesterCode, int day, Slot slot) {
			String sbDate = day + slot.code();
			Map<String, String> req = Map.of("yy", year, "shtmCd", semesterCode, "ltDongCd", "", "sbDate", sbDate,
					"sbDate2", sbDate, "regId", userNo, "regIp", "", "modId", userNo, "modIp", "", "subId", userNo);
			byte[] body = client.post()
				.uri(uri -> uri.path(EMPTY_ROOM_PATH).queryParam("strCheck", userNo).build())
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.body(Map.of("req", List.of(req)))
				.retrieve()
				.body(byte[].class);
			return FreeRoomParser.parse(body == null ? "" : new String(body, StandardCharsets.UTF_8));
		}

	}

}
