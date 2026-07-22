package com.ber5.kpiassistant.service;

import com.ber5.kpiassistant.exception.KpiDataException;
import com.ber5.kpiassistant.model.PreviewRequest;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SlackPreviewService {

  private static final DateTimeFormatter SLACK_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

  public String generate(PreviewRequest request) {
    validate(request);

    int userLocked = valueOrZero(request.userLocked());
    int totalLocked = request.mdmLocked() + request.fmiLocked() + request.frpLocked() + userLocked;
    String reportDate = request.reportDate().format(SLACK_DATE_FORMAT);
    String unpackedReturnPackagesDate = request.unpackedReturnPackagesDate() == null
        ? ""
        : request.unpackedReturnPackagesDate().format(SLACK_DATE_FORMAT);
    String message = String.join("\n",
        "Retoure Zahlen vom " + reportDate,
        request.receivedInOdoo() + " Retoure im Odoo vereinnahmt",
        "Locked: " + totalLocked,
        request.mdmLocked() + " MDM locked bei Vereinnahmung (aktuell noch " + valueOrZero(request.currentMdmLockedTotal()) + " ges.)",
        request.fmiLocked() + " FMI locked",
        request.frpLocked() + " FRP locked",
        userLocked + " User locked",
        "Gradings: " + request.gradingTotal(),
        request.successful() + " Successful",
        request.failed() + " Failed",
        "Grading Backlog: " + valueOrZero(request.gradingBacklog()),
        valueOrZero(request.unpackedReturnPackages()) + " Retourenpakete vom " + unpackedReturnPackagesDate + " sind noch nicht vereinnahmt",
        "Anmerkungen:");

    if (request.notes() != null && !request.notes().isBlank()) {
      return message + "\n" + request.notes().trim();
    }
    return message;
  }

  private void validate(PreviewRequest request) {
    Map<String, Integer> required = new LinkedHashMap<>();
    required.put("receivedInOdoo", request.receivedInOdoo());
    required.put("mdmLocked", request.mdmLocked());
    required.put("fmiLocked", request.fmiLocked());
    required.put("frpLocked", request.frpLocked());
    required.put("userLocked", request.userLocked());
    required.put("successful", request.successful());
    required.put("failed", request.failed());
    required.put("gradingTotal", request.gradingTotal());

    if (request.reportDate() == null || required.values().stream().anyMatch(value -> value == null)) {
      throw new KpiDataException(HttpStatus.BAD_REQUEST, "KPI data is required.");
    }
    if (required.values().stream().anyMatch(value -> value < 0)
        || valueOrZero(request.currentMdmLockedTotal()) < 0
        || valueOrZero(request.gradingBacklog()) < 0
        || valueOrZero(request.unpackedReturnPackages()) < 0) {
      throw new KpiDataException(HttpStatus.BAD_REQUEST, "Values cannot be negative.");
    }
    if (valueOrZero(request.unpackedReturnPackages()) > 0 && request.unpackedReturnPackagesDate() == null) {
      throw new KpiDataException(
          HttpStatus.BAD_REQUEST,
          "Retourenpakete date is required when unpacked return packages is greater than 0.");
    }
    if (request.gradingTotal() != request.successful() + request.failed()) {
      throw new KpiDataException(HttpStatus.BAD_REQUEST, "Gradings must equal Successful plus Failed.");
    }
  }

  private int valueOrZero(Integer value) {
    return value == null ? 0 : value;
  }
}
