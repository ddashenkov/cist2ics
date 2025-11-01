package com.ddashekov.cist2ics;

import java.time.LocalDateTime;
import java.util.Base64;

public record Event(String title, LocalDateTime start, LocalDateTime end, String details) {}
