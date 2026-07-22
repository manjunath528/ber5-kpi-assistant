package com.ber5.kpiassistant.controller;

import com.ber5.kpiassistant.model.KpiData;
import com.ber5.kpiassistant.model.ModeResponse;
import com.ber5.kpiassistant.model.PreviewRequest;
import com.ber5.kpiassistant.model.PreviewResponse;
import com.ber5.kpiassistant.service.KpiDataService;
import com.ber5.kpiassistant.service.SlackPreviewService;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class KpiApiController {

  private final KpiDataService kpiDataService;
  private final SlackPreviewService slackPreviewService;

  public KpiApiController(KpiDataService kpiDataService, SlackPreviewService slackPreviewService) {
    this.kpiDataService = kpiDataService;
    this.slackPreviewService = slackPreviewService;
  }

  @GetMapping("/mode")
  ModeResponse mode() {
    return new ModeResponse(kpiDataService.mode().name());
  }

  @GetMapping("/kpi")
  KpiData kpi(@RequestParam LocalDate date) {
    return kpiDataService.load(date);
  }

  @PostMapping("/preview")
  PreviewResponse preview(@RequestBody PreviewRequest request) {
    return new PreviewResponse(slackPreviewService.generate(request));
  }
}
