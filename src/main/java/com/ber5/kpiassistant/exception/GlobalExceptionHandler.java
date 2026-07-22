package com.ber5.kpiassistant.exception;

import com.ber5.kpiassistant.model.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(KpiDataException.class)
  ResponseEntity<ErrorResponse> handleKpiDataException(KpiDataException exception) {
    log.error("KPI data request failed: {}", exception.getMessage(), exception);
    return ResponseEntity.status(exception.status()).body(new ErrorResponse(exception.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> handleValidationException() {
    return ResponseEntity.badRequest().body(new ErrorResponse("Please check the entered values."));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
    log.error("Unexpected KPI Assistant error", exception);
    return ResponseEntity.internalServerError().body(new ErrorResponse("Unable to read KPI data."));
  }
}
