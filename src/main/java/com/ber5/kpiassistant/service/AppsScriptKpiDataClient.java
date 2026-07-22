package com.ber5.kpiassistant.service;

import com.ber5.kpiassistant.config.Ber5Properties;
import com.ber5.kpiassistant.exception.KpiDataException;
import com.ber5.kpiassistant.model.AppsScriptResponse;
import com.ber5.kpiassistant.model.KpiData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class AppsScriptKpiDataClient implements KpiDataClient {

  private static final Logger log = LoggerFactory.getLogger(AppsScriptKpiDataClient.class);
  private static final int LOG_BODY_LIMIT = 500;

  private final Ber5Properties properties;
  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  public AppsScriptKpiDataClient(Ber5Properties properties, RestClient restClient, ObjectMapper objectMapper) {
    this.properties = properties;
    this.restClient = restClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public KpiData load(LocalDate reportDate) {
    if (!StringUtils.hasText(properties.appsScriptUrl()) || !StringUtils.hasText(properties.appsScriptApiKey())) {
      throw new KpiDataException(HttpStatus.BAD_REQUEST, "Google Sheet connection is not configured.");
    }

    AppsScriptResponse response;
    try {
      String uri = UriComponentsBuilder.fromHttpUrl(properties.appsScriptUrl())
          .queryParam("date", reportDate)
          .queryParam("key", properties.appsScriptApiKey())
          .toUriString();
      URI requestUri = URI.create(uri);
      log.info(
          "Calling Apps Script KPI endpoint. host={}, path={}, reportDate={}, url={}",
          requestUri.getHost(),
          requestUri.getPath(),
          reportDate,
          maskedUri(uri));

      AppsScriptHttpResponse httpResponse = restClient.get()
          .uri(uri)
          .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
          .exchange((request, clientResponse) -> new AppsScriptHttpResponse(
              clientResponse.getStatusCode(),
              clientResponse.getHeaders().getContentType(),
              StreamUtils.copyToString(clientResponse.getBody(), java.nio.charset.StandardCharsets.UTF_8),
              clientResponse.getHeaders().getLocation()));

      log.info(
          "Apps Script KPI response. status={}, contentType={}, finalResponseUri={}, redirectOccurred={}",
          httpResponse.status(),
          httpResponse.contentType(),
          safeUri(httpResponse.finalResponseUri()),
          redirectOccurred(requestUri, httpResponse.finalResponseUri()));

      response = parseResponse(httpResponse);
    } catch (RestClientException exception) {
      log.error("Apps Script HTTP request failed: {}", exception.getMessage(), exception);
      throw new KpiDataException(HttpStatus.BAD_GATEWAY, "Unable to read KPI data.", exception);
    }

    if (response == null) {
      throw new KpiDataException(HttpStatus.BAD_GATEWAY, "The Apps Script endpoint returned an invalid response.");
    }
    if (!Boolean.TRUE.equals(response.success())) {
      throw new KpiDataException(HttpStatus.BAD_REQUEST, friendlyMessage(response));
    }

    validateRequiredResponse(response);
    validateNonNegativeResponse(response);

    return new KpiData(
        Objects.requireNonNull(response.reportDate()),
        response.receivedInOdoo(),
        response.mdmLocked(),
        response.fmiLocked(),
        response.frpLocked(),
        response.userLocked() == null ? 0 : response.userLocked(),
        response.successful(),
        response.failed(),
        response.gradingTotal());
  }

  private AppsScriptResponse parseResponse(AppsScriptHttpResponse httpResponse) {
    HttpStatusCode status = httpResponse.status();
    String rawBody = httpResponse.body();

    if (status.is3xxRedirection()) {
      if (isAccountsUri(httpResponse.finalResponseUri())) {
        log.error("Apps Script redirected to Google login. status={}, location={}", status, safeUri(httpResponse.finalResponseUri()));
        throw new KpiDataException(HttpStatus.BAD_GATEWAY, "Apps Script authentication is required.");
      }
      log.error(
          "Apps Script redirect was not followed. status={}, location={}, bodyPreview={}",
          status,
          safeUri(httpResponse.finalResponseUri()),
          bodyPreview(rawBody));
      throw new KpiDataException(
          HttpStatus.BAD_GATEWAY,
          "Apps Script redirect was not followed. Check HTTP redirect handling.");
    }

    if (!status.is2xxSuccessful()) {
      log.error(
          "Apps Script returned non-2xx response. status={}, contentType={}, bodyPreview={}",
          status,
          httpResponse.contentType(),
          bodyPreview(rawBody));
      throw new KpiDataException(
          HttpStatus.BAD_GATEWAY,
          "Apps Script returned non-success HTTP status: " + status.value());
    }

    if (!StringUtils.hasText(rawBody)) {
      log.error("Apps Script returned an empty response body.");
      throw new KpiDataException(HttpStatus.BAD_GATEWAY, "Apps Script returned an empty response.");
    }

    if (isAccountsUri(httpResponse.finalResponseUri())) {
      log.error("Apps Script final response URI points to Google login: {}", safeUri(httpResponse.finalResponseUri()));
      throw new KpiDataException(HttpStatus.BAD_GATEWAY, "Apps Script authentication is required.");
    }

    String trimmedBody = rawBody.trim();

    if (isHtmlResponse(trimmedBody)) {
      log.error(
          "Apps Script returned HTML instead of JSON. contentType={}, bodyPreview={}",
          httpResponse.contentType(),
          bodyPreview(rawBody));
      throw new KpiDataException(HttpStatus.BAD_GATEWAY, "Apps Script returned HTML instead of JSON.");
    }

    try {
      return objectMapper.readValue(trimmedBody, AppsScriptResponse.class);
    } catch (JsonProcessingException exception) {
      log.error("Malformed Apps Script JSON response. bodyPreview={}", bodyPreview(rawBody), exception);
      throw new KpiDataException(
          HttpStatus.BAD_GATEWAY,
          "Apps Script returned malformed JSON.",
          exception);
    }
  }

  private String maskedUri(String uri) {
    return UriComponentsBuilder.fromUriString(uri)
        .replaceQueryParam("key", "***")
        .toUriString();
  }

  private boolean isHtmlResponse(String rawBody) {
    String trimmedBody = rawBody.toLowerCase();
    return trimmedBody.startsWith("<!doctype html")
        || trimmedBody.startsWith("<html");
  }

  private String redirectOccurred(URI requestUri, URI finalResponseUri) {
    if (finalResponseUri == null) {
      return "not available";
    }
    return Boolean.toString(!Objects.equals(requestUri.getHost(), finalResponseUri.getHost())
        || !Objects.equals(requestUri.getPath(), finalResponseUri.getPath()));
  }

  private boolean isAccountsUri(URI uri) {
    return uri != null && "accounts.google.com".equalsIgnoreCase(uri.getHost());
  }

  private String safeUri(URI uri) {
    if (uri == null) {
      return "not available";
    }
    return UriComponentsBuilder.newInstance()
        .scheme(uri.getScheme())
        .host(uri.getHost())
        .path(uri.getPath())
        .build()
        .toUriString();
  }

  private String bodyPreview(String body) {
    if (body == null) {
      return "";
    }
    String compact = body.replaceAll("\\s+", " ").trim();
    return compact.length() <= LOG_BODY_LIMIT ? compact : compact.substring(0, LOG_BODY_LIMIT);
  }

  private String friendlyMessage(AppsScriptResponse response) {
    String text = ((response.error() == null ? "" : response.error()) + " "
        + (response.message() == null ? "" : response.message())).toLowerCase();
    if (text.contains("no rows") || text.contains("no kpi rows")) {
      return "No KPI rows were found for the selected date.";
    }
    if (text.contains("date") && (text.contains("not exist") || text.contains("not found"))) {
      return "The selected date does not exist in the sheet.";
    }
    if (text.contains("configured") || text.contains("spreadsheet")) {
      return "Google Sheet connection is not configured.";
    }
    return "Unable to read KPI data.";
  }

  private void validateRequiredResponse(AppsScriptResponse response) {
    List<String> missingFields = new ArrayList<>();
    if (response.reportDate() == null) {
      missingFields.add("reportDate");
    }
    if (response.receivedInOdoo() == null) {
      missingFields.add("receivedInOdoo");
    }
    if (response.mdmLocked() == null) {
      missingFields.add("mdmLocked");
    }
    if (response.fmiLocked() == null) {
      missingFields.add("fmiLocked");
    }
    if (response.frpLocked() == null) {
      missingFields.add("frpLocked");
    }
    if (response.userLocked() == null) {
      missingFields.add("userLocked");
    }
    if (response.successful() == null) {
      missingFields.add("successful");
    }
    if (response.failed() == null) {
      missingFields.add("failed");
    }
    if (response.gradingTotal() == null) {
      missingFields.add("gradingTotal");
    }

    if (!missingFields.isEmpty()) {
      log.error("Apps Script response is missing required JSON fields: {}", missingFields);
      throw new KpiDataException(
          HttpStatus.BAD_GATEWAY,
          "Apps Script response is missing required JSON fields: " + String.join(", ", missingFields));
    }
  }

  private void validateNonNegativeResponse(AppsScriptResponse response) {
    if (response.receivedInOdoo() < 0
        || response.mdmLocked() < 0
        || response.fmiLocked() < 0
        || response.frpLocked() < 0
        || (response.userLocked() != null && response.userLocked() < 0)
        || response.successful() < 0
        || response.failed() < 0
        || response.gradingTotal() < 0) {
      throw new KpiDataException(HttpStatus.BAD_GATEWAY, "The Apps Script endpoint returned an invalid response.");
    }
  }

  private record AppsScriptHttpResponse(
      HttpStatusCode status,
      MediaType contentType,
      String body,
      URI finalResponseUri) {
  }
}
