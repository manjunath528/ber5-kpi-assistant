package com.ber5.kpiassistant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Ber5KpiAssistantApplicationTests {

  @LocalServerPort
  int port;

  @Autowired
  TestRestTemplate restTemplate;

  @Test
  void contextStarts() {
    assertThat(port).isPositive();
  }

  @Test
  void dashboardPageRenders() {
    String html = restTemplate.getForObject("/", String.class);

    assertThat(html)
        .contains("BER5 KPI Assistant")
        .contains("Prepare the daily BER5 Return KPI Slack report.")
        .contains("Load KPI Data")
        .contains("Generate Preview");
  }
}
