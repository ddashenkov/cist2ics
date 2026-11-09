package com.ddashekov.cist2ics;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Fetches a teacher schedule from CIST and converts it to a {@link Calendar}.
 *
 * <p>The CIST CSV export has a header row with Russian column names, {@code \r} line breaks,
 * dates in the {@code dd.MM.yyyy} format, and times in Kyiv local time.
 */
@Component
public final class CalendarConverter {

    static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");

    private static final Logger log = LoggerFactory.getLogger(CalendarConverter.class);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private static final String START_DATE = "Дата начала";
    private static final String START_TIME = "Время начала";
    private static final String END_DATE = "Дата завершения";
    private static final String END_TIME = "Время завершения";
    private static final String DESCRIPTION = "Описание";

    private static final CSVFormat CIST_CSV = CSVFormat.DEFAULT.builder()
                                                                .setHeader()
                                                                .setSkipHeaderRecord(true)
                                                                .build();

    private final TeacherDirectory directory;
    private final CistClient cist;
    private final Clock clock;

    public CalendarConverter(TeacherDirectory directory, CistClient cist, Clock clock) {
        this.directory = directory;
        this.cist = cist;
        this.clock = clock;
    }

    /**
     * Fetches the schedule of the current academic year, from 1 September to 31 August.
     *
     * @throws TeacherNotFoundException if a name does not match the CIST directory
     * @throws CistUnavailableException if CIST does not return usable data
     */
    public Calendar fetchCalendar(String faculty, String department, String teacher) {
        var ref = directory.find(faculty, department, teacher);
        var today = LocalDate.now(clock.withZone(KYIV));
        var startYear = today.getMonth().compareTo(Month.SEPTEMBER) >= 0
                        ? today.getYear()
                        : today.getYear() - 1;
        var from = LocalDate.of(startYear, Month.SEPTEMBER, 1);
        var to = LocalDate.of(startYear + 1, Month.AUGUST, 31);
        log.info("Fetching the schedule of {} ({}), {} to {}.", ref.shortName(), ref.teacherId(), from, to);
        var csv = cist.fetchTeacherCsv(ref.teacherId(), from, to);
        return parse(csv, ref.shortName());
    }

    /**
     * Parses a CIST CSV export.
     *
     * @throws CistUnavailableException if the CSV does not have the expected columns
     */
    static Calendar parse(String csv, String title) {
        if (csv.isBlank()) {
            return new Calendar(title, List.of());
        }
        var events = new ArrayList<Event>();
        try (var parser = CIST_CSV.parse(new StringReader(csv))) {
            var header = parser.getHeaderNames();
            for (var column : List.of(START_DATE, START_TIME, END_DATE, END_TIME, DESCRIPTION)) {
                if (!header.contains(column)) {
                    throw new CistUnavailableException(
                            "The CIST CSV has no column \"%s\". Columns: %s.".formatted(column, header));
                }
            }
            for (var record : parser) {
                toEvent(record).ifPresent(events::add);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new Calendar(title, events);
    }

    private static Optional<Event> toEvent(CSVRecord record) {
        if (!record.isConsistent()) {
            log.warn("Skipping CSV line {}: {} columns.", record.getRecordNumber(), record.size());
            return Optional.empty();
        }
        var details = record.get(DESCRIPTION).strip();
        if (details.isEmpty()) {
            log.warn("Skipping CSV line {}: no description.", record.getRecordNumber());
            return Optional.empty();
        }
        try {
            var start = dateTime(record.get(START_DATE), record.get(START_TIME));
            var end = dateTime(record.get(END_DATE), record.get(END_TIME));
            return Optional.of(new Event(summary(details), start, end, details));
        } catch (DateTimeParseException e) {
            log.warn("Skipping CSV line {}: {}", record.getRecordNumber(), e.getMessage());
            return Optional.empty();
        }
    }

    private static ZonedDateTime dateTime(String date, String time) {
        return ZonedDateTime.of(LocalDateTime.of(LocalDate.parse(date.strip(), DATE),
                                                 LocalTime.parse(time.strip(), TIME)),
                                KYIV);
    }

    /**
     * Returns the subject and the lesson type, the first two words of the description.
     *
     * <p>The description has the format {@code <subject> <type> [DL] <group>;<group>…},
     * for example {@code ВМ Лк DL КІУКІу-26-1;КІУКІу-26-2} gives {@code ВМ Лк}.
     */
    private static String summary(String details) {
        var words = SPACES.split(details);
        return words.length < 2 ? details : words[0] + " " + words[1];
    }
}
