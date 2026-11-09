package com.ddashekov.cist2ics;

import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.XProperty;
import net.fortuna.ical4j.model.property.immutable.ImmutableCalScale;
import net.fortuna.ical4j.model.property.immutable.ImmutableVersion;
import net.fortuna.ical4j.validate.ValidationException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringWriter;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

@Component
public final class IcsSerializer implements Serializer {

    private static final String CRLF = "\r\n";
    private static final int MAX_LINE_OCTETS = 75;
    private static final Pattern FOLD = Pattern.compile("\r\n[ \t]");

    private final VTimeZone kyiv;

    public IcsSerializer() {
        var registry = TimeZoneRegistryFactory.getInstance().createRegistry();
        var timeZone = requireNonNull(registry.getTimeZone(CalendarConverter.KYIV.getId()),
                                      "ical4j has no time zone " + CalendarConverter.KYIV);
        this.kyiv = timeZone.getVTimeZone();
    }

    @Override
    public String serialize(Calendar calendar) {
        requireNonNull(calendar);

        var ical = new net.fortuna.ical4j.model.Calendar();
        ical.add(new ProdId("-//cist2ics//UK"));
        ical.add(ImmutableVersion.VERSION_2_0);
        ical.add(ImmutableCalScale.GREGORIAN);
        var calendarTitle = calendar.title();
        if (calendarTitle == null || calendarTitle.isBlank()) {
            calendarTitle = "Пари ХНУРЕ";
        }
        ical.add(new XProperty("X-WR-CALNAME", calendarTitle));
        ical.add(new XProperty("X-WR-TIMEZONE", CalendarConverter.KYIV.getId()));
        // The time zone also makes a calendar without events valid.
        ical.add(kyiv);
        for (var e : calendar.events()) {
            var vevent = new VEvent();
            vevent.add(new Uid(e.id()));
            var title = e.title();
            if (title == null || title.isBlank()) {
                title = "Заняття";
            }
            vevent.add(new Summary(title));
            if (e.details() != null && !e.details().isBlank()) {
                vevent.add(new Description(e.details()));
            }
            vevent.add(new DtStart<>(e.start()));
            vevent.add(new DtEnd<>(e.end()));
            ical.add(vevent);
        }

        var outputter = new CalendarOutputter();
        var result = new StringWriter();
        try {
            outputter.output(ical, result);
        } catch (IOException | ValidationException e) {
            throw new SerializationException("Failed to serialize a calendar.", e);
        }
        return foldByOctets(result.toString());
    }

    /**
     * Folds content lines to 75 octets, as RFC 5545 section 3.1 requires.
     *
     * <p>ical4j folds at 75 characters. A Cyrillic letter takes two octets in UTF-8, so ical4j
     * lines can have up to 150 octets. This method unfolds the ical4j output and folds it again
     * by octets. It never splits a UTF-8 character. ical4j escapes line breaks in values,
     * so a CRLF followed by a space or a tab is always a fold.
     */
    static String foldByOctets(String ics) {
        var unfolded = FOLD.matcher(ics).replaceAll("");
        var result = new StringBuilder(unfolded.length() + unfolded.length() / 30);
        for (var line : unfolded.split(CRLF, -1)) {
            if (!result.isEmpty()) {
                result.append(CRLF);
            }
            var octets = 0;
            var limit = MAX_LINE_OCTETS;
            for (var i = 0; i < line.length(); ) {
                var codePoint = line.codePointAt(i);
                var size = utf8Length(codePoint);
                if (octets + size > limit) {
                    result.append(CRLF).append(' ');
                    // The leading space of a continuation line counts as one octet.
                    octets = 1;
                }
                result.appendCodePoint(codePoint);
                octets += size;
                i += Character.charCount(codePoint);
            }
        }
        return result.toString();
    }

    private static int utf8Length(int codePoint) {
        if (codePoint < 0x80) {
            return 1;
        }
        if (codePoint < 0x800) {
            return 2;
        }
        return codePoint < 0x10000 ? 3 : 4;
    }
}
