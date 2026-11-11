package com.ddashekov.cist2ics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Finds the CIST IDs of a teacher from the faculty, department, and teacher names.
 *
 * <p>The directory comes from {@code P_API_PODR_JSON}. That body has two known defects:
 * <ol>
 *     <li>The end of the body is broken. The repair replaces the last two characters with
 *         {@code ]}}}.
 *     <li>Most faculties have no closing brackets, so each faculty is nested inside the
 *         {@code departments} of the previous faculty. The repair treats a node as a faculty
 *         when its ID is in {@code P_API_FACULTIES_JSON}, at any depth.
 * </ol>
 *
 * <p>The directory is kept for a configured time. If CIST fails on a refresh, the directory
 * keeps the last good copy.
 */
@Component
public class TeacherDirectory {

    private static final Logger log = LoggerFactory.getLogger(TeacherDirectory.class);

    private final CistClient cist;
    private final ObjectMapper json;
    private final Clock clock;
    private final Duration ttl;

    private volatile Snapshot snapshot;

    public TeacherDirectory(CistClient cist,
                            ObjectMapper json,
                            Clock clock,
                            @Value("${cist.directory-ttl}") Duration ttl) {
        this.cist = cist;
        this.json = json;
        this.clock = clock;
        this.ttl = ttl;
    }

    public TeacherRef find(String faculty, String department, String teacher) {
        var entries = entries();
        var inFaculty = entries.stream()
                               .filter(e -> Names.matches(faculty, e.facultyShort(), e.facultyFull()))
                               .toList();
        if (inFaculty.isEmpty()) {
            throw new TeacherNotFoundException("faculty", faculty);
        }
        var inDepartment = inFaculty.stream()
                                    .filter(e -> Names.matches(department,
                                                               e.departmentShort(),
                                                               e.departmentFull()))
                                    .toList();
        if (inDepartment.isEmpty()) {
            throw new TeacherNotFoundException("department", department);
        }
        var matches = inDepartment.stream()
                                  .filter(e -> Names.matches(teacher,
                                                             e.teacherShort(),
                                                             e.teacherFull()))
                                  .map(e -> new TeacherRef(e.teacherId(),
                                                           e.departmentId(),
                                                           e.teacherShort()))
                                  .distinct()
                                  .toList();
        if (matches.isEmpty()) {
            throw new TeacherNotFoundException("teacher", teacher);
        }
        if (matches.size() > 1) {
            log.warn("{} teachers match \"{}\" in \"{}\", \"{}\". Using {}.",
                     matches.size(), teacher, faculty, department, matches.getFirst());
        }
        return matches.getFirst();
    }

    private List<Entry> entries() {
        var current = snapshot;
        var now = clock.instant();
        if (current != null && current.loadedAt().plus(ttl).isAfter(now)) {
            return current.entries();
        }
        synchronized (this) {
            current = snapshot;
            if (current != null && current.loadedAt().plus(ttl).isAfter(now)) {
                return current.entries();
            }
            try {
                var loaded = new Snapshot(load(), now);
                snapshot = loaded;
                return loaded.entries();
            } catch (CistUnavailableException e) {
                if (current == null) {
                    throw e;
                }
                log.warn("Using the teacher directory from {}: {}", current.loadedAt(), e.getMessage());
                return current.entries();
            }
        }
    }

    private List<Entry> load() {
        var facultyIds = parseFacultyIds(cist.fetchFaculties());
        var structure = parseStructure(cist.fetchStructure());
        var entries = new ArrayList<Entry>();
        for (var node : structure.path("university").path("faculties")) {
            walk(node, null, facultyIds, entries);
        }
        if (entries.isEmpty()) {
            throw new CistUnavailableException("The CIST teacher directory is empty.");
        }
        log.info("Loaded {} teacher entries from CIST.", entries.size());
        return List.copyOf(entries);
    }

    private Set<Long> parseFacultyIds(String body) {
        var root = readJson(body, "P_API_FACULTIES_JSON");
        var faculties = root.path("university").path("faculties");
        var ids = StreamSupport.stream(faculties.spliterator(), false)
                               .map(f -> f.path("id").asLong())
                               .collect(Collectors.toUnmodifiableSet());
        if (ids.isEmpty()) {
            throw new CistUnavailableException("The CIST faculty list is empty.");
        }
        return ids;
    }

    private JsonNode parseStructure(String body) {
        var text = body.strip();
        try {
            return json.readTree(text);
        } catch (JsonProcessingException e) {
            if (text.length() < 2) {
                throw new CistUnavailableException("P_API_PODR_JSON is empty.", e);
            }
            var repaired = text.substring(0, text.length() - 2) + "]}}";
            return readJson(repaired, "P_API_PODR_JSON");
        }
    }

    private JsonNode readJson(String body, String endpoint) {
        try {
            return json.readTree(body.strip());
        } catch (JsonProcessingException e) {
            throw new CistUnavailableException(endpoint + " is not valid JSON.", e);
        }
    }

    /**
     * Walks one node of the structure tree.
     *
     * @param faculty the closest faculty node above this node, or {@code null} at the top
     */
    private static void walk(JsonNode node, JsonNode faculty, Set<Long> facultyIds,
                             List<Entry> entries) {
        if (facultyIds.contains(node.path("id").asLong())) {
            faculty = node;
        } else if (faculty != null && (node.has("teachers") || node.has("departments"))) {
            for (var teacher : node.path("teachers")) {
                entries.add(new Entry(text(faculty, "short_name"), text(faculty, "full_name"),
                                      node.path("id").asLong(),
                                      text(node, "short_name"), text(node, "full_name"),
                                      teacher.path("id").asLong(),
                                      text(teacher, "short_name"), text(teacher, "full_name")));
            }
        }
        // Other nodes (teacher-like objects in a departments list) have no children to walk.
        for (var child : node.path("departments")) {
            walk(child, faculty, facultyIds, entries);
        }
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asText("");
    }

    private record Entry(String facultyShort, String facultyFull,
                         long departmentId, String departmentShort, String departmentFull,
                         long teacherId, String teacherShort, String teacherFull) {
    }

    private record Snapshot(List<Entry> entries, Instant loadedAt) {
    }
}
