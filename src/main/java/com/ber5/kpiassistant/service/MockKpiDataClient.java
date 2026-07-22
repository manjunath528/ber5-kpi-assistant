package com.ber5.kpiassistant.service;

import com.ber5.kpiassistant.model.KpiData;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
public class MockKpiDataClient implements KpiDataClient {

  @Override
  public KpiData load(LocalDate reportDate) {
    return new KpiData(reportDate, 36, 10, 4, 3, 0, 30, 4, 34);
  }
}
