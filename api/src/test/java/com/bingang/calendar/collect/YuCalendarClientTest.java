package com.bingang.calendar.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;

@RestClientTest(YuCalendarClient.class)
class YuCalendarClientTest {

	private static final String URL = "https://www.yu.ac.kr/main/bachelor/calendar.do";

	@Autowired
	YuCalendarClient client;

	@Autowired
	MockRestServiceServer server;

	@Test
	void charset이_없는_응답도_UTF_8로_읽는다() {
		server.expect(requestTo(URL))
			.andExpect(header(HttpHeaders.USER_AGENT, YuCalendarClient.USER_AGENT))
			.andRespond(withSuccess("var calData = [{\"text\":\"개강\"}];".getBytes(StandardCharsets.UTF_8),
					MediaType.TEXT_HTML));

		assertThat(client.fetchHtml()).contains("개강");
		server.verify();
	}

	@Test
	void 학교_서버_오류는_예외로_알린다() {
		server.expect(requestTo(URL)).andRespond(withServerError());

		assertThatThrownBy(client::fetchHtml).isInstanceOf(HttpServerErrorException.class);
	}

}
