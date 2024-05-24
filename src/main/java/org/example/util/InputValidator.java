package org.example.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InputValidator {
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("\\d+(\\.\\d+)?");
        private static final Pattern CURRENCY_PATTERN = Pattern.compile("\\w{3}");
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{2}\\.\\d{2}\\.\\d{4}");

    public static boolean isNumeric(String input) {
        Matcher matcher = NUMERIC_PATTERN.matcher(input);
        return matcher.matches();
    }

    public static boolean isDate(String input) {
        Matcher matcher = DATE_PATTERN.matcher(input);
        return matcher.matches();
    }

    public static boolean isCurrency(String input) {
        Matcher matcher = CURRENCY_PATTERN.matcher(input);
        return matcher.matches();
    }
}
