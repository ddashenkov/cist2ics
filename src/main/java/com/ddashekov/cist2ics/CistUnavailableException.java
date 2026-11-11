package com.ddashekov.cist2ics;

/**
 * Thrown when CIST does not return usable data: a network failure, an error status,
 * an Oracle backend error, or a body in an unexpected format.
 */
public final class CistUnavailableException extends RuntimeException {

    public CistUnavailableException(String message) {
        super(message);
    }

    public CistUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
