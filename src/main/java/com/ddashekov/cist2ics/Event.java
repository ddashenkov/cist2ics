package com.ddashekov.cist2ics;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

public record Event(String title, LocalDateTime start, LocalDateTime end, String details) {

    /**
     * Time zone offset in Kyiv, Ukraine, ignoring the summer light saving fluctuations.
     */
    private static final ZoneOffset KYIV = ZoneOffset.ofHours(2);

    public String id() {
        return "" + title.hashCode() + start.toInstant(KYIV).getEpochSecond();
    }
}
