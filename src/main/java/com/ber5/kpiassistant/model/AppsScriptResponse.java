package com.ber5.kpiassistant.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AppsScriptResponse(
    Boolean success,
    String error,
    String message,
    LocalDate reportDate,
    Integer receivedInOdoo,
    Integer mdmLocked,
    Integer fmiLocked,
    Integer frpLocked,
    Integer userLocked,
    Integer successful,
    Integer failed,
    Integer gradingTotal) {
}
