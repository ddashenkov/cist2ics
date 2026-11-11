package com.ddashekov.cist2ics;

/**
 * Thrown when the faculty, the department, or the teacher name does not match the CIST directory.
 */
public final class TeacherNotFoundException extends RuntimeException {

    public TeacherNotFoundException(String level, String name) {
        super("Unknown %s: \"%s\".".formatted(level, name));
    }
}
