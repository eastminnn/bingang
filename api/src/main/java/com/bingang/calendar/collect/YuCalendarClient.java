package com.bingang.calendar.collect;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** 영남대 학사일정 페이지(calendar.do) HTML을 받아 온다 */
@Component
public class YuCalendarClient {

	static final String USER_AGENT = "bingang-collector/1.0";

	private final RestClient restClient;

	private final String url;

	public YuCalendarClient(RestClient.Builder builder, @Value("${bingang.calendar.url}") String url) {
		this.restClient = builder.defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT).build();
		this.url = url;
	}

	public String fetchHtml() {
		ResponseEntity<byte[]> response = restClient.get().uri(url).retrieve().toEntity(byte[].class);
		byte[] body = response.getBody();
		if (body == null) {
			throw new IllegalStateException("학사일정 페이지 응답이 비어 있습니다.");
		}
		return new String(body, charsetOf(response.getHeaders().getContentType()));
	}

	// Content-Type에 charset이 없으면 String 변환이 ISO-8859-1로 되어 한글이 깨지므로 직접 디코딩한다
	private Charset charsetOf(MediaType contentType) {
		if (contentType == null || contentType.getCharset() == null) {
			return StandardCharsets.UTF_8;
		}
		return contentType.getCharset();
	}

}
