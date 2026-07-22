package com.ber5.kpiassistant;

import com.ber5.kpiassistant.config.Ber5Properties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(Ber5Properties.class)
public class Ber5KpiAssistantApplication {

  public static void main(String[] args) {
    SpringApplication.run(Ber5KpiAssistantApplication.class, args);
  }
}
