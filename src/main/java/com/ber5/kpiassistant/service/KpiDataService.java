package com.ber5.kpiassistant.service;

import com.ber5.kpiassistant.config.Ber5Properties;
import com.ber5.kpiassistant.config.DataMode;
import com.ber5.kpiassistant.model.KpiData;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
public class KpiDataService {

  private final Ber5Properties properties;
  private final MockKpiDataClient mockClient;
  private final AppsScriptKpiDataClient appsScriptClient;

  public KpiDataService(
      Ber5Properties properties,
      MockKpiDataClient mockClient,
      AppsScriptKpiDataClient appsScriptClient) {
    this.properties = properties;
    this.mockClient = mockClient;
    this.appsScriptClient = appsScriptClient;
  }

  public KpiData load(LocalDate reportDate) {
    if (properties.dataMode() == DataMode.APPS_SCRIPT) {
      return appsScriptClient.load(reportDate);
    }
    return mockClient.load(reportDate);
  }

  public DataMode mode() {
    return properties.dataMode();
  }
}
