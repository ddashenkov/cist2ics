package com.ddashekov.cist2ics;

/**
 * The CIST IDs that select one teacher schedule.
 *
 * @param teacherId    the CIST teacher ID ({@code Aid_sotr})
 * @param departmentId the CIST department ID of the match. The schedule request does not use it,
 *                     because a department ID makes CIST return the whole department.
 * @param shortName    the teacher short name, for example {@code Дорошенко В. О.}
 */
public record TeacherRef(long teacherId, long departmentId, String shortName) {
}
