package com.ber5.kpiassistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.ber5.kpiassistant.model.KpiData;
import com.ber5.kpiassistant.service.MockKpiDataClient;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MockKpiDataClientTest {

  @Test
  void loadsDeterministicMockKpiData() {
    LocalDate reportDate = LocalDate.of(2026, 7, 15);
    KpiData data = new MockKpiDataClient().load(reportDate);

    assertThat(data.reportDate()).isEqualTo(reportDate);
    assertThat(data.receivedInOdoo()).isEqualTo(36);
    assertThat(data.mdmLocked()).isEqualTo(10);
    assertThat(data.fmiLocked()).isEqualTo(4);
    assertThat(data.frpLocked()).isEqualTo(3);
    assertThat(data.userLocked()).isZero();
    assertThat(data.successful()).isEqualTo(30);
    assertThat(data.failed()).isEqualTo(4);
    assertThat(data.gradingTotal()).isEqualTo(34);
    assertThat(data.totalLocked()).isEqualTo(17);
  }
}
