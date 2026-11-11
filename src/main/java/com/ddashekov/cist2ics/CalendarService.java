package com.ddashekov.cist2ics;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Caches calendars, so that frequent polls from calendar clients do not reach CIST.
 *
 * <p>A calendar is fresh for a short time. After that, the service fetches it again.
 * If CIST fails, the service returns the last good calendar while it is in the stale cache.
 */
@Service
public class CalendarService {

    private static final Logger log = LoggerFactory.getLogger(CalendarService.class);

    private final CalendarConverter converter;
    private final Cache<Key, Calendar> fresh;
    private final Cache<Key, Calendar> stale;

    public CalendarService(CalendarConverter converter,
                           @Value("${cist.calendar-fresh-ttl}") Duration freshTtl,
                           @Value("${cist.calendar-stale-ttl}") Duration staleTtl) {
        this.converter = converter;
        this.fresh = Caffeine.newBuilder()
                             .expireAfterWrite(freshTtl)
                             .maximumSize(10_000)
                             .build();
        this.stale = Caffeine.newBuilder()
                             .expireAfterWrite(staleTtl)
                             .maximumSize(10_000)
                             .build();
    }

    /**
     * Returns the calendar of a teacher.
     *
     * @throws TeacherNotFoundException if a name does not match the CIST directory
     * @throws CistUnavailableException if CIST fails and no stale calendar exists
     */
    public Calendar calendar(String faculty, String department, String teacher) {
        var key = new Key(Names.normalize(faculty),
                          Names.normalize(department),
                          Names.normalize(teacher));
        var cached = fresh.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        try {
            var calendar = converter.fetchCalendar(faculty, department, teacher);
            fresh.put(key, calendar);
            stale.put(key, calendar);
            return calendar;
        } catch (CistUnavailableException e) {
            var fallback = stale.getIfPresent(key);
            if (fallback == null) {
                throw e;
            }
            log.warn("CIST failed, serving a stale calendar for {}: {}", teacher, e.getMessage());
            return fallback;
        }
    }

    private record Key(String faculty, String department, String teacher) {
    }
}
