z# cist2ics

`cist2ics` is a web service that converts a teacher schedule from [CIST](https://cist.nure.ua), the timetable system of NURE (ХНУРЕ), into an iCalendar feed. You can subscribe to the feed in Google Calendar, Apple Calendar, Outlook, or another calendar client.

## Endpoint

```
GET /teacher/calendar.ics?faculty=<faculty>&department=<department>&teacher=<teacher>
```

All three parameters are required. Each value can be the short name or the full name from CIST, in Ukrainian. The match ignores case, extra spaces, the apostrophe variant, and Latin letters that look like Cyrillic letters.

| Parameter | Short name example | Full name example |
|---|---|---|
| `faculty` | `ITM` | `Факультет інформаційно - аналітичних технологій та менеджменту` |
| `department` | `ВМ` | `Кафедра вищої математики` |
| `teacher` | `Дорошенко В. О.` | `Дорошенко Володимир Олексійович` |

The feed contains the lessons of the current academic year, from 1 September to 31 August. The times use the `Europe/Kyiv` time zone.

Example with `curl`:

```sh
curl -G http://localhost:8080/teacher/calendar.ics \
  --data-urlencode 'faculty=ITM' \
  --data-urlencode 'department=ВМ' \
  --data-urlencode 'teacher=Дорошенко В. О.'
```

A calendar client needs the URL-encoded form:

```
http://localhost:8080/teacher/calendar.ics?faculty=ITM&department=%D0%92%D0%9C&teacher=%D0%94%D0%BE%D1%80%D0%BE%D1%88%D0%B5%D0%BD%D0%BA%D0%BE%20%D0%92.%20%D0%9E.
```

### Responses

| Status | Cause |
|---|---|
| `200` | The feed, as `text/calendar; charset=UTF-8`. |
| `304` | The `If-None-Match` header matches the current `ETag`. |
| `400` | A parameter is missing or blank. |
| `404` | A name does not match CIST. The message names the faculty, the department, or the teacher. |
| `502` | CIST is unavailable, and the service has no stored copy of this calendar. |

### Cache

CIST is often unavailable. The service keeps each calendar for 1 hour before it asks CIST again. If CIST fails, the service returns the last good calendar for up to 7 days. The service keeps the list of faculties, departments, and teachers for 24 hours.

## Configuration

Set the properties in `src/main/resources/application.properties`, as command-line arguments, or as environment variables.

| Property | Environment variable | Default |
|---|---|---|
| `cist.base-url` | `CIST_BASEURL` | `https://cist.nure.ua/ias/app/tt` |
| `cist.fallback-base-url` | `CIST_FALLBACKBASEURL` | `https://cist2.nure.ua/ias/app/tt` |
| `cist.connect-timeout` | `CIST_CONNECTTIMEOUT` | `5s` |
| `cist.read-timeout` | `CIST_READTIMEOUT` | `15s` |
| `cist.directory-ttl` | `CIST_DIRECTORYTTL` | `24h` |
| `cist.calendar-fresh-ttl` | `CIST_CALENDARFRESHTTL` | `1h` |
| `cist.calendar-stale-ttl` | `CIST_CALENDARSTALETTL` | `7d` |

## Build and run

The project needs Java 21. The Gradle wrapper downloads Gradle.

```sh
./gradlew test      # run the tests
./gradlew bootRun   # start the service on port 8080
```

## Docker

Build the image with Cloud Native Buildpacks. You do not need a `Dockerfile`.

```sh
./gradlew bootBuildImage
docker run -p 8080:8080 cist2ics:1.0
```

The health check is at `http://localhost:8080/actuator/health`.

## Network requirement

The host must reach `cist.nure.ua` or `cist2.nure.ua`. CIST sometimes refuses connections from some networks or is down for hours.
