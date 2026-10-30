package com.bingang.common.monitoring;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.bingang.TestcontainersConfiguration;

// 테스트는 기본적으로 지표 내보내기를 끄므로 @AutoConfigureMetrics로 Prometheus 형식을 켠다
@SpringBootTest(properties = { "bingang.calendar.enabled=false", "bingang.timetable.enabled=false" })
@AutoConfigureMockMvc
@AutoConfigureMetrics
@Import(TestcontainersConfiguration.class)
class PrometheusEndpointTest {

	@Autowired
	MockMvc mvc;

	@Test
	void API_JVM_DB_수집기_지표를_Prometheus_형식으로_내보낸다() throws Exception {
		mvc.perform(get("/api/academic-events")).andExpect(status().isOk());

		mvc.perform(get("/actuator/prometheus"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("http_server_requests_seconds_bucket")))
			.andExpect(content().string(containsString("uri=\"/api/academic-events\"")))
			.andExpect(content().string(containsString("jvm_memory_used_bytes")))
			.andExpect(content().string(containsString("hikaricp_connections_active")))
			.andExpect(content().string(containsString("bingang_collector_last_success_seconds{")))
			.andExpect(content().string(containsString("application=\"bingang-api\"")));
	}

}
