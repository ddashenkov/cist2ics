package com.ddashekov.cist2ics;

import java.time.ZonedDateTime;

/**
 * One lesson.
 *
 * @param title   the short summary, for example {@code ВМ Лк}
 * @param start   the start time in the {@code Europe/Kyiv} zone
 * @param end     the end time in the {@code Europe/Kyiv} zone
 * @param details the full CIST description, for example {@code ВМ Лк DL КІУКІу-26-1;КІУКІу-26-2}
 */
public record Event(String title, ZonedDateTime start, ZonedDateTime end, String details) {

    /**
     * Returns a stable iCalendar UID.
     *
     * <p>Calendar clients use the UID to match events between refreshes, so the UID depends only
     * on the title and the start instant.
     */
    public String id() {
        return "%08x-%d@cist2ics".formatted(title.hashCode(), start.toEpochSecond());
    }
}
