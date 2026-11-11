package com.ddashekov.cist2ics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reads raw data from the CIST timetable API.
 *
 * <p>CIST is often unavailable. The client sends each request to the primary host first and
 * to the fallback host on a network error, a 5xx status, or an Oracle backend error.
 * All CIST responses use the {@code windows-1251} encoding.
 */
@Component
public class CistClient {

    static final Charset CIST_CHARSET = Charset.forName("windows-1251");

    private static final Logger log = LoggerFactory.getLogger(CistClient.class);

    private static final DateTimeFormatter CIST_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Pattern ORACLE_ERROR = Pattern.compile("ORA-\\d{5}");

    private static final String FACULTIES = "/P_API_FACULTIES_JSON";
    private static final String STRUCTURE = "/P_API_PODR_JSON";
    /**
     * The CSV export of a schedule.
     *
     * <p>With a department ID in {@code Aid_kaf}, CIST ignores {@code Aid_sotr} and returns the
     * lessons of all teachers in the department. With {@code Aid_kaf=0}, it returns only the
     * lessons of the teacher in {@code Aid_sotr}. This was checked live on 2026-11-07.
     */
    private static final String TEACHER_CSV =
            "/WEB_IAS_TT_GNR_RASP.GEN_TEACHER_KAF_RASP?ATypeDoc=3&Aid_sotr={teacher}"
            + "&Aid_kaf=0&ADateStart={from}&ADateEnd={to}&AMultiWorkSheet=0";

    private final List<RestClient> hosts;

    public CistClient(RestClient.Builder builder,
                      @Value("${cist.base-url}") String baseUrl,
                      @Value("${cist.fallback-base-url:}") String fallbackBaseUrl) {
        var hosts = new ArrayList<RestClient>();
        hosts.add(builder.clone().baseUrl(baseUrl).build());
        if (!fallbackBaseUrl.isBlank()) {
            hosts.add(builder.clone().baseUrl(fallbackBaseUrl).build());
        }
        this.hosts = List.copyOf(hosts);
    }

    /**
     * Returns the {@code P_API_FACULTIES_JSON} body: a valid JSON list of faculties.
     */
    public String fetchFaculties() {
        return get(FACULTIES, Map.of());
    }

    /**
     * Returns the {@code P_API_PODR_JSON} body: faculties, departments, and teachers.
     *
     * <p>The body is not valid JSON. {@link TeacherDirectory} repairs it.
     */
    public String fetchStructure() {
        return get(STRUCTURE, Map.of());
    }

    /**
     * Returns the CSV export of one teacher schedule for the given dates, inclusive.
     */
    public String fetchTeacherCsv(long teacherId, LocalDate from, LocalDate to) {
        return get(TEACHER_CSV, Map.of("teacher", teacherId,
                                       "from", CIST_DATE.format(from),
                                       "to", CIST_DATE.format(to)));
    }

    private String get(String uriTemplate, Map<String, ?> variables) {
        CistUnavailableException failure = null;
        for (var host : hosts) {
            try {
                return getFrom(host, uriTemplate, variables);
            } catch (CistUnavailableException e) {
                log.warn("CIST request failed, {}", e.getMessage());
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
                if (e.getCause() instanceof HttpClientErrorException) {
                    // A 4xx status means a wrong request. The mirror gives the same answer.
                    break;
                }
            }
        }
        throw failure;
    }

    private static String getFrom(RestClient host, String uriTemplate, Map<String, ?> variables) {
        byte[] bytes;
        try {
            bytes = host.get()
                        .uri(uriTemplate, variables)
                        .retrieve()
                        .body(byte[].class);
        } catch (RestClientException e) {
            throw new CistUnavailableException(
                    "%s: %s".formatted(uriTemplate, e.getMessage()), e);
        }
        var body = bytes == null ? "" : new String(bytes, CIST_CHARSET);
        var oracleError = ORACLE_ERROR.matcher(body);
        if (oracleError.find()) {
            throw new CistUnavailableException(
                    "%s: CIST backend error %s".formatted(uriTemplate, oracleError.group()));
        }
        return body;
    }
}
