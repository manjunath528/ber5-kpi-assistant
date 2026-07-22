package com.ber5.kpiassistant.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ber5")
public record Ber5Properties(
    DataMode dataMode,
    String appsScriptUrl,
    String appsScriptApiKey,
    Duration connectTimeout,
    Duration readTimeout) {

  public Ber5Properties {
    if (dataMode == null) {
      dataMode = DataMode.MOCK;
    }
    if (connectTimeout == null) {
      connectTimeout = Duration.ofSeconds(5);
    }
    if (readTimeout == null) {
      readTimeout = Duration.ofSeconds(10);
    }
  }
}
