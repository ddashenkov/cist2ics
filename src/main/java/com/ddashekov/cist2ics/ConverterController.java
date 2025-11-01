package com.ddashekov.cist2ics;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;

@RestController
public class ConverterController {

    private final CalendarConverter converter;

    public ConverterController(CalendarConverter converter) {
        this.converter = converter;
    }

    @GetMapping(value = "/teacher/{userHandle}/calendar.ics",
                produces = APPLICATION_OCTET_STREAM_VALUE)
    public byte[] convert(@PathVariable @Pattern(regexp = ".{3,}") String userHandle) {

    }
}
