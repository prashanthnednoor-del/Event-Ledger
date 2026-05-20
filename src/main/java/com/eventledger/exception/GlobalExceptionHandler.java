package com.eventledger.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<String> messages = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(400, "Validation Failed", messages));
    }

    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(EventNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(404, "Not Found", List.of(ex.getMessage())));
    }

    // Catches malformed JSON and type mismatches (e.g. invalid ISO 8601 timestamp)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        String detail = ex.getMostSpecificCause().getMessage();
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(400, "Malformed request body", List.of(detail)));
    }

    // Catches missing required query parameters (e.g. GET /events without ?account=)
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        String message = "Required parameter '" + ex.getParameterName() + "' is missing";
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(400, "Missing Parameter", List.of(message)));
    }

    // Catches wrong type for query parameters (e.g. ?page=abc instead of an integer)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        Class<?> required = ex.getRequiredType();
        String typeName = required != null ? required.getSimpleName() : "expected type";
        String message = "Parameter '" + ex.getName() + "' must be of type " + typeName;
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(400, "Invalid Parameter Type", List.of(message)));
    }

    // Catches unsupported HTTP methods (e.g. DELETE /events/123)
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        String message = "Method '" + ex.getMethod() + "' is not supported on this endpoint";
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ErrorResponse(405, "Method Not Allowed", List.of(message)));
    }

    // Internal errors — message is intentionally withheld from the client
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse(500, "Internal Server Error",
                        List.of("An unexpected error occurred")));
    }
}
