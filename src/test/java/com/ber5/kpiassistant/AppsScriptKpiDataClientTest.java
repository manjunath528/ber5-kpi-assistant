package com.ber5.kpiassistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ber5.kpiassistant.config.Ber5Properties;
import com.ber5.kpiassistant.config.DataMode;
import com.ber5.kpiassistant.exception.KpiDataException;
import com.ber5.kpiassistant.model.KpiData;
import com.ber5.kpiassistant.service.AppsScriptKpiDataClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@ExtendWith(OutputCaptureExtension.class)
class AppsScriptKpiDataClientTest {

  @Test
  void follows302RedirectAndLoadsValidJson() throws IOException {
    try (TestServer server = new TestServer()) {
      server.redirectThenJson(validJson("""
          "receivedInOdoo": 6,
          "mdmLocked": 2,
          "fmiLocked": 1,
          "frpLocked": 1,
          "userLocked": 0,
          "successful": 4,
          "failed": 2,
          "gradingTotal": 6
          """));

      KpiData data = client(server.url("/exec")).load(LocalDate.of(2026, 7, 15));

      assertThat(data.receivedInOdoo()).isEqualTo(6);
      assertThat(data.totalLocked()).isEqualTo(4);
      assertThat(data.gradingTotal()).isEqualTo(6);
      assertThat(server.lastExecQuery()).contains("date=2026-07-15");
      assertThat(server.lastExecQuery()).contains("key=secret");
      assertThat(server.lastAcceptHeader()).contains("application/json");
      assertThat(server.redirectTargetCalled()).isTrue();
    }
  }

  @Test
  void followsMultipleRedirectsAndLoadsValidJson() throws IOException {
    try (TestServer server = new TestServer()) {
      server.multipleRedirectsThenJson(validJson("""
          "receivedInOdoo": 6,
          "mdmLocked": 2,
          "fmiLocked": 1,
          "frpLocked": 1,
          "userLocked": 0,
          "successful": 4,
          "failed": 2,
          "gradingTotal": 6
          """));

      KpiData data = client(server.url("/exec")).load(LocalDate.of(2026, 7, 15));

      assertThat(data.receivedInOdoo()).isEqualTo(6);
      assertThat(server.redirectTargetCalled()).isTrue();
    }
  }

  @Test
  void loadsSuccessfulJsonResponse() throws IOException {
    try (TestServer server = new TestServer()) {
      server.json(validJson("""
          "receivedInOdoo": 36,
          "mdmLocked": 10,
          "fmiLocked": 4,
          "frpLocked": 3,
          "userLocked": 0,
          "successful": 30,
          "failed": 4,
          "gradingTotal": 34
          """));

      KpiData data = client(server.url("/exec")).load(LocalDate.of(2026, 7, 15));

      assertThat(data.receivedInOdoo()).isEqualTo(36);
      assertThat(data.totalLocked()).isEqualTo(17);
      assertThat(data.gradingTotal()).isEqualTo(34);
      assertThat(server.lastExecQuery()).contains("date=2026-07-15");
      assertThat(server.lastAcceptHeader()).contains("application/json");
    }
  }

  @Test
  void rejectsHtmlReturnedInsteadOfJson() throws IOException {
    try (TestServer server = new TestServer()) {
      server.text("<html>Login required</html>", MediaType.TEXT_HTML_VALUE, 200);

      assertThatThrownBy(() -> client(server.url("/exec")).load(LocalDate.of(2026, 7, 15)))
          .isInstanceOf(KpiDataException.class)
          .hasMessage("Apps Script returned HTML instead of JSON.");
    }
  }

  @Test
  void reportsRedirectToGoogleAccountsAsAuthenticationRequired() throws IOException {
    try (TestServer server = new TestServer()) {
      server.redirectToGoogleAccounts();

      assertThatThrownBy(() -> client(server.url("/exec"), HttpClient.Redirect.NEVER).load(LocalDate.of(2026, 7, 15)))
          .isInstanceOf(KpiDataException.class)
          .hasMessage("Apps Script authentication is required.");
    }
  }

  @Test
  void rejectsEmptyResponse() throws IOException {
    try (TestServer server = new TestServer()) {
      server.text("   ", MediaType.APPLICATION_JSON_VALUE, 200);

      assertThatThrownBy(() -> client(server.url("/exec")).load(LocalDate.of(2026, 7, 15)))
          .isInstanceOf(KpiDataException.class)
          .hasMessage("Apps Script returned an empty response.");
    }
  }

  @Test
  void rejectsMalformedJsonResponse() throws IOException {
    try (TestServer server = new TestServer()) {
      server.text("{not valid json", MediaType.APPLICATION_JSON_VALUE, 200);

      assertThatThrownBy(() -> client(server.url("/exec")).load(LocalDate.of(2026, 7, 15)))
          .isInstanceOf(KpiDataException.class)
          .hasMessage("Apps Script returned malformed JSON.")
          .hasCauseInstanceOf(Exception.class);
    }
  }

  @Test
  void mapsSuccessFalseResponseToFriendlyMessage() throws IOException {
    try (TestServer server = new TestServer()) {
      server.json("""
          {
            "success": false,
            "error": "No KPI rows were found for the selected date."
          }
          """);

      assertThatThrownBy(() -> client(server.url("/exec")).load(LocalDate.of(2026, 7, 15)))
          .isInstanceOf(KpiDataException.class)
          .hasMessage("No KPI rows were found for the selected date.");
    }
  }

  @Test
  void ignoresUnknownExtraJsonProperties() throws IOException {
    try (TestServer server = new TestServer()) {
      server.json(validJson("""
          "receivedInOdoo": 6,
          "mdmLocked": 2,
          "fmiLocked": 1,
          "frpLocked": 1,
          "userLocked": 0,
          "successful": 4,
          "failed": 2,
          "gradingTotal": 6,
          "unexpectedExtraField": "ignored"
          """));

      KpiData data = client(server.url("/exec")).load(LocalDate.of(2026, 7, 15));

      assertThat(data.receivedInOdoo()).isEqualTo(6);
      assertThat(data.gradingTotal()).isEqualTo(6);
    }
  }

  @Test
  void acceptsJsonResponseWithUnusualContentType() throws IOException {
    try (TestServer server = new TestServer()) {
      server.text(validJson("""
          "receivedInOdoo": 6,
          "mdmLocked": 2,
          "fmiLocked": 0,
          "frpLocked": 2,
          "userLocked": 0,
          "successful": 5,
          "failed": 1,
          "gradingTotal": 6
          """), MediaType.TEXT_PLAIN_VALUE, 200);

      KpiData data = client(server.url("/exec")).load(LocalDate.of(2026, 7, 15));

      assertThat(data.receivedInOdoo()).isEqualTo(6);
      assertThat(data.gradingTotal()).isEqualTo(6);
    }
  }

  @Test
  void rejectsNon2xxStatus() throws IOException {
    try (TestServer server = new TestServer()) {
      server.text("{\"error\":\"server error\"}", MediaType.APPLICATION_JSON_VALUE, 500);

      assertThatThrownBy(() -> client(server.url("/exec")).load(LocalDate.of(2026, 7, 15)))
          .isInstanceOf(KpiDataException.class)
          .hasMessage("Apps Script returned non-success HTTP status: 500");
    }
  }

  @Test
  void rejectsMissingRequiredJsonFields() throws IOException {
    try (TestServer server = new TestServer()) {
      server.json("""
          {
            "success": true,
            "reportDate": "2026-07-15",
            "receivedInOdoo": 36
          }
          """);

      assertThatThrownBy(() -> client(server.url("/exec")).load(LocalDate.of(2026, 7, 15)))
          .isInstanceOf(KpiDataException.class)
          .hasMessageContaining("Apps Script response is missing required JSON fields");
    }
  }

  @Test
  void doesNotWriteApiKeyToLogs(CapturedOutput output) throws IOException {
    try (TestServer server = new TestServer()) {
      server.json(validJson("""
          "receivedInOdoo": 6,
          "mdmLocked": 2,
          "fmiLocked": 1,
          "frpLocked": 1,
          "userLocked": 0,
          "successful": 4,
          "failed": 2,
          "gradingTotal": 6
          """));

      client(server.url("/exec")).load(LocalDate.of(2026, 7, 15));

      assertThat(output).doesNotContain("secret");
      assertThat(output).contains("key=***");
    }
  }

  @Test
  void reportsMissingConfiguration() {
    AppsScriptKpiDataClient unconfigured = new AppsScriptKpiDataClient(
        new Ber5Properties(DataMode.APPS_SCRIPT, "", "", Duration.ofSeconds(1), Duration.ofSeconds(1)),
        RestClient.create(),
        new ObjectMapper().findAndRegisterModules());

    assertThatThrownBy(() -> unconfigured.load(LocalDate.of(2026, 7, 15)))
        .isInstanceOf(KpiDataException.class)
        .hasMessage("Google Sheet connection is not configured.");
  }

  private AppsScriptKpiDataClient client(String appsScriptUrl) {
    return client(appsScriptUrl, HttpClient.Redirect.ALWAYS);
  }

  private AppsScriptKpiDataClient client(String appsScriptUrl, HttpClient.Redirect redirect) {
    HttpClient httpClient = HttpClient.newBuilder()
        .followRedirects(redirect)
        .connectTimeout(Duration.ofSeconds(1))
        .build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(Duration.ofSeconds(1));

    return new AppsScriptKpiDataClient(
        new Ber5Properties(DataMode.APPS_SCRIPT, appsScriptUrl, "secret", Duration.ofSeconds(1), Duration.ofSeconds(1)),
        RestClient.builder().requestFactory(requestFactory).build(),
        new ObjectMapper().findAndRegisterModules());
  }

  private String validJson(String fields) {
    return """
        {
          "success": true,
          "reportDate": "2026-07-15",
          %s
        }
        """.formatted(fields);
  }

  private static class TestServer implements AutoCloseable {

    private final HttpServer server;
    private final ExecutorService executor;
    private String lastExecQuery;
    private String lastAcceptHeader;
    private boolean redirectTargetCalled;

    TestServer() throws IOException {
      server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      executor = Executors.newSingleThreadExecutor();
      server.setExecutor(executor);
      server.start();
    }

    String url(String path) {
      return "http://localhost:" + server.getAddress().getPort() + path;
    }

    String lastExecQuery() {
      return lastExecQuery;
    }

    String lastAcceptHeader() {
      return lastAcceptHeader;
    }

    boolean redirectTargetCalled() {
      return redirectTargetCalled;
    }

    void json(String body) {
      text(body, MediaType.APPLICATION_JSON_VALUE, 200);
    }

    void text(String body, String contentType, int status) {
      server.createContext("/exec", exchange -> {
        captureRequest(exchange);
        send(exchange, status, contentType, body);
      });
    }

    void redirectThenJson(String body) {
      server.createContext("/exec", exchange -> {
        captureRequest(exchange);
        exchange.getResponseHeaders().add("Location", url("/redirected"));
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
      });
      server.createContext("/redirected", exchange -> {
        redirectTargetCalled = true;
        send(exchange, 200, MediaType.APPLICATION_JSON_VALUE, body);
      });
    }

    void multipleRedirectsThenJson(String body) {
      server.createContext("/exec", exchange -> {
        captureRequest(exchange);
        exchange.getResponseHeaders().add("Location", url("/redirect-one"));
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
      });
      server.createContext("/redirect-one", exchange -> {
        exchange.getResponseHeaders().add("Location", url("/redirect-two"));
        exchange.sendResponseHeaders(303, -1);
        exchange.close();
      });
      server.createContext("/redirect-two", exchange -> {
        redirectTargetCalled = true;
        send(exchange, 200, MediaType.APPLICATION_JSON_VALUE, body);
      });
    }

    void redirectToGoogleAccounts() {
      server.createContext("/exec", exchange -> {
        captureRequest(exchange);
        exchange.getResponseHeaders().add("Location", "https://accounts.google.com/signin");
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
      });
    }

    private void captureRequest(HttpExchange exchange) {
      lastExecQuery = exchange.getRequestURI().getRawQuery();
      lastAcceptHeader = exchange.getRequestHeaders().getFirst("Accept");
    }

    private void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", contentType);
      exchange.sendResponseHeaders(status, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    }

    @Override
    public void close() {
      server.stop(0);
      executor.shutdownNow();
    }
  }
}
