package com.openforge.aimate.config;

import com.openforge.aimate.billing.InsufficientPointsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler(InsufficientPointsException.class)
    public ResponseEntity<Map<String, String>> insufficientPoints(InsufficientPointsException e) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(Map.of("error", "INSUFFICIENT_POINTS", "message", e.getMessage()));
    }
}
