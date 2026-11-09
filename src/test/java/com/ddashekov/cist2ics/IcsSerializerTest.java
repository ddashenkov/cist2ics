package com.ddashekov.cist2ics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("IcsSerializer")
class IcsSerializerTest {

    private final IcsSerializer serializer = new IcsSerializer();

    @Test
    @DisplayName("throw an exception on null calendar")
    void refuseNull() {
        assertThatThrownBy(() -> serializer.serialize(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("write the basic iCalendar fields of one event")
    void singleEvent() {
        var event = new Event("Algorithms", kyiv(2025, 1, 2, 9, 30), kyiv(2025, 1, 2, 11, 0),
                              "Lecture room 101");
        var calendar = new Calendar("CS Schedule", List.of(event));

        var ics = serializer.serialize(calendar);

        assertThat(ics)
                .contains("BEGIN:VCALENDAR")
                .contains("END:VCALENDAR")
                .contains("PRODID:-//cist2ics//UK")
                .contains("VERSION:2.0")
                .contains("X-WR-CALNAME:CS Schedule")
                .contains("BEGIN:VEVENT")
                .contains("SUMMARY:Algorithms")
                .contains("DESCRIPTION:Lecture room 101")
                .contains("DTSTART;TZID=Europe/Kyiv:20250102T093000")
                .contains("DTEND;TZID=Europe/Kyiv:20250102T110000")
                .contains("UID:" + event.id())
                .contains("END:VEVENT");
    }

    @Test
    @DisplayName("write the Kyiv time zone with summer and winter rules")
    void timeZone() {
        var summer = new Event("A", kyiv(2026, 9, 21, 11, 15), kyiv(2026, 9, 21, 12, 50), "A");
        var winter = new Event("B", kyiv(2026, 11, 2, 11, 15), kyiv(2026, 11, 2, 12, 50), "B");

        var ics = serializer.serialize(new Calendar("T", List.of(summer, winter)));

        assertThat(ics)
                .contains("BEGIN:VTIMEZONE")
                .contains("TZID:Europe/Kyiv")
                .contains("BEGIN:DAYLIGHT")
                .contains("BEGIN:STANDARD")
                .contains("DTSTART;TZID=Europe/Kyiv:20260921T111500")
                .contains("DTSTART;TZID=Europe/Kyiv:20261102T111500");
    }

    @Test
    @DisplayName("serialize multiple events all of which are present")
    void multipleEvents() {
        var eventA = new Event("Event A", kyiv(2025, 5, 10, 8, 0), kyiv(2025, 5, 10, 9, 0), "one foobar");
        var eventB = new Event("Event B", kyiv(2025, 5, 11, 10, 0), kyiv(2025, 5, 11, 11, 30), "two foobar");
        var calendar = new Calendar("Many events", List.of(eventA, eventB));

        var ics = serializer.serialize(calendar);

        assertThat(ics)
                .contains("SUMMARY:Event A")
                .contains("SUMMARY:Event B")
                .contains("DTSTART;TZID=Europe/Kyiv:20250510T080000")
                .contains("DTSTART;TZID=Europe/Kyiv:20250511T100000");
    }

    @Test
    @DisplayName("write a valid calendar without events")
    void emptyCalendar() {
        var ics = serializer.serialize(new Calendar("Empty", List.of()));

        assertThat(ics)
                .contains("BEGIN:VCALENDAR")
                .contains("X-WR-CALNAME:Empty")
                .doesNotContain("BEGIN:VEVENT");
    }

    @Test
    @DisplayName("write the Cyrillic text of a real lesson")
    void cyrillic() {
        var calendar = CalendarConverter.parse(Fixtures.text("teacher-393.csv"), "Дорошенко В. О.");

        var ics = serializer.serialize(calendar);

        assertThat(ics)
                .contains("X-WR-CALNAME:Дорошенко В. О.")
                .contains("SUMMARY:ВМ Лк")
                .contains("DESCRIPTION:ВМ Лк DL КІУКІу-26-1\\;КІУКІу-26-2");
    }

    @Test
    @DisplayName("fold lines to 75 octets without a split UTF-8 character")
    void foldByOctets() {
        // A real CIST description: ical4j folded it at 75 characters, which gave 102 octets.
        var details = "У/вВМ У.Лк DL ІКІМБз-26-1;ВСАз-26-1;АКТАКІТз-26-1;РКСз-26-1;БІБМІз-26-1;"
                + "БІБМІиз-26-1;ВПВПСз-26-1;ЕЕПСз-26-1;РІБз-26-1;ІОЛСз-26-1 📚📚";
        var event = new Event("У/вВМ У.Лк", kyiv(2026, 9, 21, 11, 15), kyiv(2026, 9, 21, 12, 50), details);

        var ics = serializer.serialize(new Calendar("Т", List.of(event)));

        assertThat(ics).endsWith("\r\n");
        var lines = ics.split("\r\n");
        assertThat(lines).allSatisfy(line -> {
            assertThat(line.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
            assertThat(Character.isHighSurrogate(line.charAt(line.length() - 1))).isFalse();
        });
        var unfolded = ics.replace("\r\n ", "");
        assertThat(unfolded).contains("DESCRIPTION:" + details.replace(";", "\\;"));
    }

    @Test
    @DisplayName("keep short lines unchanged")
    void noFoldForShortLines() {
        var ics = "BEGIN:VCALENDAR\r\nSUMMARY:ВМ Лк\r\nEND:VCALENDAR\r\n";

        assertThat(IcsSerializer.foldByOctets(ics)).isEqualTo(ics);
    }

    private static ZonedDateTime kyiv(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, CalendarConverter.KYIV);
    }
}
