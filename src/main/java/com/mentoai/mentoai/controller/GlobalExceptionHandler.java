package com.mentoai.mentoai.controller;

import com.mentoai.mentoai.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("IllegalArgumentException: {} - Path: {}", ex.getMessage(), request.getRequestURI());
        
        Map<String, Object> errorResponse = createErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                ex.getMessage(),
                request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(
            UnauthorizedException ex, HttpServletRequest request) {
        log.warn("UnauthorizedException: {} - Path: {}", ex.getMessage(), request.getRequestURI());
        
        Map<String, Object> errorResponse = createErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                ex.getMessage(),
                request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(
            Exception ex, HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean isSwaggerPath = path.startsWith("/v3/api-docs") || 
                                 path.startsWith("/swagger-ui") || 
                                 path.startsWith("/docs");
        
        // Swagger 관련 에러는 더 상세하게 로깅
        if (isSwaggerPath) {
            log.error("Swagger API Docs Error - Path: {}, Method: {}", 
                    path, request.getMethod(), ex);
        } else {
            log.error("Unexpected error occurred - Path: {}, Method: {}", 
                    path, request.getMethod(), ex);
        }
        
        Map<String, Object> errorResponse = createErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                ex.getMessage() != null ? ex.getMessage() : "An unexpected error occurred",
                path
        );
        
        // 개발 환경에서는 스택 트레이스도 포함 (Swagger 경로인 경우)
        if (isSwaggerPath) {
            errorResponse.put("exception", ex.getClass().getName());
            errorResponse.put("stackTrace", getStackTrace(ex));
        }
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    private Map<String, Object> createErrorResponse(
            int status, String error, String message, String path) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("status", status);
        response.put("error", error);
        response.put("message", message);
        response.put("path", path);
        return response;
    }

    private String[] getStackTrace(Exception ex) {
        StackTraceElement[] stackTrace = ex.getStackTrace();
        String[] result = new String[Math.min(stackTrace.length, 20)]; // 최대 20줄만
        for (int i = 0; i < result.length; i++) {
            result[i] = stackTrace[i].toString();
        }
        return result;
    }
}
