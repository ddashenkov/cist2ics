package com.ddashekov.cist2ics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("icsSerializerTest")
class IcsSerializerTest {

    private final IcsSerializer serializer = new IcsSerializer();

    @Test
    @DisplayName("throw an exception on null calendar")
    void refuseNull() {
        assertThatThrownBy(() -> serializer.serialize(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("serializeSingleEventContainsBasicIcsFields")
    void singleEvent() {
        var start = LocalDateTime.of(2025, 1, 2, 9, 30);
        var end = LocalDateTime.of(2025, 1, 2, 11, 0);
        var event = new Event("Algorithms", start, end, "Lecture room 101");
        var calendar = new Calendar("CS Schedule", List.of(event));

        var ics = serializer.serialize(calendar);

        assertThat(ics).isNotNull();
        assertThat(ics)
                .contains("BEGIN:VCALENDAR")
                .contains("END:VCALENDAR")
                .contains("PRODID:-//cist2ics//EN")
                .contains("X-WR-CALNAME:CS Schedule")
                .contains("BEGIN:VEVENT")
                .contains("SUMMARY:Algorithms")
                .contains("DESCRIPTION:Lecture room 101")
                .contains("DTSTART:20250102T093000")
                .contains("DTEND:20250102T110000")
                .contains("UID:")
                .contains("END:VEVENT");
    }

    @Test
    @DisplayName("serialize multiple events all of which are present")
    void multipleEvents() {
        var eventA = new Event("Event A",
                               LocalDateTime.of(2025, 5, 10, 8, 0),
                               LocalDateTime.of(2025, 5, 10, 9, 0),
                               "one foobar");
        var eventB = new Event("Event B",
                               LocalDateTime.of(2025, 5, 11, 10, 0),
                               LocalDateTime.of(2025, 5, 11, 11, 30),
                               "two foobar");
        var calendar = new Calendar("Many events", List.of(eventA, eventB));
        var ics = serializer.serialize(calendar);
        assertThat(ics)
                .contains("SUMMARY:Event A")
                .contains("SUMMARY:Event B")
                .contains("DTSTART:20250510T080000")
                .contains("DTSTART:20250511T100000");
    }
}
