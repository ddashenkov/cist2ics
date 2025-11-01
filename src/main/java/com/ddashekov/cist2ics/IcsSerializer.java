package com.ddashekov.cist2ics;

import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.XProperty;
import net.fortuna.ical4j.validate.ValidationException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringWriter;

import static java.util.Objects.requireNonNull;

@Component
public final class IcsSerializer implements Serializer {

    @Override
    public String serialize(Calendar calendar) {
        requireNonNull(calendar);

        var ical = new net.fortuna.ical4j.model.Calendar();
        ical.add(new ProdId("-//cist2ics//UK"));
        var calendarTitle = calendar.title();
        if (calendarTitle == null || calendarTitle.isBlank()) {
            calendarTitle = "Пари ХНУРЕ";
        }
        ical.add(new XProperty("X-WR-CALNAME", calendarTitle));
        for (var e : calendar.events()) {
            var vevent = new VEvent();
            vevent.add(new Uid(e.id()));
            var title = e.title();
            if (title == null || e.title().isBlank()) {
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
        return result.toString();
    }
}
