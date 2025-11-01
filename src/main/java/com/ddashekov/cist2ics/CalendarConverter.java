package com.ddashekov.cist2ics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.UnsupportedEncodingException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

@Component
public final class CalendarConverter {

    private static final Logger log = LoggerFactory.getLogger(CalendarConverter.class);

    private static final Pattern NEW_LINE = Pattern.compile("\n");
    private static final Pattern ROW =
            Pattern.compile("\"(.+?)\",\"(.+?)\",\"(.+?)\",\"(.+?)\",\"(.+?)\".*");

    private final String baseUrl;
    private final RestTemplate http;

    public CalendarConverter(@Value("${cist.base-url}") String baseUrl) {
        this.baseUrl = baseUrl;
        this.http = new RestTemplate();
    }

    public Optional<Calendar> fetchCalendar(String userHandle) {
        log.info("Fetching calendar for user {}", userHandle);

        var url = baseUrl + userHandle;
        ResponseEntity<byte[]> response;
        try {
            response = http.getForEntity(url, byte[].class);
        } catch (RestClientException e) {
            log.atError().setCause(e).log("Failed to query CIST: {}", e.getMessage());
            return Optional.empty();
        }
        var rawBody = parseBody(response);
        if (!response.getStatusCode().is2xxSuccessful()) {
            log.atError().log("CIST query failed with response {}: {}",
                              response.getStatusCode(), rawBody);
            return Optional.empty();
        }
        var calendar = NEW_LINE
                .splitAsStream(rawBody)
                .map(ROW::matcher)
                .filter(Matcher::matches)
                .map(matcher -> {
                    var details = matcher.group(1);
                    var startDate = matcher.group(2);
                    var startTime = matcher.group(3);
                    var endDate = matcher.group(4);
                    var endTime = matcher.group(5);
                    var words = details.split(" ");
                    var title = words.length > 0 ? words[0] : details;
                    var start = LocalDateTime.of(LocalDate.parse(startDate),
                                                 LocalTime.parse(startTime));
                    var end = LocalDateTime.of(LocalDate.parse(endDate),
                                               LocalTime.parse(endTime));
                    return new Event(title, start, end, details);
                })
                .collect(collectingAndThen(toList(), events -> new Calendar("CIST", events)));
        return calendar.notEmpty() ? Optional.of(calendar) : Optional.empty();
    }

    private static String parseBody(ResponseEntity<byte[]> response) {
        var body = response.getBody();
        if (body == null || body.length == 0) {
            return "";
        }
        try {
            return new String(body, "windows-1251");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
