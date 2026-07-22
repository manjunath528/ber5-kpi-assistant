package com.ber5.kpiassistant.service;

import com.ber5.kpiassistant.model.KpiData;
import java.time.LocalDate;

public interface KpiDataClient {

  KpiData load(LocalDate reportDate);
}
