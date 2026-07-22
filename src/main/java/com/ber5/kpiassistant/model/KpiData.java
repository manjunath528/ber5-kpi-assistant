package com.ber5.kpiassistant.model;

import java.time.LocalDate;

public record KpiData(
    LocalDate reportDate,
    int receivedInOdoo,
    int mdmLocked,
    int fmiLocked,
    int frpLocked,
    int userLocked,
    int successful,
    int failed,
    int gradingTotal) {

  public int totalLocked() {
    return mdmLocked + fmiLocked + frpLocked + userLocked;
  }
}
