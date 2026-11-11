package com.ddashekov.cist2ics;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Normalizes Ukrainian names, so that user input matches the CIST directory.
 *
 * <p>CIST data is inconsistent: it uses several apostrophe characters, Latin letters that look
 * like Cyrillic ones (for example {@code ШI} with a Latin {@code I}), and irregular spaces.
 */
final class Names {

    private static final Pattern APOSTROPHES = Pattern.compile("[’ʼ`‘]");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern SPACE_AFTER_DOT = Pattern.compile("\\.\\s+");
    private static final Pattern SPACES_AROUND_DASH = Pattern.compile("\\s*-\\s*");

    /**
     * Latin letters that look like Cyrillic letters, in lower case.
     */
    private static final Map<Character, Character> LATIN_TO_CYRILLIC = Map.ofEntries(
            Map.entry('a', 'а'), Map.entry('b', 'в'), Map.entry('c', 'с'), Map.entry('e', 'е'),
            Map.entry('h', 'н'), Map.entry('i', 'і'), Map.entry('k', 'к'), Map.entry('m', 'м'),
            Map.entry('o', 'о'), Map.entry('p', 'р'), Map.entry('t', 'т'), Map.entry('x', 'х'),
            Map.entry('y', 'у')
    );

    private Names() {
    }

    static String normalize(String name) {
        var text = name.strip().toLowerCase(Locale.ROOT);
        text = APOSTROPHES.matcher(text).replaceAll("'");
        text = SPACES.matcher(text).replaceAll(" ");
        text = SPACE_AFTER_DOT.matcher(text).replaceAll(".");
        text = SPACES_AROUND_DASH.matcher(text).replaceAll("-");
        var result = new StringBuilder(text.length());
        for (var c : text.toCharArray()) {
            result.append(LATIN_TO_CYRILLIC.getOrDefault(c, c));
        }
        return result.toString();
    }

    static boolean matches(String input, String shortName, String fullName) {
        var normalized = normalize(input);
        return normalized.equals(normalize(shortName)) || normalized.equals(normalize(fullName));
    }
}
