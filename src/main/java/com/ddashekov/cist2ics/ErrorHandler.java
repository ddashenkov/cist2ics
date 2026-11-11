package com.ddashekov.cist2ics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.nio.charset.StandardCharsets;

/**
 * Maps the application exceptions to HTTP statuses with a plain text message.
 */
@RestControllerAdvice
class ErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);

    @ExceptionHandler(TeacherNotFoundException.class)
    ResponseEntity<String> notFound(TeacherNotFoundException e) {
        return plainText(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(CistUnavailableException.class)
    ResponseEntity<String> cistUnavailable(CistUnavailableException e) {
        log.error("CIST is unavailable: {}", e.getMessage());
        return plainText(HttpStatus.BAD_GATEWAY, "CIST is unavailable. Try again later.");
    }

    @ExceptionHandler(SerializationException.class)
    ResponseEntity<String> serializationFailed(SerializationException e) {
        log.atError().setCause(e).log("Failed to serialize a calendar.");
        return plainText(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to build the calendar.");
    }

    private static ResponseEntity<String> plainText(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                             .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                             .body(message);
    }
}
