package com.uci.pkbe.web;

import com.uci.pkbe.web.dto.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handle(ApiException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(new ErrorResponse(exception.getError(), exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
        return ResponseEntity.status(500)
                .body(new ErrorResponse("INTERNAL", exception.getMessage()));
    }
}
