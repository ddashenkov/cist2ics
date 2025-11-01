package com.ddashekov.cist2ics;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.List;

public record Calendar(String title, List<Event> events) {

    public String hash() {
        var buffer = ByteBuffer.allocate(events.size() * Integer.SIZE / Byte.SIZE);
        events.stream()
              .map(Event::hashCode)
              .forEach(buffer::putInt);
        return Base64.getEncoder()
                     .withoutPadding()
                     .encodeToString(buffer.array());
    }

    public boolean notEmpty() {
        return !events.isEmpty();
    }
}
