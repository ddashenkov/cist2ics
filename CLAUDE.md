# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project purpose

`cist2ics` is a Spring Boot web service. It fetches a teacher schedule from CIST, the timetable system of NURE (ХНУРЕ) in Kharkiv. It converts the schedule to an iCalendar (`.ics`) feed. `README.md` describes the endpoint, the responses, and the configuration.

## Commands

The project uses the Gradle wrapper, Java 21 (toolchain and `.java-version`), and Spring Boot 3.5.

- Build: `./gradlew build`
- Run all tests: `./gradlew test`
- Run one test class: `./gradlew test --tests 'com.ddashekov.cist2ics.TeacherDirectoryTest'`
- Run one test method: `./gradlew test --tests 'com.ddashekov.cist2ics.TeacherDirectoryTest.nestedFaculty'`
- Run the service: `./gradlew bootRun`
- Build the container image: `./gradlew bootBuildImage` (needs Docker)

The project has no linter or formatter configuration.

## Architecture

All code is in one package, `com.ddashekov.cist2ics`. The package name has no "n" after "ddashe". Keep this spelling.

The request flow:

1. `ConverterController` serves `GET /teacher/calendar.ics?faculty=…&department=…&teacher=…`. `ErrorHandler` maps the exceptions to 404, 502, and 500.
2. `CalendarService` keeps a fresh cache and a stale cache (Caffeine). It serves the stale copy when CIST fails.
3. `CalendarConverter` asks `TeacherDirectory` for the teacher ID, then fetches and parses the CSV export.
4. `TeacherDirectory` builds the faculty, department, and teacher list from two CIST JSON endpoints. `Names.normalize` defines the name match.
5. `CistClient` does all HTTP calls. It tries `cist.base-url` first and `cist.fallback-base-url` second.
6. `IcsSerializer`, the only `Serializer` implementation, writes the iCalendar text with ical4j 4.x.

## CIST behavior that is not obvious

These facts come from live calls. The raw responses are in `src/test/resources/cist/`. Do not convert their encoding, because the tests read the `windows-1251` bytes.

- All responses use `windows-1251`.
- `P_API_PODR_JSON` is invalid JSON. The end of the body is broken, and most faculties have no closing brackets, so each faculty is nested inside the previous one. `TeacherDirectory` repairs both defects. It finds the faculty nodes by the IDs from `P_API_FACULTIES_JSON`.
- The schedule CSV comes from `WEB_IAS_TT_GNR_RASP.GEN_TEACHER_KAF_RASP`. The request must send `Aid_kaf=0`. With a real department ID, CIST ignores `Aid_sotr` and returns the lessons of the whole department.
- The CSV has a header row with Russian column names and `\r` line breaks. Dates are `dd.MM.yyyy`, and times are Kyiv local time. The event summary is the first two words of `Описание` (subject and lesson type).
- `P_API_EVEN_JSON` (events as JSON) needs a registered client key. The project does not use it.
- CIST is often down, or one host refuses connections while the other works. Body text with `ORA-<digits>` is an Oracle backend error.

## Conventions

- `Event.id()` is the iCalendar `UID`. Calendar clients use it to match events between refreshes, so keep it stable.
- The `ETag` is weak, because ical4j writes a new `DTSTAMP` on each response.
