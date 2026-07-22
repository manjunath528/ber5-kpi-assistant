package com.ber5.kpiassistant;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class KpiApiControllerTest {

  @Autowired
  MockMvc mockMvc;

  @Test
  void mockKpiEndpointLoadsData() throws Exception {
    mockMvc.perform(get("/api/kpi").param("date", "2026-07-15"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reportDate").value("2026-07-15"))
        .andExpect(jsonPath("$.receivedInOdoo").value(36))
        .andExpect(jsonPath("$.gradingTotal").value(34));
  }

  @Test
  void previewEndpointGeneratesMessage() throws Exception {
    mockMvc.perform(post("/api/preview")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "reportDate": "2026-07-15",
                  "receivedInOdoo": 36,
                  "mdmLocked": 10,
                  "fmiLocked": 4,
                  "frpLocked": 3,
                  "userLocked": 0,
                  "successful": 30,
                  "failed": 4,
                  "gradingTotal": 34,
                  "currentMdmLockedTotal": 12,
                  "gradingBacklog": 8,
                  "unpackedReturnPackages": 2,
                  "unpackedReturnPackagesDate": "2026-07-14",
                  "notes": "Keine weiteren Auffälligkeiten."
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message", containsString("Retoure Zahlen vom 15.07.2026")))
        .andExpect(jsonPath("$.message", containsString("Locked: 17\n10 MDM locked bei Vereinnahmung (aktuell noch 12 ges.)")))
        .andExpect(jsonPath("$.message", containsString("Grading Backlog: 8")))
        .andExpect(jsonPath("$.message", containsString("2 Retourenpakete vom 14.07.2026 sind noch nicht vereinnahmt")))
        .andExpect(jsonPath("$.message", containsString("Anmerkungen:\nKeine weiteren Auffälligkeiten.")));
  }

  @Test
  void previewEndpointRejectsMissingManualInput() throws Exception {
    mockMvc.perform(post("/api/preview")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "reportDate": "2026-07-15",
                  "receivedInOdoo": 36,
                  "mdmLocked": 10,
                  "fmiLocked": 4,
                  "frpLocked": 3,
                  "successful": 30,
                  "failed": 4,
                  "gradingTotal": 34,
                  "gradingBacklog": 8
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(content().string(containsString("KPI data is required.")));
  }
}
