package com.ber5.kpiassistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ber5.kpiassistant.exception.KpiDataException;
import com.ber5.kpiassistant.model.PreviewRequest;
import com.ber5.kpiassistant.service.SlackPreviewService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SlackPreviewServiceTest {

  private final SlackPreviewService service = new SlackPreviewService();

  @Test
  void generatesSlackMessageWithExactLineOrderAndGermanDate() {
    String message = service.generate(validRequest());

    assertThat(message).isEqualTo("""
        Retoure Zahlen vom 15.07.2026
        36 Retoure im Odoo vereinnahmt
        Locked: 17
        10 MDM locked bei Vereinnahmung (aktuell noch 12 ges.)
        4 FMI locked
        3 FRP locked
        0 User locked
        Gradings: 34
        30 Successful
        4 Failed
        Grading Backlog: 8
        2 Retourenpakete vom 14.07.2026 sind noch nicht vereinnahmt
        Anmerkungen:""");
  }

  @Test
  void reportDateAndRetourenpaketeDateCanBeDifferent() {
    String message = service.generate(validRequest());

    assertThat(message).contains("Retoure Zahlen vom 15.07.2026");
    assertThat(message).contains("2 Retourenpakete vom 14.07.2026 sind noch nicht vereinnahmt");
    assertThat(message).doesNotContain("2 Retourenpakete vom 15.07.2026 sind noch nicht vereinnahmt");
  }

  @Test
  void containsNoBlankLinesOrExtraFormatting() {
    String message = service.generate(validRequest());

    assertThat(message)
        .doesNotContain("\n\n")
        .doesNotContain("- ")
        .doesNotContain("#")
        .doesNotContain("*")
        .doesNotContain("```");
  }

  @Test
  void calculatesTotalLockedWithUserLockedValue() {
    PreviewRequest request = new PreviewRequest(
        LocalDate.of(2026, 7, 15),
        6, 2, 0, 2, 1, 5, 1, 6, 5, 3, 2, LocalDate.of(2026, 7, 14), "");

    String message = service.generate(request);

    assertThat(message).contains("Locked: 5");
    assertThat(message).contains("1 User locked");
  }

  @Test
  void usesManualCurrentMdmLockedTotalGradingBacklogAndUnpackedReturnPackages() {
    String message = service.generate(validRequest());

    assertThat(message).contains("10 MDM locked bei Vereinnahmung (aktuell noch 12 ges.)");
    assertThat(message).contains("Grading Backlog: 8");
    assertThat(message).contains("2 Retourenpakete vom 14.07.2026 sind noch nicht vereinnahmt");
  }

  @Test
  void appendsNotesOnNextLine() {
    PreviewRequest request = new PreviewRequest(
        LocalDate.of(2026, 7, 15),
        6, 2, 0, 2, 0, 5, 1, 6, 5, 3, 2, LocalDate.of(2026, 7, 14), "Keine weiteren Auffälligkeiten.");

    assertThat(service.generate(request)).endsWith("""
        Anmerkungen:
        Keine weiteren Auffälligkeiten.""");
  }

  @Test
  void leavesOnlyAnmerkungenWhenNotesAreEmpty() {
    assertThat(service.generate(validRequest())).endsWith("Anmerkungen:");
  }

  @Test
  void rendersZeroValuesWithoutNulls() {
    PreviewRequest request = new PreviewRequest(
        LocalDate.of(2026, 7, 15),
        0, 0, 0, 0, 0, 0, 0, 0, null, null, null, null, "");

    assertThat(service.generate(request)).isEqualTo("""
        Retoure Zahlen vom 15.07.2026
        0 Retoure im Odoo vereinnahmt
        Locked: 0
        0 MDM locked bei Vereinnahmung (aktuell noch 0 ges.)
        0 FMI locked
        0 FRP locked
        0 User locked
        Gradings: 0
        0 Successful
        0 Failed
        Grading Backlog: 0
        0 Retourenpakete vom  sind noch nicht vereinnahmt
        Anmerkungen:""");
    assertThat(service.generate(request)).doesNotContain("null");
  }

  @Test
  void doesNotInsertTodayWhenPackageCountIsZeroAndPackageDateIsEmpty() {
    PreviewRequest request = new PreviewRequest(
        LocalDate.of(2026, 7, 15),
        0, 0, 0, 0, 0, 0, 0, 0, null, null, 0, null, "");

    String message = service.generate(request);

    assertThat(message).contains("0 Retourenpakete vom  sind noch nicht vereinnahmt");
    assertThat(message).doesNotContain("22.07.2026");
  }

  @Test
  void requiresPackageDateWhenPackageCountIsGreaterThanZero() {
    PreviewRequest request = new PreviewRequest(
        LocalDate.of(2026, 7, 15),
        6, 2, 0, 2, 0, 5, 1, 6, 5, 3, 5, null, "");

    assertThatThrownBy(() -> service.generate(request))
        .isInstanceOf(KpiDataException.class)
        .hasMessage("Retourenpakete date is required when unpacked return packages is greater than 0.");
  }

  @Test
  void allowsEmptyPackageDateWhenPackageCountIsZero() {
    PreviewRequest request = new PreviewRequest(
        LocalDate.of(2026, 7, 15),
        6, 2, 0, 2, 0, 5, 1, 6, 5, 3, 0, null, "");

    assertThat(service.generate(request)).contains("0 Retourenpakete vom  sind noch nicht vereinnahmt");
  }

  @Test
  void rejectsNegativePackageCount() {
    PreviewRequest request = new PreviewRequest(
        LocalDate.of(2026, 7, 15),
        6, 2, 0, 2, 0, 5, 1, 6, 5, 3, -1, LocalDate.of(2026, 7, 14), "");

    assertThatThrownBy(() -> service.generate(request))
        .isInstanceOf(KpiDataException.class)
        .hasMessage("Values cannot be negative.");
  }

  @Test
  void validatesGradingTotal() {
    PreviewRequest request = new PreviewRequest(
        LocalDate.of(2026, 7, 15),
        36, 10, 4, 3, 0, 30, 4, 35, 12, 8, 2, LocalDate.of(2026, 7, 14), "");

    assertThatThrownBy(() -> service.generate(request))
        .isInstanceOf(KpiDataException.class)
        .hasMessage("Gradings must equal Successful plus Failed.");
  }

  @Test
  void rejectsNegativeValues() {
    PreviewRequest request = new PreviewRequest(
        LocalDate.of(2026, 7, 15),
        36, 10, -1, 3, 0, 30, 4, 34, 12, 8, 2, LocalDate.of(2026, 7, 14), "");

    assertThatThrownBy(() -> service.generate(request))
        .isInstanceOf(KpiDataException.class)
        .hasMessage("Values cannot be negative.");
  }

  @Test
  void rejectsMissingKpiInputs() {
    PreviewRequest request = new PreviewRequest(
        LocalDate.of(2026, 7, 15),
        36, 10, 4, 3, null, 30, 4, 34, 12, 8, 2, LocalDate.of(2026, 7, 14), "");

    assertThatThrownBy(() -> service.generate(request))
        .isInstanceOf(KpiDataException.class)
        .hasMessage("KPI data is required.");
  }

  private PreviewRequest validRequest() {
    return new PreviewRequest(
        LocalDate.of(2026, 7, 15),
        36,
        10,
        4,
        3,
        0,
        30,
        4,
        34,
        12,
        8,
        2,
        LocalDate.of(2026, 7, 14),
        "");
  }
}
