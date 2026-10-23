package com.openforge.aimate.config;

import com.openforge.aimate.billing.InsufficientPointsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST 异常直接写成 JSON，避免落到 /error 后再被 Security 伪装成 401。
 */
@Slf4j
@RestControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler(InsufficientPointsException.class)
    public ResponseEntity<Map<String, String>> insufficientPoints(InsufficientPointsException e) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(Map.of("error", "INSUFFICIENT_POINTS", "message", e.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> responseStatus(ResponseStatusException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", e.getStatusCode().toString());
        body.put("message", e.getReason() != null ? e.getReason() : e.getMessage());
        return ResponseEntity.status(e.getStatusCode()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unhandled(Exception e) {
        log.error("[API] unhandled {}", e.toString(), e);
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = e.getClass().getSimpleName();
        }
        // 截断，避免把整段 COS/堆栈吐给前端
        if (message.length() > 500) {
            message = message.substring(0, 500);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", e.getClass().getSimpleName(),
                        "message", message
                ));
    }
}
