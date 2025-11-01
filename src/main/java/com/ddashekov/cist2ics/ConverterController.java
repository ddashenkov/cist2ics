package com.ddashekov.cist2ics;

import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;

@RestController
public final class ConverterController {

    private final CalendarConverter converter;
    private final Serializer serializer;

    public ConverterController(CalendarConverter converter, Serializer serializer) {
        this.converter = converter;
        this.serializer = serializer;
    }

    @GetMapping(value = "/teacher/{userHandle}/calendar.ics",
                produces = APPLICATION_OCTET_STREAM_VALUE)
    public byte[] convert(@PathVariable @Pattern(regexp = ".{3,}") String userHandle) {
        return converter.fetchCalendar(userHandle)
                        .map(serializer::serialize)
                        .map(s -> s.getBytes(StandardCharsets.UTF_8))
                        .orElseGet(() -> new byte[0]);
    }
}
