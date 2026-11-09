package com.ddashekov.cist2ics;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

public record Calendar(String title, List<Event> events) {

    public Calendar {
        events = List.copyOf(events);
    }

    /**
     * Returns a short hash of the title and the events, for use as an HTTP entity tag.
     */
    public String hash() {
        var buffer = ByteBuffer.allocate((events.size() + 1) * Integer.BYTES);
        buffer.putInt(title.hashCode());
        events.forEach(e -> buffer.putInt(e.hashCode()));
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(buffer.array());
            return Base64.getUrlEncoder()
                         .withoutPadding()
                         .encodeToString(Arrays.copyOf(digest, 16));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every JVM must support SHA-256.", e);
        }
    }
}
