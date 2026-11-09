package com.ddashekov.cist2ics;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@RestController
public final class ConverterController {

    static final MediaType TEXT_CALENDAR = new MediaType("text", "calendar", StandardCharsets.UTF_8);

    private final CalendarService calendars;
    private final Serializer serializer;

    public ConverterController(CalendarService calendars, Serializer serializer) {
        this.calendars = calendars;
        this.serializer = serializer;
    }

    /**
     * Returns the schedule of one teacher as an iCalendar feed.
     *
     * <p>Each name can be the short or the full CIST name, in Ukrainian. Spring MVC answers
     * {@code 304 Not Modified} when the {@code If-None-Match} header matches the entity tag.
     */
    @GetMapping("/teacher/calendar.ics")
    public ResponseEntity<byte[]> convert(@RequestParam @NotBlank String faculty,
                                          @RequestParam @NotBlank String department,
                                          @RequestParam @NotBlank String teacher) {
        var calendar = calendars.calendar(faculty, department, teacher);
        var body = serializer.serialize(calendar).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                             .contentType(TEXT_CALENDAR)
                             .header("Content-Disposition",
                                     ContentDisposition.inline()
                                                       .filename("calendar.ics")
                                                       .build()
                                                       .toString())
                             // DTSTAMP changes on each response, so the tag is weak.
                             .eTag("W/\"" + calendar.hash() + "\"")
                             .cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
                             .body(body);
    }
}
