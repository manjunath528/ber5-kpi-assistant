package com.ber5.kpiassistant.model;

import java.time.LocalDate;

public record PreviewRequest(
    LocalDate reportDate,
    Integer receivedInOdoo,
    Integer mdmLocked,
    Integer fmiLocked,
    Integer frpLocked,
    Integer userLocked,
    Integer successful,
    Integer failed,
    Integer gradingTotal,
    Integer currentMdmLockedTotal,
    Integer gradingBacklog,
    Integer unpackedReturnPackages,
    LocalDate unpackedReturnPackagesDate,
    String notes) {
}
